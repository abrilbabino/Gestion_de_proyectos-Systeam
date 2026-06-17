# HU-33 — Carga de saldo / Adquisición de $IDEA

## Contexto y decisiones de diseño

- **Precio de $IDEA**: dinámico, determinado por las reservas del pool en `IdeaSwap.sol` (AMM). No es un precio fijo.
- **Supply**: 100M IDEA pre-minados al deployer en el constructor. No existe función `mint()`.
- **Pasarela**: MercadoPago (sandbox para desarrollo)
- **Tipo de cambio ARS/USD**: consultado en tiempo real desde una API externa
- **Precio IDEA/USDC**: consultado en tiempo real desde el contrato `IdeaSwap` via Web3j
- **Acreditación**: la treasury wallet del backend ejecuta el swap en IdeaSwap y transfiere IDEA al usuario
- **Slippage**: el precio se fija al momento de crear la preferencia. Si el pool se mueve antes de que MP confirme, se usa el precio bloqueado (el riesgo lo asume la plataforma, no el usuario)
- **Red**: Base Sepolia (ChainID 84532)

---

## Flujo completo

```
Usuario ingresa cantidad de $IDEA a comprar
        │
        ▼
Frontend llama POST /api/pagos/preferencia
        │  backend consulta tipo de cambio ARS/USD (dolarapi.com)
        │  backend consulta precio IDEA/USDC (IdeaSwap.getAmountsOut)
        │  calcula total en pesos y bloquea precio en DB
        │  crea preferencia en MP
        │  retorna checkoutUrl + cotización bloqueada
        ▼
Frontend muestra resumen (X IDEA = Y ARS al precio actual del pool)
Frontend redirige a MercadoPago checkout
        │
        ▼  (usuario paga en MP)
        │
MP llama POST /api/pagos/webhook  ← endpoint público
        │  backend valida el pago con MP
        │  si aprobado:
        │    treasury aprueba IdeaSwap para gastar USDC
        │    treasury llama swapUsdcForExactIdea(cantidadIdea)
        │      → treasury recibe IDEA del pool
        │    treasury transfiere IDEA al wallet del usuario
        │    registra tx_hash en DB → estado APROBADO
        ▼
Frontend muestra pantalla de éxito / actualiza billetera
```

---

## Backend

### Paso 1 — Dependencia MercadoPago

En `pom.xml`:

```xml
<dependency>
    <groupId>com.mercadopago</groupId>
    <artifactId>sdk-java</artifactId>
    <version>2.1.24</version>
</dependency>
```

### Paso 2 — Variables de entorno

Agregar al `application.yml`:

```yaml
mercadopago:
  access-token: ${MP_ACCESS_TOKEN}
  webhook-secret: ${MP_WEBHOOK_SECRET}

blockchain:
  idea-token-address: ${IDEA_TOKEN_CONTRACT_ADDRESS}
  idea-swap-address: ${IDEA_SWAP_CONTRACT_ADDRESS}
  usdc-address: ${USDC_CONTRACT_ADDRESS}
  treasury-private-key: ${TREASURY_PRIVATE_KEY}  # wallet que tiene IDEA y USDC

tipo-cambio:
  api-url: https://dolarapi.com/v1/dolares/bolsa
```

> La treasury wallet es el deployer del contrato, que recibió los 100M IDEA en el constructor. También necesita tener USDC para ejecutar el swap.

### Paso 3 — Migración Flyway

Crear `V23__add_pagos.sql`:

```sql
CREATE TABLE pagos (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL,
    mp_preference_id    VARCHAR(255),
    mp_payment_id       VARCHAR(255),
    monto_ars           NUMERIC(18,2) NOT NULL,
    tipo_cambio_ars_usd NUMERIC(18,6) NOT NULL,
    precio_idea_usdc    NUMERIC(18,6) NOT NULL,
    cantidad_idea       NUMERIC(18,6) NOT NULL,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    wallet_address      VARCHAR(255),
    tx_hash_swap        VARCHAR(255),
    tx_hash_transfer    VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pagos_usuario FOREIGN KEY (usuario_id) REFERENCES users(id),
    CONSTRAINT chk_pagos_estado CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO','CANCELADO'))
);

CREATE INDEX idx_pagos_usuario ON pagos(usuario_id);
CREATE INDEX idx_pagos_mp_payment ON pagos(mp_payment_id);
```

> `precio_idea_usdc` registra el precio del pool al momento de crear la preferencia. `tx_hash_swap` y `tx_hash_transfer` registran las dos transacciones on-chain.

### Paso 4 — Entidad y repositorio

```java
// Pago.java
@Entity
@Table(name = "pagos")
public class Pago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String mpPreferenceId;
    private String mpPaymentId;
    private BigDecimal montoArs;
    private BigDecimal tipoCambioArsUsd;
    private BigDecimal precioIdeaUsdc;   // precio del pool al momento de cotizar
    private BigDecimal cantidadIdea;
    private String estado;
    private String walletAddress;
    private String txHashSwap;
    private String txHashTransfer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
// PagoRepository.java
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByMpPaymentId(String mpPaymentId);
    List<Pago> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);
}
```

### Paso 5 — Servicio de tipo de cambio

```java
@Service
public class TipoCambioService {

    private final RestTemplate restTemplate;

    @Value("${tipo-cambio.api-url}")
    private String apiUrl;

    public BigDecimal obtenerArsUsd() {
        try {
            Map response = restTemplate.getForObject(apiUrl, Map.class);
            // dolarapi.com retorna { "compra": 1200.5, "venta": 1210.0, ... }
            // Usamos "venta" porque el usuario compra dólares
            return new BigDecimal(response.get("venta").toString());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo obtener el tipo de cambio ARS/USD");
        }
    }
}
```

### Paso 6 — Servicio de IdeaSwap (precio y ejecución del swap)

```java
@Service
public class IdeaSwapService {

    @Value("${blockchain.idea-swap-address}")
    private String swapAddress;

    @Value("${blockchain.idea-token-address}")
    private String ideaTokenAddress;

    @Value("${blockchain.usdc-address}")
    private String usdcAddress;

    @Value("${blockchain.treasury-private-key}")
    private String treasuryPrivateKey;

    private final Web3j web3j;

    // Consulta el precio actual: cuántos USDC cuesta 1 IDEA en el pool
    public BigDecimal obtenerPrecioIdeaEnUsdc() {
        IdeaSwap swapContrato = IdeaSwap.load(swapAddress, web3j,
            Credentials.create(treasuryPrivateKey), new DefaultGasProvider());

        // getAmountsOut(1e18, false) → cuánto USDC sale por 1 IDEA
        Tuple2<BigInteger, BigInteger> result = swapContrato
            .getAmountsOut(BigInteger.TEN.pow(18), false).send();

        // result.component1() = amountOut en USDC (6 decimales si es USDC estándar)
        return new BigDecimal(result.component1())
            .divide(BigDecimal.TEN.pow(6), 6, RoundingMode.HALF_UP);
    }

    // Ejecuta el swap: treasury paga USDC, recibe IDEA del pool
    public String ejecutarSwap(BigInteger cantidadIdeaWei) throws Exception {
        Credentials treasury = Credentials.create(treasuryPrivateKey);

        // 1. Calcular cuánto USDC necesita el swap (+ 1% fee del pool)
        IdeaSwap swapContrato = IdeaSwap.load(swapAddress, web3j, treasury,
            new DefaultGasProvider());
        Tuple2<BigInteger, BigInteger> amounts = swapContrato
            .getAmountsOut(cantidadIdeaWei, false).send();
        BigInteger usdcNecesario = amounts.component1()
            .multiply(BigInteger.valueOf(101))
            .divide(BigInteger.valueOf(100)); // +1% de margen para el fee

        // 2. Aprobar al swap para gastar USDC de la treasury
        ERC20 usdc = ERC20.load(usdcAddress, web3j, treasury, new DefaultGasProvider());
        usdc.approve(swapAddress, usdcNecesario).send();

        // 3. Ejecutar el swap: treasury paga USDC, recibe IDEA
        TransactionReceipt receipt = swapContrato
            .swapUsdcForExactIdea(cantidadIdeaWei).send();

        return receipt.getTransactionHash();
    }

    // Transfiere IDEA de la treasury al usuario
    public String transferirIdea(String destinatario, BigInteger cantidadIdeaWei) throws Exception {
        Credentials treasury = Credentials.create(treasuryPrivateKey);
        ERC20 idea = ERC20.load(ideaTokenAddress, web3j, treasury, new DefaultGasProvider());
        TransactionReceipt receipt = idea.transfer(destinatario, cantidadIdeaWei).send();
        return receipt.getTransactionHash();
    }
}
```

> **Nota sobre el burn**: `IdeaToken` aplica un burn del 1% en cada transfer. Al transferir IDEA al usuario, el usuario recibe el 99% de la cantidad enviada. Si querés que el usuario reciba exactamente `cantidadIdea`, enviá `cantidadIdea / 0.99` en el transfer.

### Paso 7 — Servicio de MercadoPago

```java
@Service
public class PagoService {

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    private final TipoCambioService tipoCambioService;
    private final IdeaSwapService ideaSwapService;
    private final PagoRepository pagoRepository;

    public CrearPreferenciaResponse crearPreferencia(Long usuarioId, String walletAddress,
                                                      BigDecimal cantidadIdea) throws Exception {
        MercadoPagoConfig.setAccessToken(mpAccessToken);

        // 1. Cotizar precio actual
        BigDecimal tipoCambio = tipoCambioService.obtenerArsUsd();         // ARS por 1 USD
        BigDecimal precioIdeaUsdc = ideaSwapService.obtenerPrecioIdeaEnUsdc(); // USDC por 1 IDEA

        // 2. Calcular monto en ARS: cantidadIdea * precioIdeaUsdc * tipoCambio
        //    (asumiendo 1 USDC ≈ 1 USD)
        BigDecimal montoArs = cantidadIdea
            .multiply(precioIdeaUsdc)
            .multiply(tipoCambio)
            .setScale(2, RoundingMode.HALF_UP);

        // 3. Guardar pago PENDIENTE con precio bloqueado
        Pago pago = new Pago();
        pago.setUsuarioId(usuarioId);
        pago.setMontoArs(montoArs);
        pago.setTipoCambioArsUsd(tipoCambio);
        pago.setPrecioIdeaUsdc(precioIdeaUsdc);
        pago.setCantidadIdea(cantidadIdea);
        pago.setWalletAddress(walletAddress);
        pago.setEstado("PENDIENTE");
        pago.setCreatedAt(LocalDateTime.now());
        pago.setUpdatedAt(LocalDateTime.now());
        pago = pagoRepository.save(pago);

        // 4. Crear preferencia en MP
        PreferenceClient client = new PreferenceClient();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
            .title(cantidadIdea + " tokens $IDEA")
            .quantity(1)
            .unitPrice(montoArs)
            .currencyId("ARS")
            .build();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
            .success("https://tuapp.com/comprar-idea/exito")
            .failure("https://tuapp.com/comprar-idea/fallo")
            .pending("https://tuapp.com/comprar-idea/pendiente")
            .build();

        PreferenceRequest request = PreferenceRequest.builder()
            .items(List.of(item))
            .backUrls(backUrls)
            .notificationUrl("https://tubackend.com/api/pagos/webhook")
            .externalReference(pago.getId().toString())
            .build();

        Preference preference = client.create(request);

        pago.setMpPreferenceId(preference.getId());
        pago.setUpdatedAt(LocalDateTime.now());
        pagoRepository.save(pago);

        return new CrearPreferenciaResponse(
            preference.getSandboxInitPoint(),
            preference.getId(),
            tipoCambio,
            precioIdeaUsdc,
            montoArs,
            cantidadIdea
        );
    }

    @Transactional
    public void procesarWebhook(String paymentId) throws Exception {
        MercadoPagoConfig.setAccessToken(mpAccessToken);
        PaymentClient client = new PaymentClient();
        Payment payment = client.get(Long.parseLong(paymentId));

        String externalRef = payment.getExternalReference();
        Pago pago = pagoRepository.findById(Long.parseLong(externalRef))
            .orElseThrow(() -> new RuntimeException("Pago no encontrado: " + externalRef));

        if (pago.getEstado().equals("APROBADO")) return; // idempotencia

        pago.setMpPaymentId(paymentId);
        pago.setUpdatedAt(LocalDateTime.now());

        if ("approved".equals(payment.getStatus())) {
            BigInteger cantidadWei = pago.getCantidadIdea()
                .multiply(BigDecimal.TEN.pow(18))
                .toBigInteger();

            // Treasury compra IDEA del pool con USDC
            String txSwap = ideaSwapService.ejecutarSwap(cantidadWei);
            pago.setTxHashSwap(txSwap);

            // Treasury transfiere IDEA al wallet del usuario
            String txTransfer = ideaSwapService.transferirIdea(pago.getWalletAddress(), cantidadWei);
            pago.setTxHashTransfer(txTransfer);

            pago.setEstado("APROBADO");
        } else if ("rejected".equals(payment.getStatus())) {
            pago.setEstado("RECHAZADO");
        }

        pagoRepository.save(pago);
    }

    public List<PagoResponse> obtenerHistorial(Long usuarioId) {
        return pagoRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId)
            .stream().map(PagoResponse::from).toList();
    }
}
```

### Paso 8 — Controller

```java
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    // POST /api/pagos/preferencia — requiere auth JWT
    @PostMapping("/preferencia")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CrearPreferenciaResponse> crearPreferencia(
            @RequestBody CrearPreferenciaRequest request,
            @AuthenticationPrincipal UserDetails user) throws Exception {

        Long usuarioId = ((CustomUserDetails) user).getId();
        return ResponseEntity.ok(
            pagoService.crearPreferencia(usuarioId, request.getWalletAddress(),
                request.getCantidadIdea())
        );
    }

    // POST /api/pagos/webhook — público, lo llama MercadoPago
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestParam String type,
                                         @RequestParam("data.id") String paymentId) throws Exception {
        if ("payment".equals(type)) {
            pagoService.procesarWebhook(paymentId);
        }
        return ResponseEntity.ok().build();
    }

    // GET /api/pagos/historial — requiere auth JWT
    @GetMapping("/historial")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PagoResponse>> historial(
            @AuthenticationPrincipal UserDetails user) {
        Long usuarioId = ((CustomUserDetails) user).getId();
        return ResponseEntity.ok(pagoService.obtenerHistorial(usuarioId));
    }
}
```

> **Importante**: el endpoint `/webhook` debe estar excluido del filtro JWT en `SecurityConfig`. Agregar `.requestMatchers("/api/pagos/webhook").permitAll()`.

---

## Frontend

### Paso 1 — Agregar endpoints en `src/config/api.js`

```js
export const PAGOS_ENDPOINTS = {
  CREAR_PREFERENCIA: `${API_BASE_URL}/api/pagos/preferencia`,
  HISTORIAL: `${API_BASE_URL}/api/pagos/historial`,
};
```

### Paso 2 — Hook `src/hooks/use-pagos.js`

```js
import { useMutation, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api-client";
import { PAGOS_ENDPOINTS } from "@/config/api";

export function useCrearPreferencia() {
  return useMutation({
    mutationFn: ({ cantidadIdea, walletAddress }) =>
      apiRequest(PAGOS_ENDPOINTS.CREAR_PREFERENCIA, {
        method: "POST",
        body: { cantidadIdea, walletAddress },
      }),
  });
}

export function useHistorialPagos() {
  return useQuery({
    queryKey: ["pagos", "historial"],
    queryFn: () => apiRequest(PAGOS_ENDPOINTS.HISTORIAL),
  });
}
```

### Paso 3 — Nueva página `src/pages/comprar-idea/index.jsx`

```jsx
import { useState } from "react";
import { useAccount } from "wagmi";
import { useCrearPreferencia } from "@/hooks/use-pagos";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function ComprarIdeaPage() {
  const [cantidad, setCantidad] = useState("");
  const { address } = useAccount();
  const { mutate: crearPreferencia, isPending, data, error } = useCrearPreferencia();

  function handleCotizar() {
    if (!cantidad || !address) return;
    crearPreferencia(
      { cantidadIdea: Number(cantidad), walletAddress: address },
      {
        onSuccess: (res) => {
          // Guardamos el checkoutUrl para que el usuario lo confirme antes de ir
          // (ver paso de confirmación abajo)
          window.location.href = res.checkoutUrl;
        },
      }
    );
  }

  return (
    <div className="max-w-md mx-auto mt-10">
      <Card>
        <CardHeader>
          <CardTitle>Comprar $IDEA</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div>
            <label className="text-sm font-medium">Cantidad de $IDEA</label>
            <Input
              type="number"
              min={1}
              value={cantidad}
              onChange={(e) => setCantidad(e.target.value)}
              placeholder="Ej: 100"
            />
          </div>

          {data && (
            <div className="rounded-md bg-muted p-3 text-sm space-y-1">
              <p>Precio actual del pool: <strong>{data.precioIdeaUsdc} USDC / IDEA</strong></p>
              <p>Tipo de cambio: <strong>${data.tipoCambioArsUsd} ARS / USD</strong></p>
              <p className="text-base font-semibold text-foreground">
                Total a pagar: ${data.totalArs} ARS
              </p>
              <p className="text-xs text-muted-foreground">
                Precio bloqueado al momento de cotizar. Puede variar si el pool se mueve antes de confirmar.
              </p>
            </div>
          )}

          {!address && (
            <p className="text-sm text-destructive">
              Conectá tu wallet para continuar.
            </p>
          )}

          <Button
            onClick={handleCotizar}
            disabled={!cantidad || !address || isPending}
          >
            {isPending ? "Cotizando..." : "Cotizar y pagar con MercadoPago"}
          </Button>

          {error && (
            <p className="text-sm text-destructive">{error.message}</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
```

### Paso 4 — Páginas de resultado

`src/pages/comprar-idea/exito.jsx`:
```jsx
export default function CompraExitoPage() {
  return (
    <div className="text-center mt-20">
      <h1 className="text-2xl font-bold text-green-600">¡Compra exitosa!</h1>
      <p className="text-muted-foreground mt-2">
        Tus $IDEA serán acreditados en tu billetera en instantes una vez confirmada la transacción on-chain.
      </p>
    </div>
  );
}
```

`src/pages/comprar-idea/fallo.jsx` — similar con mensaje de error y botón para reintentar.

### Paso 5 — Registrar rutas en `src/router/index.jsx`

```jsx
// Dentro del bloque de rutas protegidas (ProtectedRoute + DashboardLayout):
{ path: "comprar-idea",          element: <ComprarIdeaPage /> },
{ path: "comprar-idea/exito",    element: <CompraExitoPage /> },
{ path: "comprar-idea/fallo",    element: <CompraFalloPage /> },
{ path: "comprar-idea/pendiente",element: <CompraPendientePage /> },
```

### Paso 6 — Botón en la página de billetera

En `src/pages/wallet/index.jsx`, junto al saldo de $IDEA:

```jsx
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

<Button asChild size="sm">
  <Link to="/comprar-idea">Comprar $IDEA</Link>
</Button>
```

---

## Checklist de implementación

### Blockchain (prerequisito)
- [ ] Verificar que `IdeaSwap` tiene liquidez cargada en Base Sepolia (`ideaReserve > 0` y `usdcReserve > 0`)
- [ ] Si el pool está vacío: la treasury wallet debe llamar `addLiquidity()` con IDEA y USDC de prueba
- [ ] Verificar que la treasury wallet tiene USDC suficiente en Base Sepolia para ejecutar swaps
- [ ] Verificar que la treasury wallet tiene ETH en Base Sepolia para pagar gas

### Backend
- [ ] Agregar dependencia MercadoPago en `pom.xml`
- [ ] Crear variables de entorno (`MP_ACCESS_TOKEN`, `TREASURY_PRIVATE_KEY`, `IDEA_TOKEN_CONTRACT_ADDRESS`, `IDEA_SWAP_CONTRACT_ADDRESS`, `USDC_CONTRACT_ADDRESS`)
- [ ] Crear migración `V23__add_pagos.sql`
- [ ] Implementar `Pago` entity + `PagoRepository`
- [ ] Implementar `TipoCambioService` (dolarapi.com)
- [ ] Implementar `IdeaSwapService` (precio del pool + ejecutar swap + transferir IDEA)
- [ ] Generar wrappers Java de `IdeaSwap` y `ERC20` con `web3j generate`
- [ ] Implementar `PagoService` (crear preferencia + procesar webhook)
- [ ] Implementar `PagoController` con los 3 endpoints
- [ ] Excluir `/api/pagos/webhook` del filtro JWT en `SecurityConfig`
- [ ] Configurar ngrok o túnel de Railway para recibir webhooks en local

### Frontend
- [ ] Agregar endpoints en `src/config/api.js`
- [ ] Crear `src/hooks/use-pagos.js`
- [ ] Crear `src/pages/comprar-idea/index.jsx`
- [ ] Crear `src/pages/comprar-idea/exito.jsx`
- [ ] Crear `src/pages/comprar-idea/fallo.jsx`
- [ ] Crear `src/pages/comprar-idea/pendiente.jsx`
- [ ] Agregar rutas en `src/router/index.jsx`
- [ ] Agregar botón "Comprar $IDEA" en `src/pages/wallet/index.jsx`

---

## Consideraciones importantes

**Pool vacío**: si `IdeaSwap` no tiene liquidez, `getAmountsOut()` divide por cero y el contrato revierte. Hay que cargar liquidez antes de que HU-33 pueda funcionar. Es el prerequisito más crítico.

**Burn del 1% en transfers**: `IdeaToken` quema el 1% de cada transfer. Al transferir IDEA al usuario, este recibe el 99% de lo enviado. Para que reciba exactamente `cantidadIdea`, el transfer debe enviar `cantidadIdea / 0.99`. Decidir si el burn lo absorbe la plataforma o el usuario.

**Slippage**: el precio se fija al crear la preferencia. Si el pool se mueve mientras el usuario paga en MP (puede tomar minutos), la treasury ejecuta el swap al precio actual del pool, que puede diferir del cotizado. El riesgo lo asume la plataforma. Para protegerse, se puede agregar un TTL a la preferencia (ej: rechazar webhooks si el pago llegó más de 15 minutos después de la cotización).

**Dos transacciones on-chain**: el webhook ejecuta primero el swap (treasury ← IDEA del pool) y luego el transfer (treasury → usuario). Ambas consumen gas. Si el transfer falla después del swap exitoso, el estado queda inconsistente — registrar ambos tx_hash por separado para poder diagnosticar.

**Webhook en desarrollo local**: MercadoPago necesita llamar a tu backend desde internet. Usá [ngrok](https://ngrok.com/) o el túnel de Railway para exponer el puerto local.

**Idempotencia del webhook**: MP puede llamar al webhook más de una vez. El `procesarWebhook` ya contempla esto con el chequeo `if (estado == "APROBADO") return`.

**Seguridad de la treasury**: la private key nunca debe estar en el código. Usar variables de entorno y en producción un secret manager (ej. AWS Secrets Manager, Railway secrets).
