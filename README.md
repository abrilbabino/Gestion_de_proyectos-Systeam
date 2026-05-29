# IDEAFY — Documentación Completa del Proyecto

Este archivo reúne toda la documentación técnica del proyecto IDEAFY en un solo lugar.

---

## Índice

- [1. Stage 1 — Smart Contracts de IDEAFY](#1-stage-1--smart-contracts-de-ideafy)
- [2. Stage 2 — Backend Java](#2-stage-2--backend-java)
- [3. Stage 4 — Dynamic Pricing con Factor de Rendimiento](#3-stage-4--dynamic-pricing-con-factor-de-rendimiento)
- [4. Stage 5 — Marketplace de Sub-Tokens](#4-stage-5--marketplace-de-sub-tokens)
- [5. Stage 6 — Governance REST API](#5-stage-6--governance-rest-api)
- [6. API Gateway — Explicación completa](#6-api-gateway--explicación-completa)
- [7. Guía para levantar todo desde cero](#7-guía-para-levantar-todo-desde-cero)

---

## 1. Stage 1 — Smart Contracts de IDEAFY
# Stage 1 — Smart Contracts de IDEAFY

## Clase completa: de un monolito a 8 contratos modulares

---

## Índice

1. [Introducción: ¿Por dónde empezamos y por qué?](#1-introducción-por-dónde-empezamos-y-por-qué)
2. [Lección 1 — Conceptos básicos que tenés que saber antes de leer el código](#2-lección-1--conceptos-básicos-que-tenés-que-saber-antes-de-leer-el-código)
3. [Lección 2 — El problema: qué teníamos antes y por qué estaba mal](#3-lección-2--el-problema-qué-teníamos-antes-y-por-qué-estaba-mal)
4. [Lección 3 — La solución: 8 contratos, cada uno con un propósito](#4-lección-3--la-solución-8-contratos-cada-uno-con-un-propósito)
5. [Lección 4 — IdeaToken.sol: la moneda de plataforma $IDEA](#5-lección-4--ideatokensol-la-moneda-de-plataforma-idea)
6. [Lección 5 — SubToken.sol: el token por proyecto](#6-lección-5--subtokensol-el-token-por-proyecto)
7. [Lección 6 — IdeafyFactory.sol: la fábrica que crea SubTokens](#7-lección-6--ideafyfactorysol-la-fábrica-que-crea-subtokens)
8. [Lección 7 — OfferingContract.sol: la venta primaria con protección](#8-lección-7--offeringcontractsol-la-venta-primaria-con-protección)
9. [Lección 8 — DividendDistributor.sol: dividendos en $IDEA (el más importante)](#9-lección-8--dividenddistributorsol-dividendos-en-idea-el-más-importante)
10. [Lección 9 — IdeaSwap.sol: el pool de liquidez $IDEA ↔ USDC](#10-lección-9--ideaswapsol-el-pool-de-liquidez-idea--usdc)
11. [Lección 10 — IdeaMarketplace.sol: el mercado secundario](#11-lección-10--ideamarketplacesol-el-mercado-secundario)
12. [Lección 11 — IdeaGovernance.sol: el poder de los holders](#12-lección-11--ideagovernancesol-el-poder-de-los-holders)
13. [Comparativa completa: antes vs después](#13-comparativa-completa-antes-vs-después)
14. [Escalabilidad 100x: qué aguanta y qué no](#14-escalabilidad-100x-qué-aguanta-y-qué-no)
15. [Pendiente para Stage 2](#15-pendiente-para-stage-2)
16. [Apéndice A — Glosario de conceptos](#16-apéndice-a--glosario-de-conceptos)

---

## 1. Introducción: ¿Por dónde empezamos y por qué?

### El punto de partida

Cuando arrancamos el proyecto teníamos un sistema que **funcionaba** para la demo: se podían crear
proyectos, invertir, y hacer refunds. Pero cuando el profesor nos pasó su checklist de 10 preguntas,
nos dimos cuenta de que el modelo **no respondía satisfactoriamente** a varias:

- *"¿Qué representa exactamente tu token?"*
- *"¿Por qué alguien lo querría además de para revenderlo?"*
- *"¿Qué quema o bloquea tokens?"*
- *"Si hay 100x usuarios, ¿el modelo aguanta?"*

**¿Qué hicimos entonces?** Paramos de codear y nos pusimos a pensar. Respondimos cada pregunta
por escrito (está en `IDEAFY_ARQUITECTURA_PROFESOR.md`). De esas respuestas **salió el diseño
completo** de los 8 contratos que ves acá.

### ¿Por qué arrancamos por los contratos y no por el backend?

Porque los contratos son la **base económica** de todo el sistema. El backend Java solo
consume los contratos. Si los contratos están mal diseñados:

- No importa qué tan lindo sea el backend: la economía no funciona
- Cambiar un contrato después de deployarlo en Mainnet es **carísimo** (o imposible)
- Los errores en contratos pueden significar **pérdida de plata real**

En cambio, cambiar el backend es "solo código". Por eso primero los contratos, después el backend.

### ¿Y por qué en este orden? (IdeaToken → SubToken → Factory → Offering → Dividendos → Swap → Marketplace → Governance)

El orden de creación no es aleatorio. Sigue **dependencias**:

1. **IdeaToken** primero porque es la moneda base. Sin $IDEA no funciona nada.
2. **SubToken** después porque es el token que se va a crear para cada proyecto. Necesita entender cómo funciona el token base.
3. **IdeafyFactory** después porque es la que crea SubTokens. Necesita saber qué es un SubToken para deployarlo.
4. **OfferingContract** después porque maneja la venta de SubTokens. Necesita la Factory para distribuir tokens y $IDEA para recibir pagos.
5. **DividendDistributor** después porque usa $IDEA para pagar dividendos y la Factory para saber qué SubTokens existen.
6. **IdeaSwap** después porque es el pool de liquidez de $IDEA. Es independiente pero necesita $IDEA.
7. **IdeaMarketplace** después porque es el mercado secundario de SubTokens. Necesita $IDEA y SubToken.
8. **IdeaGovernance** al final porque es la capa de decisión sobre todo lo anterior.

**Regla práctica:** Si el contrato A necesita al contrato B para funcionar, primero se hace B.

---

## 2. Lección 1 — Conceptos básicos que tenés que saber antes de leer el código

Antes de ver un solo `.sol`, tenemos que entender **qué significa cada cosa**. Si no entendés
estos conceptos, el código no te va a hacer sentido.

### Token

Un token es un **activo digital** que vive en una blockchain. Pensalo como una ficha de casino
digital: tiene un valor, se puede transferir, y el contrato dice exactamente cuántas fichas existen.
En Ethereum, los tokens siguen el estándar **ERC-20** que define funciones básicas como:
`balanceOf()`, `transfer()`, `transferFrom()`, `approve()`, `totalSupply()`.

**Analogía:** Un token ERC-20 es como una moneda en un videojuego. El juego dice "hay 100 monedas
en total", y cada jugador tiene un saldo. Nadie puede crear monedas de la nada porque el código
del juego lo impide.

### Supply

El **supply** (suministro) es la cantidad total de tokens que existen. Hay dos tipos:

| Tipo | Descripción | Ejemplo |
|---|---|---|
| **Total Supply** | Todos los tokens que existen, incluyendo los quemados | 100.000.000 $IDEA |
| **Circulating Supply** | Los tokens que están en circulación (excluye quemados y bloqueados) | 80.000.000 $IDEA (si se quemaron 20M) |

**¿Por qué importa?** Porque el precio de un token está determinado por oferta y demanda.
Si el supply es fijo (como en $IDEA), el precio solo puede subir si aumenta la demanda.
Si el supply es variable (alguien puede mintear más), el precio puede bajar aunque haya demanda.

### Mint

**Mint** (acuñar) es crear nuevos tokens de la nada. Es lo contrario de quemar.

```solidity
_mint(address para, uint256 cantidad);
```

**¿Por qué es peligroso?** Si cualquiera puede mintear, el supply puede crecer infinitamente
y el token pierde valor. Por eso en nuestro `IdeaToken` **no hay función mint()**. Todos los
tokens se crean una sola vez en el constructor y después nadie puede crear más.

### Burn

**Burn** (quemar) es destruir tokens, sacándolos de circulación para siempre. Se hace enviando
tokens a `address(0)` (la dirección cero, que nadie controla).

```solidity
_burn(address desde, uint256 cantidad);
// o equivalentemente:
super._update(from, address(0), cantidad);
```

**¿Por qué quemamos?** Para hacer el token **deflacionario**: si siempre se quema un porcentaje
en cada transacción, con el tiempo hay menos tokens, y si la demanda se mantiene, el precio sube.

### Cap (límite máximo)

Un **cap** es un límite superior de supply que no se puede superar. En nuestro `IdeaToken`:

```solidity
uint256 public constant MAX_SUPPLY = 100_000_000e18;
```

Esto significa que **nunca** puede haber más de 100M de $IDEA. Ni el admin, ni el owner,
ni un hack — es matemáticamente imposible porque el contrato no tiene mint().

### Soft Cap y Hard Cap

Son límites en una **ronda de inversión** (offering):

| Concepto | Significado | Ejemplo |
|---|---|---|
| **Soft Cap** | Mínimo necesario para que la ronda sea exitosa | 30.000 $IDEA |
| **Hard Cap** | Máximo que se puede recaudar | 50.000 $IDEA |

**¿Por qué existen?**

- **Soft Cap:** El proyecto necesita un mínimo para funcionar. Si no junta eso, no tiene sentido
  que reciba la plata. Los inversores recuperan su dinero (refund).
- **Hard Cap:** Protege al inversor de dilución. Si el proyecto ya juntó suficiente, no puede
  seguir vendiendo tokens infinitamente.

**Analogía:** Es como una preventa de un producto. Si no se venden al menos 100 unidades (soft cap),
se cancela y todos recuperan su plata. Si se venden 500 (hard cap), se cierra la preventa.

### Swap

**Swap** es intercambiar un token por otro. En nuestro caso, $IDEA ↔ USDC. No es comprar con
plata fiat — es intercambiar un token por otro.

**¿Cómo funciona?** Necesitás un **pool de liquidez** (ver más abajo) que tenga ambos tokens.
El swap toma uno, lo agrega al pool, y saca el otro según el precio actual.

### Pool de Liquidez

Es un **contenedor de tokens** que permite hacer swaps. Alguien (el treasury, los LP providers)
pone tokens de ambos tipos en el pool, y después cualquiera puede intercambiar entre ellos.

```
Pool de liquidez $IDEA/USDC:
  - 100.000 $IDEA
  - 100.000 USDC
  - Precio: 1 $IDEA = 1 USDC

Si alguien compra 1.000 $IDEA con USDC:
  - Pone 1.000 USDC en el pool
  - Saca 1.000 $IDEA del pool
  - Nuevo balance: 101.000 $IDEA + 99.000 USDC
  - Nuevo precio: 1 $IDEA ≈ 0.98 USDC (subió el precio de USDC, bajó el de $IDEA)
```

### Slippage

Es la **diferencia entre el precio esperado y el precio real** de un swap. Pasa porque cada swap
cambia las reservas del pool. Mientras más grande sea el swap comparado con el pool, más slippage.

### Fee

Es una **comisión** que cobra la plataforma por cada operación. En nuestro sistema:

| Operación | Fee | A dónde va |
|---|---|---|
| Swap $IDEA↔USDC | 1% | Treasury |
| Marketplace (compra/venta) | 2.5% | Treasury |
| Transferencia de $IDEA | 1% (burn) | Se quema (desaparece) |

### Treasury

Es la **billetera de la plataforma**. Una dirección controlada por el equipo que recibe los fees
y administra los fondos del ecosistema. En nuestro caso, el treasury también provee la liquidez
inicial del pool de $IDEA/USDC.

### Holdear

Viene del inglés **hold** (mantener). Significa **no vender**. Un holder es alguien que compra
y mantiene el token esperando que suba de precio, en vez de revenderlo rápido.

**¿Por qué alguien holdearía $IDEA?** Porque da derechos:
- Governance (votar)
- Acceso prioritario a rondas de inversión
- Cross-rewards (descuentos)
- Dividendos (si tiene sub-tokens)
- Staking (en el futuro)

### Order Book (Libro de Órdenes)

Es una **lista de órdenes de compra y venta** para un activo. En nuestro Marketplace:

```
Órdenes de venta de $IDEA-BREW:
  - 50 tokens a 6 $IDEA c/u (vendedor: 0x123...)
  - 20 tokens a 7 $IDEA c/u (vendedor: 0x456...)
  - 100 tokens a 5.5 $IDEA c/u (vendedor: 0x789...)

Si querés comprar 30, comprás las más baratas primero.
```

En nuestro contrato, el order book es simple: cada vendedor lista sus tokens a un precio fijo,
y el comprador elige qué orden comprar.

### AMM (Automated Market Maker)

Es una **alternativa al order book**. En vez de tener vendedores y compradores listando precios,
usás una fórmula matemática para determinar el precio según las reservas del pool. Uniswap es un AMM.

**Diferencia clave:**

| Order book | AMM |
|---|---|
| Necesita vendedores listando a distintos precios | El precio lo determina una fórmula |
| Puede tener poca liquidez si nadie lista | Siempre hay precio (mientras haya reservas) |
| El comprador elige qué orden tomar | El comprador acepta el precio actual |

Nosotros usamos order book para el marketplace (sub-tokens) y un modelo simple de pool para el swap ($IDEA/USDC).

### Governance (Gobierno)

Es el **sistema de votación** donde los holders deciden el futuro de la plataforma.

**¿Cómo funciona?**
1. Alguien crea una **propuesta** (necesita tener X tokens para proponer)
2. Los holders **votan** a favor o en contra (1 token = 1 voto)
3. Después de un tiempo, se **cuentan los votos**
4. Si hay suficiente participación (quorum) y gana el sí → la propuesta se ejecuta

**¿Qué se puede votar?**
- Aprobar o rechazar un proyecto nuevo
- Cambiar un fee
- Usar fondos del treasury

### Treasury (ver arriba)

### Claim()

**Claim** (reclamar) es cuando un usuario pide activamente recibir algo que le corresponde.
En nuestro sistema:

- `claimTokens()` en OfferingContract: el inversor reclama sus sub-tokens después de una ronda exitosa
- `claim()` en DividendDistributor: el holder reclama sus dividendos acumulados

**¿Por qué claim() y no enviar automáticamente?** Porque enviar a N personas requiere un loop
que cuesta N veces gas. Con claim(), cada uno paga su propio gas.

### Dividendos (dividends)

Son **pagos periódicos** a los holders de un activo. En nuestro caso, los holders de sub-tokens
reciben dividendos en $IDEA proporcionales a la cantidad de sub-tokens que tienen.

**Flujo completo:**

```
1. Cervecería vende USD 10.000 en cerveza en un mes
2. Ganancia neta: USD 5.000
3. Rubro Gastro = 40% → USD 2.000 van a inversores
4. Esos USD 2.000 se convierten a $IDEA (vía swap)
5. Los $IDEA se distribuyen a holders de $IDEA-BREW
6. Cada holder reclama su parte con claim()
```

### USDC / USDT

Son **stablecoins** — tokens que valen aproximadamente 1 dólar cada uno.

| Token | Emisor | Blockchain |
|---|---|---|
| USDC | Circle | Ethereum, Base, muchas más |
| USDT | Tether | Ethereum, muchas más |

**¿Por qué usamos USDC y no USDT?** USDC está más integrado con DeFi, tiene más liquidez en Base,
y es más fácil de integrar con Uniswap. Para el PoC cualquiera funciona.

### Accumulator Pattern

Es una **técnica para distribuir recompensas sin loops**. La explicación completa está en
la Lección 8 (DividendDistributor), pero la idea es:

```
En vez de: for each holder { transfer(reward) }  ← O(n), carísimo

Hacemos:
  - Guardamos un acumulador: dividendPerToken = total / supply  ← O(1)
  - Cada holder reclama: owed = (acumulador - su_ultimo_snapshot) * su_balance  ← O(1)
```

### Factory Pattern

Es un **patrón de diseño** donde un contrato (la factory) crea otros contratos.

```
Factory.launchProject()
  → new SubToken(...)  ← deploya un contrato nuevo
  → guarda la dirección
  → vuelve
```

**¿Por qué es útil?**

- **Escalabilidad:** Podés crear tantos SubTokens como quieras, cada uno con sus propios datos
- **Consistencia:** Todos los SubTokens tienen la misma estructura (mismo código)
- **Registro central:** La factory sabe qué SubTokens existen

### Proxy / Upgradeability

Es la capacidad de **cambiar el código de un contrato después de deployado**. Se usa cuando
querés poder arreglar bugs o agregar funcionalidad.

**Cómo funciona:**

```
Usuario → llama a Proxy → Proxy delega a Implementación
                              ↓
                      (el código real)

Si querés actualizar:
  Deployás una nueva Implementación
  El Proxy ahora apunta a la nueva
  Los usuarios ni se enteran
```

**Trade-off:** Más complejidad técnica, posibilidad de errores en la delegación, pero necesario
para contratos que pueden necesitar cambios.

---

### Evolución de esta sección

**Versión 1 (cuando se creó el documento):**
> ~~En nuestro caso, el SubToken **no tiene proxy por ahora**. Lo agregaremos en Stage 2 con UUPS.~~

**Versión 2 (después de implementar 100x scalability):**
> El **SubToken usa UUPS proxy desde Stage 1**. La IdeafyFactory deploya `ERC1967Proxy` apuntando a
> una implementación de SubToken. Si necesitamos actualizar el SubToken en el futuro, deployamos una
> nueva implementación y llamamos `upgradeTo()` en cada proxy. Sin esto, 10.000 SubTokens ya
> deployados serían imposibles de actualizar.

**¿Por qué el cambio?** Cuando analizamos el escenario de 10.000 proyectos, nos dimos cuenta de que
si deployábamos SubTokens sin proxy y después encontrábamos un bug, no había forma de arreglarlo.
Con UUPS, deployar una nueva implementación cuesta ~USD 0.012 y actualizar 10.000 proxies es un script
de 5 minutos.

---

## 3. Lección 2 — El problema: qué teníamos antes y por qué estaba mal

### Contrato 1: InvestmentSwap.sol (111 líneas)

Este era nuestro contrato principal. Hacía **3 cosas distintas** en el mismo lugar:

```solidity
contract InvestmentSwap is Ownable, ReentrancyGuard {

    IERC20 public immutable idea;
    address public immutable treasury;
    mapping(uint256 => address) public tokenDeProyecto;
    mapping(address => uint256) public proyectoDeToken;
    address[] public tokensCreados;

    constructor(address _idea, address _treasury) Ownable(msg.sender) {
        idea = IERC20(_idea);
        treasury = _treasury;
    }
```

**Línea por línea:**
- `Ownable(msg.sender)` → hereda de OpenZeppelin, el que deploya es el dueño
- `ReentrancyGuard` → protege contra ataques de reentrancia (cuando un contrato malicioso llama
  a una función varias veces antes de que termine la primera)
- `IERC20 public immutable idea` → guarda la dirección del token $IDEA (inmutable, no se puede cambiar)
- `address public immutable treasury` → dirección del treasury (inmutable)
- `mapping(uint256 => address) tokenDeProyecto` → proyecto ID → dirección del SubToken
- `mapping(address => uint256) proyectoDeToken` → dirección del SubToken → proyecto ID (el inverso)
- `address[] public tokensCreados` → lista de todos los SubTokens creados

Ya en el constructor hay un problema: recibe `_idea` y `_treasury` como parámetros.
¿Qué pasa si nos equivocamos y pasamos una dirección incorrecta? No hay forma de cambiarlo
porque son `immutable`. En los contratos nuevos evitamos esto usando constructores más simples.

#### crearTokenProyecto() — El "factory"

```solidity
function crearTokenProyecto(uint256 proyectoId, string calldata nombre, string calldata simbolo, uint256 supplyInicial) external onlyOwner returns (address) {
    require(tokenDeProyecto[proyectoId] == address(0),
        "InvestmentSwap: token already exists for this project");
    ProjectToken nuevoToken = new ProjectToken(nombre, simbolo, address(this));
    address tokenAddress = address(nuevoToken);
    if (supplyInicial > 0) {
        nuevoToken.mint(treasury, supplyInicial);
    }
    tokenDeProyecto[proyectoId] = tokenAddress;
    proyectoDeToken[tokenAddress] = proyectoId;
    tokensCreados.push(tokenAddress);
    emit TokenDeProyectoCreado(proyectoId, tokenAddress);
    return tokenAddress;
}
```

**¿Qué hace?**
1. Verifica que el proyecto no tenga ya un token
2. Crea un nuevo ProjectToken (hereda de ERC-20)
3. Si hay supply inicial, lo mintea al treasury
4. Guarda la relación proyecto → token en los mappings

**Problemas:**
- `onlyOwner` → **solo el owner** puede crear tokens. Si el owner pierde su private key, no se
  pueden crear más proyectos. No hay roles granulars.
- `supplyInicial` va al **treasury**, pero ¿por qué? El treasury es la billetera de la plataforma,
  no el creador del proyecto. Los tokens deberían ir a la factory para que ella los distribuya.
- No guarda **metadatos del proyecto** (rubro, dividend%). Si querés saber qué rubro tiene un
  proyecto, tenés que consultar la base de datos off-chain.
- El nombre y símbolo son **strings libres**. Alguien podría crear un token con nombre
  "Bitcoin" o símbolo "ETH" y confundir inversores.

#### invest() — El "exchange"

```solidity
function invest(
    uint256 proyectoId,
    uint256 ideaAmount,
    uint256 subTokenAmount,
    address investor
) external nonReentrant {
    require(ideaAmount > 0, "InvestmentSwap: ideaAmount must be > 0");
    require(subTokenAmount > 0, "InvestmentSwap: subTokenAmount must be > 0");
    require(investor != address(0), "InvestmentSwap: invalid investor address");
    address projectTokenAddr = tokenDeProyecto[proyectoId];
    require(projectTokenAddr != address(0),
        "InvestmentSwap: project token not deployed");
    require(idea.transferFrom(msg.sender, treasury, ideaAmount),
        "InvestmentSwap: IDEA transfer failed (check allowance + balance)");
    ProjectToken(projectTokenAddr).mint(investor, subTokenAmount);
    emit InvestmentMade(proyectoId, investor, ideaAmount, subTokenAmount);
}
```

**¿Qué hace?**
1. Recibe `ideaAmount` de $IDEA del inversor
2. Los envía al **treasury** (no al creador del proyecto)
3. **Mintea** `subTokenAmount` tokens al inversor

**Problemas graves:**
- **Sin límites:** No hay soft cap ni hard cap. Podés invertir 1 $IDEA o 1.000.000.
- **Sin tiempo:** Podés invertir hoy, mañana, o dentro de un año. No hay ventana de inversión.
- **Entrega inmediata:** Apenas invertís, recibís los tokens. Si después el proyecto no funciona,
  ya tenés tokens inservibles. No hay refund si el proyecto no junta suficiente capital.
- **Los $IDEA van al treasury**, no al creador. El creador del proyecto nunca recibe la plata
  de sus inversores directamente. ¿Por qué está diseñado así? Porque el treasury después
  "administra" los fondos. Pero esto centraliza el control.
- **`subTokenAmount` lo define el que llama**, no el contrato. Podrías poner `subTokenAmount = 1`
  y `ideaAmount = 1000` y te llevarías tokens regalados (bueno, no tanto, porque el backend
  calcula esto, pero el contrato no lo valida).

#### refund() — El "refund"

```solidity
function refund(
    uint256 proyectoId,
    uint256 subTokenAmount,
    address holder,
    address investor
) external onlyOwner nonReentrant {
    require(subTokenAmount > 0, "InvestmentSwap: subTokenAmount must be > 0");
    address projectTokenAddr = tokenDeProyecto[proyectoId];
    require(projectTokenAddr != address(0),
        "InvestmentSwap: project token not deployed");
    ProjectToken(projectTokenAddr).burnFrom(holder, subTokenAmount);
    emit RefundMade(proyectoId, investor, 0, subTokenAmount);
}
```

**Problemas:**
- **Solo el owner puede hacer refund.** Si el owner está de vacaciones o perdió su wallet, los
  inversores no pueden recuperar su plata.
- **No devuelve $IDEA.** Solo quema los sub-tokens. ¿Dónde está la parte de devolver los $IDEA
  que el inversor puso? No existe en este contrato. Tendría que hacerse desde el backend.
- **El parámetro `investor` no se usa para validar nada.** Solo para el evento. Podrías pasar
  cualquier dirección.

### Contrato 2: ProjectToken.sol (50 líneas)

```solidity
contract ProjectToken is ERC20, Ownable {

    uint256 public constant TASA_QUEMA = 10; // 10 bps = 0.1%

    constructor(string memory nombre, string memory simbolo, address dueno)
        ERC20(nombre, simbolo) Ownable(dueno) {}

    function mint(address para, uint256 cantidad) external onlyOwner {
        _mint(para, cantidad);
        emit TokensMinteados(para, cantidad);
    }

    function burnFrom(address desde, uint256 cantidad) external onlyOwner {
        _burn(desde, cantidad);
        emit TokensQuemados(desde, cantidad);
    }

    function _update(address desde, address para, uint256 valor) internal override {
        if (desde == address(0) || para == address(0)) {
            super._update(desde, para, valor);
            return;
        }
        uint256 cantidadAQuemar = (valor * TASA_QUEMA) / 10000;
        if (cantidadAQuemar == 0) {
            super._update(desde, para, valor);
            return;
        }
        uint256 cantidadAEnviar = valor - cantidadAQuemar;
        _burn(desde, cantidadAQuemar);
        emit TokensQuemados(desde, cantidadAQuemar);
        super._update(desde, para, cantidadAEnviar);
    }
}
```

**Problemas por línea:**

1. `mint()` es `onlyOwner` → cualquier owner puede inflar el supply infinitamente. Esto ya
   pasó en proyectos reales (ej: hack de CREAM Finance donde el owner mintió tokens para
   manipular pools).

2. `burnFrom()` es `onlyOwner` → solo el owner puede quemar. En nuestro nuevo diseño,
   solo la factory puede quemar (en refunds), y está validado por roles.

3. `TASA_QUEMA = 10` (0.1%) en cada transfer → ¿por qué el sub-token tiene quema? Si cada
   vez que transferís tu token de la cervecería perdés 0.1%, no querés transferirlo. Esto
   mata la liquidez del mercado secundario.

4. **No guarda metadata del proyecto**: proyectoId, rubro, dividendBps. Si querés saber
   a qué proyecto pertenece el token 0xABC... tenés que hacer una consulta a la base de datos.

5. **Ownable en vez de AccessControl**: Solo hay un dueño. No podés tener roles granulars
   (creator, allocator, admin). Todo lo decide el owner.

### Resumen de los problemas

| # | Problema | Consecuencia |
|---|---|---|
| 1 | InvestmentSwap hace factory + exchange + refund | Si queremos agregar governance o marketplace, hay que tocar InvestmentSwap o crear contratos que no se conectan |
| 2 | Sin soft/hard cap en inversiones | Un proyecto puede recaudar $1 cuando necesita $50.000. Los inversores no tienen protección |
| 3 | Entrega inmediata de tokens | Si la ronda falla, ya entregamos tokens inservibles. El refund solo quema tokens, no devuelve $IDEA |
| 4 | Dividendos no existen on-chain | El backend Java los calcula y envía USDT manualmente. No hay transparencia ni descentralización |
| 5 | El sub-token tiene quema | Nadie quiere transferir un token que se quema en cada transfer. El marketplace no funciona |
| 6 | ProjectToken sin metadatos | No sabés a qué proyecto pertenece un token on-chain. Todo depende de la base de datos |
| 7 | mint() público (soloOwner) | El supply puede inflarse. No hay predictibilidad |
| 8 | Refund solo owner | Si el owner falla, los inversores pierden su plata |
| 9 | Sin governance | Los holders no tienen poder de decisión. Es un sistema centralizado |
| 10 | Sin swap $IDEA↔USDC | No hay liquidez. El creador no puede convertir USDC a $IDEA para dividendos |

---

## 4. Lección 3 — La solución: 8 contratos, cada uno con un propósito

### Principios de diseño

Cuando nos sentamos a diseñar los contratos nuevos, definimos **5 principios** que guiaron
cada decisión:

#### 1. Separation of Concerns (Separación de Responsabilidades)

Cada contrato hace **una sola cosa** y la hace bien.

| Contrato | Su única responsabilidad |
|---|---|
| IdeaToken | Ser la moneda de plataforma ($IDEA) |
| SubToken | Representar un proyecto específico |
| IdeafyFactory | Crear y rastrear SubTokens |
| OfferingContract | Manejar la venta primaria de tokens |
| DividendDistributor | Acumular y pagar dividendos |
| IdeaSwap | Intercambiar $IDEA ↔ USDC |
| IdeaMarketplace | Facilitar la compra/venta secundaria |
| IdeaGovernance | Manejar la votación de holders |

**¿Por qué?** Porque si en el futuro queremos cambiar cómo se pagan dividendos, solo tocamos
DividendDistributor. No tocamos la Factory, no tocamos el Offering, no tocamos los 10.000
SubTokens ya deployados.

#### 2. Inmutabilidad Predecible

El supply de los tokens debe ser **predecible y no inflable**.

- `IdeaToken`: todo el supply se mintea en el constructor. No hay `mint()`.
- `SubToken`: todo el supply se mintea al factory en el constructor. No hay `mint()`.
- Caps `MAX_SUPPLY` hardcodeados como `constant`.

#### 3. Gas Eficiente

Cada operación en blockchain cuesta gas. Optimizamos para que las operaciones más frecuentes
sean baratas.

- **Mappings** en vez de arrays (O(1) vs O(n))
- **Accumulator pattern** para dividendos (O(1) por claim, no O(n) por distribución)
- **Claim pattern** en vez de push (cada usuario paga su propio gas)

#### 4. Composabilidad

Los contratos se conectan entre sí usando **direcciones** y **roles**, no heredando unos de otros.

```
IdeaToken ── usado por ──→ Offering, Distributor, Swap, Marketplace, Governance
Factory   ── usado por ──→ Offering (allocateTokens), Distributor (subTokenOfProject)
SubToken  ── usado por ──→ Factory, Marketplace, Distributor
```

**¿Por qué no herencia?** Porque si OfferingContract heredara de IdeafyFactory, estariamos
acoplando sus implementaciones. Si cambiamos Factory, Offering se rompe. Con composabilidad,
Offering solo necesita la **dirección** de Factory, no su código.

#### 5. Role-Based Security

Usamos `AccessControl` de OpenZeppelin en vez de `Ownable`.

| Rol | Qué permite |
|---|---|
| `DEFAULT_ADMIN_ROLE` | Asignar y revocar cualquier rol |
| `ADMIN_ROLE` | Registrar offerings, finalizar rondas, cambiar fees |
| `CREATOR_ROLE` | Llamar `launchProject()` en la Factory |
| `ALLOCATOR_ROLE` | Llamar `allocateTokens()` en la Factory (lo tiene OfferingContract) |
| `LIQUIDITY_PROVIDER_ROLE` | Agregar/remover liquidez del pool |
| `EXECUTOR_ROLE` | Ejecutar propuestas aprobadas en Governance |

**¿Por qué es mejor que Ownable?** Ownable solo tiene un dueño. Si el dueño hace algo malo
o lo hackean, no hay defensa. Con roles, podemos tener múltiples admins, revocar roles
individualmente, y dar permisos específicos (ej: un creador solo puede lanzar proyectos,
no cambiar fees).

---

## 5. Lección 4 — IdeaToken.sol: la moneda de plataforma $IDEA

### ¿Qué problema resuelve?

Sin una moneda base no puede haber intercambio. Toda la economía de IDEAFY gira alrededor de
$IDEA: para invertir en proyectos necesitás $IDEA, los dividendos se pagan en $IDEA, los fees
se cobran en $IDEA, y la votación se hace con $IDEA.

### ¿Cómo lo pensamos?

Nos preguntamos: **¿qué propiedades debería tener $IDEA para que la economía funcione?**

1. **Supply fijo y conocido** → para que inversores y creadores sepan exactamente cuánto hay
2. **Deflacionario** → para que holdear tenga sentido económico
3. **Sin riesgo de inflación** → que NADIE pueda crear más tokens después del deploy
4. **Seguro** → que no tenga bugs conocidos

Las alternativas que consideramos:

| Alternativa | Problema | Decisión |
|---|---|---|
| Usar un ERC-20 básico sin cap | El supply puede crecer infinitamente, el token no tiene valor | ❌ Descartado |
| Usar un ERC-20 con mint() soloOwner | Si el owner es hackeado, pueden mintear millones | ❌ Descartado |
| Usar un ERC-20 Burnable con cap y sin mint() | Es exactamente lo que necesitamos | ✅ Elegido |
| Usar un token ya existente (USDC, DAI) | No controlamos el supply, no podemos cobrar fees en nuestra moneda | ❌ Descartado |

### El código explicado

```solidity
// SPDX-License-Identifier: MIT
// Licencia estándar para código open source. MIT es la más permisiva.
pragma solidity ^0.8.20;

// Importamos ERC20Burnable (ERC20 con funciones de quemar) y AccessControl (roles)
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

contract IdeaToken is ERC20Burnable, AccessControl {

    // Definimos un rol para el treasury. keccak256 convierte el string en un
    // bytes32 único que identifica el rol en el sistema de AccessControl.
    bytes32 public constant TREASURY_ROLE = keccak256("TREASURY_ROLE");

    // MAX_SUPPLY es una constante. 100_000_000 * 10^18 (18 decimales como ETH).
    // Los guiones bajos son solo para legibilidad en Solidity.
    // 100M es el número mágico: ni tan poco que se acabe rápido, ni tanto que
    // no tenga valor. Es el mismo orden de magnitud que tokens como UNI o AAVE.
    uint256 public constant MAX_SUPPLY = 100_000_000e18;

    // BURN_BPS = 100 basis points = 1%. "BPS" es "basis points" = 1/100 de 1%.
    // En finanzas, todo se mide en bps porque evita errores con decimales.
    uint256 public constant BURN_BPS = 100;

    // El constructor se ejecuta UNA SOLA VEZ cuando deployamos el contrato.
    // No se puede volver a ejecutar.
    constructor() ERC20("IDEAFY Platform", "IDEA") {
        // Le damos al deployer (msg.sender) el rol de admin general
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        // Y también el rol de treasury (puede recibir fees, etc.)
        _grantRole(TREASURY_ROLE, msg.sender);
        // Minteamos TODO el supply al deployer. Después de esto, nunca más
        // se puede crear un token $IDEA. No hay función mint() en ninguna parte.
        _mint(msg.sender, MAX_SUPPLY);
    }

    // _update() es la función que Solidity llama internamente SIEMPRE que
    // se hace una transferencia (transfer, transferFrom, mint, burn).
    // Overrideamos para agregar el burn de 1%.
    function _update(address from, address to, uint256 amount) internal override {
        // from == address(0) significa que es un mint (creación de tokens)
        // to == address(0) significa que es un burn (destrucción de tokens)
        // En ambos casos, NO queremos quemar (sería un loop infinito).
        if (from != address(0) && to != address(0)) {
            // Calculamos cuánto quemar: amount * 100 / 10000 = 1%
            uint256 burn = (amount * BURN_BPS) / 10000;
            if (burn > 0) {
                // Primero: quemamos el 1%. to = address(0) significa "quemar".
                super._update(from, address(0), burn);
                // Después: transferimos el 99% restante al destinatario.
                super._update(from, to, amount - burn);
            } else {
                // Si amount es muy chico como para que burn > 0, transferimos todo.
                super._update(from, to, amount);
            }
        } else {
            // Si es mint o burn, hacemos la operación normal sin quemar.
            super._update(from, to, amount);
        }
    }
}
```

### Conceptos importantes acá

**¿Por qué `_update()` y no `_transfer()`?**
OpenZeppelin v5 cambió `_transfer()` por `_update()` porque también cubre mint y burn.
Antes tenías que overridear `_transfer()`, `_mint()`, y `_burn()` por separado. Ahora
con `_update()` tenés todo en un solo lugar.

**¿Por qué 100M y no 1M o 1B?**
- 1M sería muy poco para un ecosistema con 10.000 proyectos
- 1B sería demasiado para un PoC (el precio por token sería muy bajo)
- 100M es un número redondo, comparable a otros tokens de plataforma

**¿Por qué 1% y no 0.5% o 2%?**
- 0.5% es muy poco, el burn no se siente
- 2% es mucho, desincentiva transacciones
- 1% es el punto medio: suficiente para ser deflacionario, no tanto como para que duela transferir

**¿Qué pasa si alguien transfiere 0.000000000000000001 $IDEA?**
El burn sería 0 (porque 1% de eso redondea a 0). No se quema nada. Es un edge case que
no afecta la economía.

### ¿Por qué es el primer contrato que creamos?

Porque **sin $IDEA no hay ecosistema**. Todos los demás contratos necesitan $IDEA:
- OfferingContract lo recibe de inversores
- DividendDistributor lo distribuye
- IdeaSwap lo intercambia
- IdeaMarketplace lo cobra como fee
- IdeaGovernance lo usa para votar

Si hiciéramos primero el Marketplace sin tener $IDEA, no tendríamos con qué pagar las compras.

---

## 6. Lección 5 — SubToken.sol: el token por proyecto

### ¿Qué problema resuelve?

Necesitamos una forma de representar la **participación en un proyecto específico**.
Si invertís en la cervecería, recibís tokens de la cervecería ($IDEA-BREW), no de la plataforma
($IDEA). Cada proyecto tiene su propio token.

### ¿Cómo lo pensamos?

La gran pregunta fue: **¿el sub-token debería ser como $IDEA (con quema, con cap, etc.)
o debería ser diferente?**

| Opción | Pros | Contras | Decisión |
|---|---|---|---|
| Mismo diseño que $IDEA (con quema) | Consistencia | Si cada transfer quema, nadie transfiere. El marketplace no funciona | ❌ |
| Sin quema, con mint() | Flexible | El creador puede inflar el supply | ❌ |
| **Sin quema, sin mint(), supply fijo** | El inversor sabe exactamente cuántos tokens hay. El creador no puede hacer trampa | No se puede emitir más si se necesita | ✅ |

Decidimos que el sub-token sea un **bono de participación**, no una moneda. Su valor no viene
de ser intercambiable, sino de **dar derecho a dividendos**.

### Diferencias con el ProjectToken anterior

| ProjectToken (antes) | SubToken (ahora) | ¿Por qué? |
|---|---|---|
| `mint()` público onlyOwner | No hay mint() | El creador no puede inflar el supply después del launch |
| `burnFrom()` público onlyOwner | `burnFrom()` soloFactory | Solo la factory puede quemar (cuando hay refunds) |
| Quema 0.1% en transfers | **Sin quema** | Si el sub-token quemara, nadie lo transferiría. La quema es de $IDEA |
| Sin metadatos | Guarda proyectoId, rubroId, dividendBps, creator | Cualquier contrato puede consultar de qué proyecto es este token sin necesidad de base de datos |
| Ownable | Sin Ownable | La propiedad no tiene sentido si no hay mint() |

### El código — Evolución: de immutable+constructor a UUPS+initialize

**Versión 1 (original — sin proxy, creado con `new SubToken()`):**
```solidity
contract SubToken is ERC20 {
    uint256 public immutable proyectoId;
    uint256 public immutable rubroId;
    uint256 public immutable dividendBps;
    address public immutable creator;
    address public immutable factory;

    constructor(
        uint256 _proyectoId, uint256 _rubroId, uint256 _dividendBps,
        address _creator, address _factory,
        string memory nombre, string memory simbolo, uint256 supplyInicial
    ) ERC20(nombre, simbolo) {
        proyectoId = _proyectoId;
        rubroId = _rubroId;
        dividendBps = _dividendBps;
        creator = _creator;
        factory = _factory;
        _mint(_factory, supplyInicial);
    }
    // ...
}
```

**Versión 2 (con UUPS proxy — upgradeable):**
```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/token/ERC20/ERC20Upgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/access/OwnableUpgradeable.sol";

contract SubToken is ERC20Upgradeable, UUPSUpgradeable, OwnableUpgradeable {

    // proyectoId: ID del proyecto en nuestra base de datos Java.
    // Usamos storage (no immutable) porque los proxies no soportan inmutables.
    uint256 public proyectoId;

    // rubroId: 1=Gastro, 2=Tech, 3=Agro, 4=Inmo.
    uint256 public rubroId;

    // dividendBps: % de ganancia que va a inversores, en basis points.
    uint256 public dividendBps;

    // creator: wallet del creador del proyecto.
    address public creator;

    // factory: la dirección de IdeafyFactory que deployó este token.
    address public factory;

    // constructor() vacío — en contratos upgradeables el constructor no puede
    // tener lógica porque el proxy delega a la implementación. En su lugar,
    // usamos initialize().
    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    // initialize(): reemplaza al constructor. Se llama UNA SOLA VEZ después
    // de deployar el proxy. Recibe los mismos parámetros que antes tenía el
    // constructor.
    function initialize(
        uint256 _proyectoId,
        uint256 _rubroId,
        uint256 _dividendBps,
        address _creator,
        address _factory,
        string memory nombre,
        string memory simbolo,
        uint256 supplyInicial
    ) external initializer {
        // Estos tres initializer() son obligatorios para contratos upgradeables:
        __ERC20_init(nombre, simbolo);       // Inicializa ERC20
        __UUPSUpgradeable_init();            // Inicializa UUPS
        __Ownable_init(msg.sender);          // El owner es la factory (msg.sender al deployar)

        proyectoId = _proyectoId;
        rubroId = _rubroId;
        dividendBps = _dividendBps;
        creator = _creator;
        factory = _factory;

        _mint(_factory, supplyInicial);
    }

    // _authorizeUpgrade(): SOLO el owner (la factory) puede hacer upgrade.
    // Esta función la llama UUPSUpgradeable.upgradeTo() internamente.
    function _authorizeUpgrade(address newImplementation) internal override onlyOwner {}

    // burnFrom(): solo la factory puede quemar tokens (para refunds).
    function burnFrom(address desde, uint256 cantidad) external {
        require(msg.sender == factory, "SubToken: only factory");
        _burn(desde, cantidad);
    }
}
```

### ¿Por qué UUPS y no proxy tradicional?

| Aspecto | Proxy tradicional (Transparent) | UUPS |
|---|---|---|
| Dónde está el upgrade | En el proxy | **En la implementación** |
| Gas por llamada | Más (el proxy paga el chequeo) | Menos (el chequeo está en la impl) |
| Complejidad | Menos | Más (hay que cuidar `_authorizeUpgrade`) |
| Riesgo | Perder el upgrade si el proxy tiene bug | **Perder el upgrade si la impl tiene bug** (no podemos actualizar una impl rota) |

Elegimos UUPS porque:
1. Es el estándar recomendado por OpenZeppelin (`@openzeppelin/contracts-upgradeable`)
2. Es más barato en gas (cada llamada al proxy ahorra el chequeo de admin)
3. La ideafyFactory tiene `setSubTokenImplementation()` para cambiar la impl globalmente,
   y cada proxy individual puede hacer `upgradeTo()` si es necesario

### Cambio clave: `immutable` → `storage`

En la versión anterior (sin proxy), usábamos `uint256 public immutable proyectoId` porque
era más barato y seguro. **Con proxy no podemos usar `immutable`** porque los proxies
delegan llamadas y el storage del proxy es el que importa — las variables `immutable` se
leen del bytecode del contrato implementación, no del proxy.

### ¿Por qué el sub-token NO tiene quema?

**¿Por qué $IDEA quema 1% y el sub-token no?**

La respuesta corta: porque son dos cosas distintas.

- **$IDEA** es una **moneda** → se usa para transacciones, fees, governance
- **SubToken** es un **bono de participación** → da derecho a dividendos

Si el sub-token quemara 1% en cada transfer:
1. Si tenés 100 tokens de la cervecería y los transferís a tu otra wallet, perdés 1 token
2. No los vas a querer transferir → el marketplace no tiene liquidez
3. Nadie compra un token que se deshace solo

**Analogía:** Es como una acción de una empresa. Si cada vez que vendés una acción
la empresa te saca el 1%, nadie vendería acciones. Las acciones solo dan dividendos,
no se desgastan con el uso.

### Metadatos on-chain vs off-chain

Podríamos guardar el rubro y el dividend% en la base de datos (como hacíamos antes).
¿Por qué los guardamos on-chain?

| Guardar en DB (off-chain) | Guardar en contrato (on-chain) |
|---|---|
| Cualquiera puede modificar la DB | Es inmutable, nadie puede cambiar los datos |
| No es visible para otros contratos | Otros contratos (DividendDistributor, Marketplace) pueden leerlo |
| Depende de nuestro backend | Funciona aunque nuestro backend esté caído |

**¿Qué datos van on-chain y cuáles en DB?**

| Dato | Dónde | Por qué |
|---|---|---|
| proyectoId | On-chain (SubToken) | Identifica al token |
| rubroId | On-chain (SubToken) | Lo necesita DividendDistributor para calcular % |
| dividendBps | On-chain (SubToken) | Lo necesita DividendDistributor |
| creator wallet | On-chain (SubToken) | Para enviarle los fondos recaudados |
| Nombre del proyecto | DB | Es largo, cambia, no lo necesita otro contrato |
| Descripción del proyecto | DB | Solo lo ve el frontend |
| Imágenes del proyecto | DB | Solo lo ve el frontend |
| KYC del creador | DB | Es privado |

---

## 7. Lección 6 — IdeafyFactory.sol: la fábrica que crea SubTokens

### ¿Qué problema resuelve?

Necesitamos un **punto central** que:
1. Cree SubTokens cuando se lance un proyecto nuevo
2. Los registre para poder consultarlos después
3. Controle quién puede crear proyectos (solo creadores aprobados)
4. Distribuya tokens a inversores cuando el OfferingContract lo pida

### ¿Cómo lo pensamos?

La alternativa era que cada proyecto deployara su propio SubToken manualmente.

| Opción | Problema | Decisión |
|---|---|---|
| Cada creador deploya su propio token | El creador necesita saber Solidity, tener ETH para deployar, y no habría registro centralizado | ❌ |
| Un contrato que almacena todos los tokens y permite crear nuevos | La factory deploya, todos los tokens son iguales, hay registro central | ✅ |

**¿Por qué la factory puede deployar contratos?**

En Solidity, cualquier contrato puede deployar otro contrato con `new NombreContrato()`.
Esto se llama **factory pattern**. Es usado por:
- Uniswap V3 (crea pools nuevos con `createPool()`)
- OpenZeppelin (crea contratos con `Clones` o `ERC1967Proxy`)
- La mayoría de los protocolos DeFi

**Novedad: en lugar de `new SubToken()`, usamos `ERC1967Proxy`**. Esto hace que cada
SubToken sea **upgradeable** (ver sección de Proxy arriba).

### El código — Evolución: de `new SubToken()` a `ERC1967Proxy`

**Versión 1 (original — deployaba SubToken directamente, sin upgradeabilidad):**
```solidity
function launchProject(...) external onlyRole(CREATOR_ROLE) returns (address) {
    // ...
    SubToken nuevo = new SubToken(
        proyectoId, rubroId, dividendBps, creator,
        address(this), nombre, simbolo, supplyInicial
    );
    address tokenAddr = address(nuevo);
    subTokenOfProject[proyectoId] = tokenAddr;
    // ...
}
```

**¿Qué problema tenía?**
- Si después de deployar 10.000 SubTokens encontrábamos un bug, no había forma de
  actualizarlos
- `getSubTokenAt(index)` obligaba al frontend a hacer N RPC calls para obtener la lista

**Versión 2 (con proxy upgradeable + paginación):**
```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./SubToken.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";

contract IdeafyFactory is AccessControl {

    bytes32 public constant CREATOR_ROLE = keccak256("CREATOR_ROLE");
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant ALLOCATOR_ROLE = keccak256("ALLOCATOR_ROLE");

    mapping(uint256 => address) public subTokenOfProject;
    mapping(address => uint256) public projectOfSubToken;

    // subTokenImplementation: dirección del contrato de implementación de SubToken.
    // Todos los proxies apuntan a esta implementación. Si queremos actualizar,
    // deployamos una nueva y llamamos setSubTokenImplementation().
    address public subTokenImplementation;

    // Lista paginada de todos los SubTokens creados
    address[] public allSubTokens;

    event ProjectLaunched(
        uint256 indexed proyectoId,
        address indexed subToken,
        address indexed creator,
        uint256 rubroId,
        uint256 dividendBps,
        uint256 supply
    );

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    // launchProject() ahora deploya un ERC1967Proxy en lugar de un SubToken directo.
    // Esto permite que el SubToken sea upgradeable.
    function launchProject(
        uint256 proyectoId,
        uint256 rubroId,
        uint256 dividendBps,
        address creator,
        string calldata nombre,
        string calldata simbolo,
        uint256 supplyInicial
    ) external onlyRole(CREATOR_ROLE) returns (address) {

        require(subTokenOfProject[proyectoId] == address(0),
            "IdeafyFactory: project already has a token");
        require(creator != address(0), "IdeafyFactory: invalid creator");
        require(supplyInicial > 0, "IdeafyFactory: supply must be > 0");

        // 1. Deployamos un proxy ERC1967 que apunta a subTokenImplementation
        // 2. El proxy se inicializa con los datos de initialize()
        bytes memory initData = abi.encodeWithSelector(
            SubToken.initialize.selector,
            proyectoId, rubroId, dividendBps, creator,
            address(this), nombre, simbolo, supplyInicial
        );
        ERC1967Proxy proxy = new ERC1967Proxy(subTokenImplementation, initData);

        address tokenAddr = address(proxy);

        subTokenOfProject[proyectoId] = tokenAddr;
        projectOfSubToken[tokenAddr] = proyectoId;
        allSubTokens.push(tokenAddr);

        emit ProjectLaunched(proyectoId, tokenAddr, creator, rubroId, dividendBps, supplyInicial);
        return tokenAddr;
    }

    // setSubTokenImplementation(): el admin cambia la implementación a la que
    // apuntan los proxies NUEVOS. Los proxies ya existentes no se ven afectados
    // (cada uno puede hacer upgradeTo() individualmente si es necesario).
    function setSubTokenImplementation(address nuevaImpl) external onlyRole(ADMIN_ROLE) {
        require(nuevaImpl != address(0), "IdeafyFactory: invalid implementation");
        subTokenImplementation = nuevaImpl;
    }

    // Versión paginada de getSubTokens. En lugar de devolver un array enorme,
    // devuelve un slice (offset+limit) + el total. Esto permite al frontend
    // mostrar una página a la vez.
    function getSubTokensPaginated(uint256 offset, uint256 limit)
        external view returns (address[] memory tokens, uint256 total)
    {
        total = allSubTokens.length;
        if (offset >= total) {
            return (new address[](0), total);
        }
        uint256 end = offset + limit;
        if (end > total) end = total;
        uint256 count = end - offset;
        tokens = new address[](count);
        for (uint256 i = 0; i < count; i++) {
            tokens[i] = allSubTokens[offset + i];
        }
    }

    function allocateTokens(
        uint256 proyectoId,
        address to,
        uint256 amount
    ) external onlyRole(ALLOCATOR_ROLE) {
        require(to != address(0), "IdeafyFactory: invalid recipient");
        require(amount > 0, "IdeafyFactory: amount must be > 0");
        SubToken sub = SubToken(subTokenOfProject[proyectoId]);
        require(address(sub) != address(0), "IdeafyFactory: project not found");
        sub.transfer(to, amount);
    }
}
```

### Conceptos importantes

**¿Por qué la factory recibe todo el supply?**

Cuando `new ERC1967Proxy(impl, initData)` se ejecuta, el proxy llama a `initialize()`
del SubToken, que hace:
```solidity
_mint(_factory, supplyInicial);
```

O sea, **todo el supply se lo da a la factory**. ¿Por qué no al creador o al treasury?

- Si se lo diéramos al **creador**, podría vender todo antes de que los inversores tengan chance
- Si se lo diéramos al **treasury**, el creador no recibiría nada
- Si se lo damos a la **factory**, ella controla cuándo y a quién se los da (a través de OfferingContract)

**¿Qué pasa si necesito actualizar el SubToken después de deployar 10.000 proyectos?**

Con el patrón anterior (`new SubToken()`), no había forma de actualizar 10.000 contratos.
Con el nuevo patrón (proxy):
1. Deployás una nueva implementación de SubToken (p.ej., con un bugfix)
2. Llamás `upgradeTo(nuevaImplementacion)` en cada proxy (o usás un script)
3. Todos los SubTokens ahora ejecutan el nuevo código, pero conservan su estado (balances, etc.)

**¿Por qué roles y no solo un admin?**

Porque necesitamos granularidad:

| Persona | Rol | Puede hacer |
|---|---|---|
| Admin del sistema | ADMIN_ROLE | Registrar offerings, finalizar rondas, cambiar implementación |
| Creador de proyecto | CREATOR_ROLE | Llamar launchProject() |
| OfferingContract | ALLOCATOR_ROLE | Llamar allocateTokens() |
| Cualquier otro | - | Solo consultar |

**¿Por qué `getSubTokensPaginated()` en lugar de `getSubTokenAt()`?**

La versión anterior tenía `getSubTokenAt(index)` y `getSubTokenCount()`. El frontend
tenía que llamar a count(), luego loopear con índices. Con 10.000 tokens, eso son
10.000 RPC calls. La versión paginada devuelve un slice en una sola llamada.

---

## 8. Lección 7 — OfferingContract.sol: la venta primaria con protección

### ¿Qué problema resuelve?

El viejo `InvestmentSwap.invest()` tenía **cero protecciones** para los inversores:
- Invertías cuando querías
- Cuanto querías
- Recibías los tokens al instante
- Si el proyecto no funcionaba, solo el owner podía hacer refund

El OfferingContract resuelve todo esto con **soft cap**, **hard cap**, **ventana de tiempo**
y **refund automático**.

### ¿Cómo lo pensamos?

Analizamos cómo funcionan las rondas de inversión en el mundo real:

| Plataforma | Tiene soft cap? | Tiene hard cap? | Tiene tiempo límite? |
|---|---|---|---|
| Kickstarter | ✅ (objetivo de financiamiento) | ❌ (podés recaudar más) | ✅ (30-60 días) |
| Juicebox (web3) | ✅ | ✅ | ✅ |
| DAO Maker | ✅ | ✅ | ✅ |
| **InvestmentSwap (antes)** | ❌ | ❌ | ❌ |

Nos faltaban las protecciones básicas que hasta Kickstarter tiene.

**Alternativas consideradas:**

| Opción | Problema | Decisión |
|---|---|---|
| Un Offering por contrato (cada proyecto deploya su propia offering) | Caro de deployar, difícil de rastrear | ❌ |
| **Un solo OfferingContract que registra todas las offerings** | Más barato, más fácil de administrar, un solo punto de integración | ✅ |

### El flujo completo explicado en detalle

```
LÍNEA DE TIEMPO DE UN OFFERING

  1. REGISTER  ──────────────→  2. INVEST  ──────────────→  3. FINALIZE
                                                              │
                                        ┌─────────────────────┼─────────────────────┐
                                        ▼                     ▼                     ▼
                                  4a. SUCCESS            4b. FAIL              (nunca finaliza)
                                  tokens al inv.         refund al inv.       creador no reclama
                                  $IDEA al creador       tokens quemados      admin puede reclaim
```

**Paso 1: registerOffering()**

El admin (con ADMIN_ROLE) registra una nueva offering para un proyecto que ya tiene SubToken.

**Paso 2: invest()**

Los inversores envían $IDEA al contrato. El contrato los retiene (no van al creador todavía).
Se registra cuánto invirtió cada uno.

**Paso 3: finalize()**

Después de `endTime`, el admin llama `finalize()`. El contrato verifica:
- Si `totalInvested >= softCap` → **success = true**. Los $IDEA se envían al creador.
- Si `totalInvested < softCap` → **success = false**. Nadie recibe nada todavía.

**Paso 4a: claimTokens() (si success)**

Cada inversor llama `claimTokens()` y recibe sus SubTokens (vía `factory.allocateTokens()`).

**Paso 4b: refund() (si fail)**

Cada inversor llama `refund()` y recupera sus $IDEA.

### El código explicado (partes clave)

```solidity
// Estructura que define una offering.
// En Solidity, un struct es como una clase Java con solo atributos (sin métodos).
struct Offering {
    uint256 proyectoId;
    address creator;          // Quién recibe los fondos si la ronda es exitosa
    uint256 softCap;          // Mínimo en $IDEA
    uint256 hardCap;          // Máximo en $IDEA
    uint256 pricePerToken;    // $IDEA por 1 SubToken (en wei, con 18 decimales)
    uint256 startTime;        // Cuándo empieza la venta (timestamp Unix)
    uint256 endTime;          // Cuándo termina (timestamp Unix)
    bool finalized;            // Ya se cerró la ronda?
    bool success;              // Fue exitosa? (totalInvested >= softCap)
    uint256 totalInvested;     // Cuánto se recaudó en total
}
```

**¿Por qué usamos un struct en vez de parámetros separados?**

Porque todos los datos de una offering están relacionados. Tenerlos en un struct hace que
sea más fácil pasarlos entre funciones y entender el código. Es como tener una clase `Offering`
en Java con sus campos.

**invest():**

```solidity
function invest(uint256 proyectoId, uint256 ideaAmount) external nonReentrant {
    // Buscamos la offering en el mapping
    Offering storage off = offerings[proyectoId];

    // Validaciones: existe? no está finalizada? está en el período?
    require(off.proyectoId != 0, "Offering: not registered");
    require(!off.finalized, "Offering: already finalized");
    require(block.timestamp >= off.startTime, "Offering: not started");
    require(block.timestamp <= off.endTime, "Offering: ended");
    require(ideaAmount > 0, "Offering: amount > 0");

    // No podemos superar el hard cap
    require(off.totalInvested + ideaAmount <= off.hardCap,
        "Offering: exceeds hard cap");

    // Transferimos $IDEA del inversor a ESTE contrato (OfferingContract)
    // El inversor tiene que haber llamado approve() antes en IdeaToken
    require(idea.transferFrom(msg.sender, address(this), ideaAmount),
        "Offering: IDEA transfer failed");

    // Calculamos cuántos SubTokens le corresponden
    // pricePerToken = 5 $IDEA → 1 SubToken
    // Si invierte 100 $IDEA → 100 * 1e18 / 5e18 = 20 SubTokens
    uint256 tokenAmount = (ideaAmount * 1e18) / off.pricePerToken;

    // Registramos la contribución del inversor
    if (contributions[proyectoId][msg.sender] == 0) {
        investors[proyectoId].push(msg.sender);  // Primera vez que invierte
    }
    contributions[proyectoId][msg.sender] += ideaAmount;
    off.totalInvested += ideaAmount;

    emit InvestmentMade(proyectoId, msg.sender, ideaAmount, tokenAmount);
}
```

**Cálculo de `tokenAmount`:**

```solidity
tokenAmount = (ideaAmount * 1e18) / off.pricePerToken;
```

- `ideaAmount` está en wei (18 decimales). Si invierte 100 $IDEA = 100 * 10^18 wei.
- `pricePerToken` está en wei. Si 1 SubToken cuesta 5 $IDEA = 5 * 10^18 wei.
- `tokenAmount = (100e18 * 1e18) / 5e18 = 20e18` = 20 SubTokens.

El `* 1e18` extra es para mantener la precisión de 18 decimales.

**finalize():**

```solidity
function finalize(uint256 proyectoId) external onlyRole(ADMIN_ROLE) {
    Offering storage off = offerings[proyectoId];
    require(off.proyectoId != 0, "Offering: not registered");
    require(!off.finalized, "Offering: already finalized");
    require(block.timestamp > off.endTime, "Offering: still active");

    off.finalized = true;

    // Si se alcanzó el soft cap → éxito
    // Los $IDEA van al creador del proyecto
    if (off.totalInvested >= off.softCap) {
        off.success = true;
        require(idea.transfer(off.creator, off.totalInvested),
            "Offering: creator transfer failed");
    }
    // Si no → success queda en false
    // Los inversores pueden hacer refund individualmente

    emit OfferingFinalized(proyectoId, off.success);
}
```

**¿Por qué el creador recibe los $IDEA recién en finalize()?**

Porque durante la venta, los $IDEA están retenidos en el OfferingContract. Si la ronda falla,
los inversores recuperan su plata. Si la ronda es exitosa, recién ahí el creador recibe los fondos.

Esto protege al inversor: **nunca le estás dando plata al creador hasta que se cumpla la condición**
de que haya suficiente capital.

**claimTokens():**

```solidity
function claimTokens(uint256 proyectoId) external nonReentrant {
    Offering storage off = offerings[proyectoId];
    require(off.finalized, "Offering: not finalized");
    require(off.success, "Offering: not successful, use refund()");

    uint256 contributed = contributions[proyectoId][msg.sender];
    require(contributed > 0, "Offering: nothing to claim");

    // Marcamos como reclamado (para que no pueda reclamar dos veces)
    contributions[proyectoId][msg.sender] = 0;

    // Calculamos cuántos SubTokens le tocan
    uint256 tokenAmount = (contributed * 1e18) / off.pricePerToken;

    // La factory transfiere los SubTokens al inversor
    factory.allocateTokens(proyectoId, msg.sender, tokenAmount);

    emit TokensClaimed(proyectoId, msg.sender, tokenAmount);
}
```

**refund():**

```solidity
function refund(uint256 proyectoId) external nonReentrant {
    Offering storage off = offerings[proyectoId];
    require(off.finalized, "Offering: not finalized");
    require(!off.success, "Offering: was successful, use claimTokens");

    uint256 contributed = contributions[proyectoId][msg.sender];
    require(contributed > 0, "Offering: nothing to refund");

    // Marcamos como reclamado
    contributions[proyectoId][msg.sender] = 0;

    // Devolvemos los $IDEA al inversor
    require(idea.transfer(msg.sender, contributed),
        "Offering: refund transfer failed");

    emit RefundMade(proyectoId, msg.sender, contributed);
}
```

**Diferencia clave con el InvestmentSwap anterior:** Acá **el inversor mismo** puede llamar
`refund()`, no necesita que el admin lo haga. Si el proyecto no junta suficiente capital,
cada inversor recupera su plata cuando quiera.

---

## 9. Lección 8 — DividendDistributor.sol: dividendos en $IDEA (el más importante)

### ¿Qué problema resuelve?

**Este es el cambio más importante de todo el rediseño.** Cambia el flujo económico completo
de la plataforma.

**Antes (modelo roto):**
```
Creador → reporta ganancia
       → Backend Java calcula dividendos
       → Backend envía USDT a inversores  ← ¡Fuga del ecosistema!
       → Inversor recibe USDT y se va
```

**Ahora (modelo correcto):**
```
Creador → reporta ganancia
       → Convierte USDC a $IDEA en el IdeaSwap
       → DividendDistributor acumula $IDEA
       → Holder reclama sus $IDEA con claim()
       → Holder puede: votar, stakear, comprar más tokens, o vender $IDEA
       → Si vende $IDEA, lo hace en el mismo pool → el ecosistema retiene valor
```

**¿Por qué es tan importante?**

Con USDT, el dinero sale del ecosistema y no vuelve. El inversor cobra y se va a gastar
su USDT a otro lado. El token $IDEA no se beneficia de los dividendos.

Con $IDEA, el inversor tiene incentivos para **holdear**: puede votar, puede tener acceso
prioritario a nuevas rondas, puede stakear (en el futuro). Y si vende, lo hace en el pool
de $IDEA/USDC, lo que significa que alguien más compra sus $IDEA. El valor circula dentro
del ecosistema.

### El Accumulator Pattern (la clave técnica)

El mayor problema técnico de distribuir dividendos es el **gas**.

**Sin accumulator (la forma ingenua):**

```solidity
// MAL: esto itera sobre todos los holders
function distributeBad(uint256 proyectoId, uint256 totalAmount) external {
    for (uint256 i = 0; i < holders.length; i++) {
        address holder = holders[i];
        uint256 amount = totalAmount * balanceOf(holder) / totalSupply;
        idea.transfer(holder, amount);
    }
}
```

Si hay 10.000 holders, este loop hace **10.000 transferencias**. Cada transferencia cuesta
~30k gas. Total: 300M de gas. Aún en Base (0.01 gwei), eso es USD 30 por **una** distribución.
Si hacés distribuciones mensuales para 100 proyectos, son USD 3.000 por mes. **No escala.**

**Con accumulator (la forma correcta):**

```solidity
// BIEN: esto no itera. Solo actualiza un número.
function distribute(uint256 proyectoId, uint256 totalDividend) external {
    // Paso 1: recibir los $IDEA
    idea.transferFrom(msg.sender, address(this), totalDividend);

    // Paso 2: actualizar el acumulador
    // dividendPerToken = cuanto $IDEA le toca a CADA token
    // Si repartimos 1000 $IDEA y hay 10000 SubTokens:
    //   dividendPerToken = 1000e18 * 1e18 / 10000e18 = 0.1e18 = 0.1 $IDEA por token
    dividendPerToken[proyectoId] += (totalDividend * 1e18) / totalSupply;
}
```

Esto cuesta **1 sola transferencia** + **1 operación aritmética**. El gas es fijo, no importa
si hay 10 holders o 10 millones.

Después, **cada holder reclama su parte**:

```solidity
function claim(uint256 proyectoId) external {
    // El holder paga su propio gas para reclamar

    // Cuántos SubTokens tiene este usuario?
    uint256 balance = IERC20(subTokenAddr).balanceOf(msg.sender);

    // Cuánto $IDEA por token se ha acumulado hasta ahora?
    uint256 perToken = dividendPerToken[proyectoId];

    // Cuánto $IDEA por token ya le pagamos a este usuario?
    uint256 paid = dividendPerTokenPaid[proyectoId][msg.sender];

    // Lo que le debemos = (acumulado - yaPagado) * sus_tokens
    uint256 owed = ((perToken - paid) * balance) / 1e18;

    // Actualizamos su snapshot
    dividendPerTokenPaid[proyectoId][msg.sender] = perToken;

    // Le pagamos
    idea.transfer(msg.sender, owed);
}
```

**Analogía:** Pensalo como un contador de agua en un edificio.

- Sin accumulator: el portero va departamento por departamento a cobrar el agua (O(n)).
- Con accumulator: el portero pone el precio del agua en el pizarrón, y cada vecino
  paga según su consumo (O(1) por vecino).

### El código completo explicado

```solidity
contract DividendDistributor is AccessControl, ReentrancyGuard {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    // El token $IDEA (para pagar dividendos)
    IERC20 public immutable idea;

    // La factory (para consultar subTokenOfProject)
    IdeafyFactory public immutable factory;

    // proyectoId => cuánto $IDEA se ha acumulado por cada SubToken
    // Es la suma de todas las distribuciones / totalSupply
    // Escalado por 1e18 para precisión
    mapping(uint256 => uint256) public dividendPerToken;

    // proyectoId => usuario => último snapshot de dividendPerToken
    mapping(uint256 => mapping(address => uint256)) public dividendPerTokenPaid;

    constructor(address _idea, address _factory) {
        idea = IERC20(_idea);
        factory = IdeafyFactory(_factory);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    // distribute(): la llama el admin cuando hay dividendos para repartir
    function distribute(uint256 proyectoId, uint256 totalDividend)
        external
        onlyRole(ADMIN_ROLE)
        nonReentrant
    {
        require(totalDividend > 0, "DD: amount > 0");

        // Recibimos los $IDEA del admin (o del treasury, o de quien sea)
        require(idea.transferFrom(msg.sender, address(this), totalDividend),
            "DD: IDEA transfer failed");

        // Consultamos la dirección del SubToken
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        require(subTokenAddr != address(0), "DD: project not found");

        // Necesitamos el totalSupply del SubToken para calcular
        uint256 totalSupply = IERC20(subTokenAddr).totalSupply();
        require(totalSupply > 0, "DD: no tokens in circulation");

        // Actualizamos el acumulador.
        // (totalDividend * 1e18) / totalSupply = $IDEA por token
        // Este valor se SUMA al acumulador existente
        dividendPerToken[proyectoId] += (totalDividend * 1e18) / totalSupply;

        emit Distributed(proyectoId, totalDividend, dividendPerToken[proyectoId]);
    }

    // claim(): la llama el holder para recibir sus dividendos
    function claim(uint256 proyectoId) external nonReentrant {
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        require(subTokenAddr != address(0), "DD: project not found");

        // Balance del usuario en el SubToken
        uint256 balance = IERC20(subTokenAddr).balanceOf(msg.sender);
        require(balance > 0, "DD: no tokens");

        uint256 perToken = dividendPerToken[proyectoId];
        uint256 paid = dividendPerTokenPaid[proyectoId][msg.sender];

        // Lo que le debemos
        uint256 owed = ((perToken - paid) * balance) / 1e18;

        if (owed == 0) {
            revert("DD: nothing to claim");
        }

        // Actualizamos el snapshot para que no pueda reclamar dos veces lo mismo
        dividendPerTokenPaid[proyectoId][msg.sender] = perToken;

        // Le pagamos en $IDEA
        require(idea.transfer(msg.sender, owed), "DD: transfer failed");

        emit Claimed(proyectoId, msg.sender, owed);
    }

    // Función de consulta: cuánto puede reclamar un usuario
    function getClaimable(uint256 proyectoId, address user) external view returns (uint256) {
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        if (subTokenAddr == address(0)) return 0;
        uint256 balance = IERC20(subTokenAddr).balanceOf(user);
        if (balance == 0) return 0;
        uint256 perToken = dividendPerToken[proyectoId];
        uint256 paid = dividendPerTokenPaid[proyectoId][user];
        if (perToken <= paid) return 0;
        return ((perToken - paid) * balance) / 1e18;
    }
}
```

### ¿Qué pasa si un usuario transfiere sus SubTokens después de una distribución?

Gran pregunta. El nuevo holder recibe la cantidad acumulada de dividendPerToken desde
el momento en que recibe los tokens. Pero el snapshot `dividendPerTokenPaid` del nuevo
holder sigue siendo el viejo (de cuando reclamó por última vez, o 0 si nunca reclamó).

Esto significa que si:
1. Alice tiene 100 tokens, `dividendPerToken = 10`, `dividendPerTokenPaid[Alice] = 5`
2. Alice le transfiere 50 tokens a Bob, Bob tiene `dividendPerTokenPaid[Bob] = 0`
3. Bob reclama: recibe `(10 - 0) * 50 / 1e18 = 500` (lo que corresponde a los tokens,
   aunque Alice ya había acumulado parte de ese dividendo con esos tokens)

Hay un **desacople** temporal: el dividendo acumulado se paga al que tiene los tokens
en el momento del claim, no al que los tenía en el momento de la distribución.

**¿Es esto malo?** Depende:
- Para el PoC: no, porque el flujo es simple y los inversores no están haciendo trading
  activo de sub-tokens.
- Para producción: se puede resolver con un hook que actualice `dividendPerTokenPaid`
  en cada transferencia (como hace SushiSwap con `beforeTokenTransfer`). Es más gas
  pero más justo.

---

## 10. Lección 9 — IdeaSwap.sol: el pool de liquidez $IDEA ↔ USDC

### ¿Qué problema resuelve?

El creador gana plata en **moneda fiat** (ARS, USD). Necesita convertir esa plata a $IDEA
para distribuir dividendos. El inversor que recibe $IDEA quizás quiere **salir** del ecosistema
y convertir a USDC.

Sin un swap, no hay forma de convertir entre $IDEA y USDC. El ecosistema sería un **callejón
sin salida**: podés entrar (comprando $IDEA para invertir) pero no podés salir.

### ¿Cómo lo pensamos?

| Opción | Problema | Decisión |
|---|---|---|
| Usar Uniswap V3 directamente | Dependemos de Uniswap, no podemos cobrar fee propio | ❌ |
| Crear nuestro propio AMM (como Uniswap) | Muy complejo para un PoC | ❌ |
| **Pool simple con reservas y precio lineal** | Suficiente para demostrar el concepto, fácil de implementar | ✅ |

Nuestro modelo de pricing es:

```
Precio de $IDEA en USDC = usdcReserve / ideaReserve

Ejemplo:
  Reservas: 100.000 $IDEA + 100.000 USDC
  Precio: 1 $IDEA = 1 USDC

  Alguien compra 1.000 $IDEA con USDC:
    USDC a pagar = 1.000 * (100.000 / 100.000) = 1.000 USDC
    Fee 1% = 10 USDC
    Total: 1.010 USDC
    Nuevas reservas: 101.000 $IDEA + 98.990 USDC
    Nuevo precio: 1 $IDEA = 0.98 USDC (slippage)
```

**¿Por qué no es un AMM "real"?** Un AMM como Uniswap usa la fórmula `x * y = k`
(constant product), que hace que el precio se ajuste suavemente. Nosotros usamos
un modelo más simple que funciona igual para volúmenes chicos.

### El código clave

```solidity
// Swap USDC → $IDEA (comprar $IDEA)
function swapUsdcForExactIdea(uint256 ideaOut) external nonReentrant {
    require(ideaOut > 0, "Swap: output > 0");
    require(ideaReserve >= ideaOut, "Swap: insufficient IDEA reserve");

    // Calculamos cuánto USDC necesita pagar
    // ideaOut * (usdcReserve / ideaReserve) = USDC necesario
    uint256 usdcIn = (ideaOut * usdcReserve) / ideaReserve;

    // Fee: 1% del USDC
    uint256 fee = (usdcIn * feeBps) / 10000;
    uint256 usdcInTotal = usdcIn + fee;

    // Recibimos USDC del usuario
    require(usdc.transferFrom(msg.sender, address(this), usdcInTotal),
        "Swap: USDC transfer failed");

    // Actualizamos reservas
    ideaReserve -= ideaOut;
    usdcReserve += usdcIn;

    // Enviamos $IDEA al usuario
    require(idea.transfer(msg.sender, ideaOut), "Swap: IDEA transfer failed");

    emit Swapped(msg.sender, ideaOut, usdcInTotal, false);
}
```

---

## 11. Lección 10 — IdeaMarketplace.sol: el mercado secundario

### ¿Qué problema resuelve?

Un inversor que compró 20 $IDEA-BREW en la venta primaria quizás después quiere **venderlos**
porque necesita la plata. Sin un mercado secundario, está atrapado.

El marketplace permite que inversores **listen sus tokens a la venta** y otros **los compren**.

### ¿Cómo lo pensamos?

**Versión 1 (original — order book on-chain con arrays):**
| Opción | Decisión |
|---|---|
| AMM para cada sub-token (como Uniswap) | ❌ liquidez fragmentada |
| **Order book simple con listings** | ✅ para PoC |
| Matching off-chain (0x Protocol) | ⏳ para Stage 5 |

**Problema detectado al analizar 100x:** Los arrays `listingsOfSeller[]` y `allListings[]`
crecen sin límite. Si hay 10.000 sellers con 5 listings cada uno, consultar los listings
de un seller requiere leer 50.000 entries de storage → la tx falla por gas limit.

**Versión 2 (con matching off-chain + EIP-712 + paginación):**
| Opción | Decisión |
|---|---|
| AMM para cada sub-token (como Uniswap) | ❌ |
| Order book simple con listings y arrays de consulta | ❌ arrays no escalan |
| **Matching off-chain + EIP-712** → backend matchea, contrato solo settlement | ✅ |

### ¿Por qué descartamos el order book on-chain?

El problema no es el storage en sí (50.000 listings cuestan ~USD 500 en Base, manejable).
El problema es que **los arrays de consulta crecen sin límite**:

- `listingsOfSeller[seller]` devuelve los listings de un vendedor → si tiene 1.000 listings,
  la transacción falla por gas limit
- `allListings[]` idem para el marketplace entero
- No hay forma de paginar arrays dinámicos en Solidity sin copiarlos a memoria

**Solución híbrida:**
1. On-chain: listing simple con `listTokens()` + `buyTokens()` para operaciones manuales
   (sin arrays de consulta — solo mapping por listingId)
2. Off-chain: el backend mantiene el order book de órdenes firmadas (EIP-712).
   Cuando dos órdenes matchean, se llama `executeOrder(firmaVendedor, firmaComprador)`
   que verifica ambas firmas y ejecuta el swap atómicamente

### El flujo on-chain (listings manuales)

```
1. Vendedor: listTokens(subToken, 50, 6)
   → Transfiere 50 $IDEA-BREW al Marketplace
   → Se crea un listing: 50 tokens a 6 $IDEA c/u
   → listingId = ++nextListingId

2. Comprador: buyTokens(listingId, 20)
   → Paga 120 $IDEA + 3 $IDEA (2.5% fee) = 123 $IDEA
   → Recibe 20 $IDEA-BREW
   → Vendedor recibe 117 $IDEA
   → Marketplace retiene 3 $IDEA (fee)
   → Listing actualizado: quedan 30 tokens
```

### El flujo off-chain (alta frecuencia)

```
Backend                            Blockchain
   │                                   │
   ├─ Vendedor firma order:            │
   │  {subToken, cantidad, precio,     │
   │   nonce, deadline, chainId}       │
   │  → firma con EIP-712              │
   │                                   │
   ├─ Backend busca matching:          │
   │  Buy order de otro usuario        │
   │  que matchea precio y cantidad    │
   │                                   │
   ├─ Llama: executeOrder(             │
   │   orderVendedor, firmaV,          │
   │   orderComprador, firmaC          │
   │ ) ──────────────────────────────► │
   │                                   ├─ Verifica firma V (EIP-712)
   │                                   ├─ Verifica firma C (EIP-712)
   │                                   ├─ Verifica nonce (no replay)
   │                                   ├─ Verifica deadline (no expirado)
   │                                   ├─ Transfiere tokens V → C
   │                                   ├─ Transfiere $IDEA C → V
   │                                   ├─ Retiene fee 2.5%
   │                                   └─ Incrementa nonce de ambos
   │                                   │
   │◄─ Event: OrderExecuted(id,tokens) │
```

### El código clave (executeOrder)

```solidity
// EIP-712 Domain Separator
bytes32 public constant DOMAIN_SEPARATOR = keccak256(abi.encode(
    keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"),
    keccak256("IDEAFY Marketplace"),
    keccak256("1"),
    block.chainid,
    address(this)
));

// Estructura de la orden
struct Order {
    address trader;      // quien pone los tokens
    address subToken;    // qué sub-token
    uint256 amount;      // cantidad
    uint256 price;       // precio por token en $IDEA
    uint256 nonce;       // único por trader (anti-replay)
    uint256 deadline;    // timestamp Unix, orden expira después
}

// Tipo EIP-712 para hashear la orden
bytes32 public constant ORDER_TYPEHASH = keccak256(
    "Order(address trader,address subToken,uint256 amount,uint256 price,uint256 nonce,uint256 deadline)"
);

// Nonces: trader → nonce actual. Cada orden consume un nonce.
mapping(address => uint256) public nonces;

function executeOrder(
    Order calldata orderSeller,
    bytes calldata signatureSeller,
    Order calldata orderBuyer,
    bytes calldata signatureBuyer
) external nonReentrant {
    // Verificar que ambas órdenes referencian el mismo sub-token y precio
    require(orderSeller.subToken == orderBuyer.subToken, "MKT: token mismatch");
    require(orderSeller.price == orderBuyer.price, "MKT: price mismatch");
    require(orderSeller.amount == orderBuyer.amount, "MKT: amount mismatch");

    // Verificar firmas y nonces
    _verifyOrder(orderSeller, signatureSeller);
    _verifyOrder(orderBuyer, signatureBuyer);

    // Ejecutar swap atómico
    _executeTokenTransfer(orderSeller.trader, orderBuyer.trader, orderSeller.subToken, orderSeller.amount);
    uint256 totalPrice = orderSeller.amount * orderSeller.price;
    uint256 fee = (totalPrice * 250) / 10000; // 2.5%
    _executeIdeaTransfer(orderBuyer.trader, orderSeller.trader, totalPrice - fee);
    _executeIdeaTransfer(orderBuyer.trader, address(this), fee);
}

function _verifyOrder(Order calldata order, bytes calldata signature) internal {
    require(order.deadline >= block.timestamp, "MKT: expired");
    require(order.nonce == nonces[order.trader], "MKT: invalid nonce");
    nonces[order.trader]++; // consume el nonce (no hay replay)

    bytes32 digest = keccak256(abi.encodePacked(
        "\x19\x01",
        DOMAIN_SEPARATOR,
        keccak256(abi.encode(ORDER_TYPEHASH, order.trader, order.subToken,
                  order.amount, order.price, order.nonce, order.deadline))
    ));
    address signer = ECDSA.recover(digest, signature);
    require(signer == order.trader, "MKT: invalid signature");
}
```

### ¿Por qué no guardamos arrays de listings?

| Versión anterior (con arrays) | Versión nueva (solo mappings) |
|---|---|
| `mapping(address => uint256[]) listingsOfSeller` | ❌ Eliminado |
| `Listing[] public allListings` | ❌ Eliminado |
| `listings[id]` mapping para acceso directo | ✅ `mapping(uint256 => Listing) public listings` |
| Consultar listings de un seller: O(n) | ❌ No existe — el frontend usa The Graph |
| `buyTokens()` con iteración | ❌ `buyTokens(id, cantidad)` directo |
| **Problema:** con 10.000 sellers, el contrato no puede devolver arrays tan grandes | **Solución:** cada operación es O(1), la consulta es off-chain |

**¿Cómo consulta el frontend los listings entonces?**

1. **The Graph** (recomendado): indexa eventos `Listed` y `Bought`, el frontend consulta
   GraphQL. Escalable a millones de listings.
2. **Backend propio**: escucha eventos y los guarda en PostgreSQL. Misma data que The Graph.
3. Para el PoC: `getListingsPaginated()` y `getActiveListingsPaginated()` devuelven
   slices del mapping con offset/limit + total

---

## 12. Lección 11 — IdeaGovernance.sol: el poder de los holders

### ¿Qué problema resuelve?

La plataforma no puede ser una **dictadura** donde el equipo decide todo. Los holders de $IDEA
deben tener poder de decisión sobre:
- Qué proyectos se aprueban
- Qué fees se cobran
- Cómo se usa el treasury

### ¿Cómo lo pensamos?

"¿Por qué alguien querría $IDEA además de para revenderlo?" nos hizo
pensar. Governance es una de las respuestas más fuertes.

Analizamos protocolos existentes:

| Protocolo | Sistema de governance |
|---|---|
| Uniswap | UNI → votan cambios de protocolo |
| Compound | COMP → votan markets y parámetros |
| MakerDAO | MKR → votan estabilidad del DAI |
| **IDEAFY** | **IDEA → votan proyectos y fees** |

### Parámetros y por qué esos valores

| Parámetro | Valor | ¿Por qué? |
|---|---|---|
| `PROPOSAL_THRESHOLD` | 1.000 $IDEA | Suficiente para evitar spam (un usuario normal no tiene 1.000 $IDEA), pero no tanto como para que solo los grandes puedan proponer |
| `QUORUM_BPS` | 1.000 (10%) | Suficiente para que una propuesta tenga legitimidad (10% del supply votó), sin ser imposible de alcanzar |
| `VOTING_PERIOD` | 7 días | Da tiempo para que todos los holders voten, sin que la propuesta quede abierta para siempre |

---

## 13. Comparativa completa: antes vs después

### Estructural

| Aspecto | Antes (InvestmentSwap + ProjectToken) | Después (8 contratos) |
|---|---|---|
| Cantidad de contratos | 2 | 8 |
| Líneas de código | ~161 | ~725 |
| Token base | ProjectToken (sin cap, sin mint) | IdeaToken (cap 100M, sin mint, burn 1%) |
| Sub-token | ProjectToken (mint público, con quema, sin metadatos) | SubToken (supply fijo, sin quema, con proyectoId/rubro/dividend%) |
| Venta primaria | invest() sin límites | OfferingContract con soft/hard cap, tiempo, refund |
| Dividendos | No existían on-chain (backend enviaba USDT) | DividendDistributor con accumulator en $IDEA |
| Swap | No existía | IdeaSwap con pool de reservas |
| Marketplace | No existía | IdeaMarketplace con order book |
| Governance | No existía | IdeaGovernance on-chain |

### Funcional

| Feature | Antes | Ahora |
|---|---|---|
| Crear token para proyecto | ✅ InvestmentSwap.crearTokenProyecto() | ✅ IdeafyFactory.launchProject() via ERC1967Proxy |
| Invertir en proyecto | ✅ InvestmentSwap.invest() | ✅ OfferingContract.invest() |
| Soft cap / Hard cap | ❌ | ✅ |
| Refund automático (lo hace el inversor) | ❌ (solo owner) | ✅ |
| Dividendos en $IDEA | ❌ | ✅ |
| Swap $IDEA↔USDC | ❌ | ✅ |
| Marketplace secundario | ❌ | ✅ (on-chain listings + EIP-712 off-chain) |
| Governance on-chain | ❌ | ✅ |
| Roles granulars (creator, admin, allocator) | ❌ (solo Owner) | ✅ AccessControl |
| Cap máximo de supply | ❌ | ✅ 100M |
| Burn deflacionario | ❌ (solo sub-token 0.1%) | ✅ 1% en $IDEA |
| Metadatos on-chain en sub-token | ❌ | ✅ |
| Accumulator pattern (O(1) claims) | ❌ | ✅ |
| Upgradeabilidad de SubToken | ❌ (no proxy) | ✅ UUPS Proxy via ERC1967Proxy |
| Paginación en queries | ❌ (arrays completos) | ✅ offset/limit + total |
| EIP-712 firmas para trading off-chain | ❌ | ✅ executeOrder() con nonces |
| Arrays de listings en Marketplace | ❌ | ❌ Eliminados (`listingsOfSeller`, `allListings`) |

---

## 14. Escalabilidad 100x: qué aguanta y qué no

### Costos reales en Base (estimados)

| Operación | Gas | Costo en Base (0.01 gwei) |
|---|---|---|
| Deployar IdeaToken | ~1.5M | ~USD 0.015 |
| Deployar IdeafyFactory | ~1.2M | ~USD 0.012 |
| launchProject() (deploya SubToken) | ~500k | ~USD 0.005 |
| invest() en Offering | ~80k | ~USD 0.0008 |
| claimTokens() | ~60k | ~USD 0.0006 |
| distribute() | ~70k | ~USD 0.0007 |
| claim() dividendos | ~50k | ~USD 0.0005 |
| swap en IdeaSwap | ~80k | ~USD 0.0008 |
| buyTokens() Marketplace | ~90k | ~USD 0.0009 |
| vote() | ~50k | ~USD 0.0005 |

**Para 10.000 proyectos:**
- Deploy SubTokens: 10.000 × $0.005 = **~USD 50**
- Inversiones: 10.000 × $0.0008 = **~USD 8**
- Claims: 10.000 × $0.0006 = **~USD 6**
- **Total: ~USD 64**

### Qué aguanta 100x sin cambios

| Componente | Por qué |
|---|---|
| IdeafyFactory | `launchProject()` es O(1). Un deploy de SubToken cuesta ~$0.005. 10.000 proyectos = USD 50 |
| SubToken | Cada uno es independiente. Los balances son mappings (O(1)). No hay límite |
| OfferingContract | `invest()` es O(1). `claimTokens()` es O(1). `finalize()` no itera inversores |
| DividendDistributor | Accumulator pattern: O(1) por claim. Cada holder paga su gas |
| IdeaGovernance | `vote()` es O(1). La votación es individual, no por iteración |

### Evolución de esta sección

**Versión 1 (cuando se creó el documento) — "Qué hay que cambiar para 100x" (todo pendiente):**

| Componente | Problema | Solución propuesta | Prioridad |
|---|---|---|---|
| **IdeaMarketplace** | Arrays de listings crecen sin límite | Matching off-chain (backend matchea, contrato solo settlement) | ALTA |
| **Frontend** | 10.000 RPC calls para `balanceOf()` | The Graph subgraph | ALTA |
| **Dividend reports** | 333 reportes/día manuales | Chainlink oracle | MEDIA |
| **SubToken upgrade** | No se pueden modificar 10.000 SubTokens | UUPS Proxy | MEDIA |

**Versión 2 (después de implementar) — "Qué cambiamos para 100x" (3 de 4 items ✅):**

| Componente | Problema | Solución implementada | Estado |
|---|---|---|---|
| **IdeaMarketplace** | Arrays de listings crecen sin límite. Consultar listings de un seller devuelve arrays enormes | **Eliminados `listingsOfSeller[]` y `allListings[]`**. Matching off-chain con EIP-712 firmas. El contrato solo verifica nonces y ejecuta settlement. `getListingsPaginated()` + `getActiveListingsPaginated()` para consultas on-chain | ✅ |
| **SubToken upgrade** | No se pueden modificar 10.000 SubTokens ya deployados | **UUPS Proxy**: `ERC1967Proxy` + `SubToken upgradeable`. Factory guarda `subTokenImplementation`, deploya proxies. `_authorizeUpgrade()` solo factory | ✅ |
| **Paginación en todas las queries** | Funciones como `getInvestors()`, `getSubTokens()`, `getListings()` devuelven arrays completos | **Todas ahora tienen versión paginada**: `getInvestorsPaginated()`, `getSubTokensPaginated()`, `getListingsPaginated()`, `getActiveListingsPaginated()`. Cada una devuelve `(result[], total)` | ✅ |

### Qué falta para 100x (los que no implementamos)

| Componente | Problema | Solución | Prioridad |
|---|---|---|---|
| **Frontend** | Consultar `balanceOf()` para 10.000 sub-tokens = 10.000 RPC calls | The Graph subgraph (indexar eventos, consultar GraphQL) | ALTA |
| **Dividend reports** | Admin verificando 333 reportes/día manualmente | Chainlink oracle (reporte automático desde POS) | MEDIA |

---

## 15. Pendiente para Stage 2

### 📋 Lo que estaba pendiente (original) y cómo cambió

**Versión 1 (cuando se creó el documento):** Stage 2 incluía UUPS Proxy, off-chain matching y paginación

**Versión 2 (después de la sesión de escalabilidad):** Esos 3 items se hicieron en Stage 1. Stage 2 ahora solo incluye el refactor del backend Java.

### ✅ Escalabilidad 100x — implementado en Stage 1 (era parte de Stage 2 original)

| Lo que dijimos que había que cambiar | Cómo lo implementamos |
|---|---|
| **UUPS Proxy** para SubToken | SubToken usa `ERC20Upgradeable` + `UUPSUpgradeable`. Factory deploya `ERC1967Proxy` en lugar de `new SubToken()`. `_authorizeUpgrade()` restringido a factory |
| **Off-chain matching** en Marketplace | Se eliminaron arrays `listingsOfSeller[]` y `allListings[]`. Se agregó `executeOrder()` con EIP-712 firmas, nonces, y deadline. Compatible con backend de order book |
| **Paginación** en todas las queries | `getSubTokensPaginated()`, `getInvestorsPaginated()`, `getListingsPaginated()`, `getActiveListingsPaginated()` — todas devuelven `(result[], total)` |

### 🚧 Lo que queda para Stage 2 (después de mover los 3 items a Stage 1)

#### Refactorizar el backend Java

| Servicio Java actual | Contrato nuevo | Cambio necesario |
|---|---|---|
| TokenizationService | IdeafyFactory | Cambiar llamada de `InvestmentSwap.crearTokenProyecto()` a `IdeafyFactory.launchProject()` |
| InvestmentService | OfferingContract | Cambiar de `InvestmentSwap.invest()` a `OfferingContract.invest()` + `claimTokens()` |
| DividendService | DividendDistributor | Cambiar distribución en USDT a distribución en $IDEA |
| InvestmentSwap service | IdeaSwap | Nuevo servicio para swap $IDEA/USDC |
| MarketplaceService | IdeaMarketplace | Nuevo servicio para marketplace |
| GovernanceService | IdeaGovernance | Nuevo servicio para governance |

#### Configuración

- Actualizar `application.properties` con nuevas direcciones de contratos
- Actualizar `Web3jConfig` con los nuevos ABIs
- Migraciones SQL para tabla `rubros` y columna `rubro_id` en `projects`

---

## 16. Apéndice A — Glosario de conceptos

### A

**AMM (Automated Market Maker)**
Mecanismo que determina el precio de un token automáticamente según las reservas de un pool
de liquidez, sin necesidad de un order book. Ej: Uniswap. En IDEAFY no usamos AMM puro,
sino un pool simple con precio lineal.

**Accumulator Pattern**
Técnica para distribuir recompensas sin iterar sobre todos los usuarios. Se mantiene un
acumulador (`dividendPerToken`) que aumenta con cada distribución, y cada usuario reclama
su parte calculando `(acumulador - su_snapshot) * su_balance`. O(1) por claim, no O(n)
por distribución.

### B

**Basis Points (bps)**
Unidad de medida financiera. 1 bps = 0.01%. 100 bps = 1%. 10000 bps = 100%.
Se usa para evitar errores con decimales. Ej: en vez de decir "fee de 2.5%" decimos "fee de 250 bps".

**Burn**
Destruir tokens permanentemente, enviándolos a `address(0)`. Reduce el supply circulante.
En IDEAFY: 1% de cada transferencia de $IDEA se quema.

### C

**Cap**
Límite máximo. Puede ser de supply (MAX_SUPPLY = 100M) o de recaudación (hardCap).

**Claim**
Acción de reclamar activamente un derecho. En nuestros contratos: `claimTokens()` (reclamar
sub-tokens después de una offering exitosa) y `claim()` (reclamar dividendos acumulados).

**Circulating Supply**
Cantidad de tokens que están en circulación (no quemados, no bloqueados). Diferente del
total supply si hay tokens quemados.

**Composabilidad**
Propiedad de un sistema donde los componentes pueden combinarse de diferentes maneras.
Nuestros contratos se conectan por referencias (direcciones), no por herencia, lo que
los hace altamente componibles.

### D

**Dividendos**
Pagos periódicos a los holders de un activo. En IDEAFY, los holders de sub-tokens reciben
dividendos en $IDEA proporcionales a su tenencia.

**Deploy**
Acción de subir un contrato a la blockchain. Cuesta gas. Una vez deployado, el código
es inmutable (no se puede cambiar, excepto con proxies).

### E

**ERC-20**
Estándar de tokens fungibles en Ethereum. Define funciones: `balanceOf()`, `transfer()`,
`transferFrom()`, `approve()`, `totalSupply()`. Todos nuestros tokens son ERC-20.

**Escrow**
Tercero de confianza que retiene fondos hasta que se cumplan ciertas condiciones.
En nuestro OfferingContract, los $IDEA de los inversores están en escrow hasta que
la ronda se finaliza.

### F

**Factory Pattern**
Patrón de diseño donde un contrato (la factory) crea otros contratos. En IDEAFY,
IdeafyFactory deploya nuevos SubTokens mediante `ERC1967Proxy` apuntando a una
implementación de SubToken upgradeable.

**Fee**
Comisión que cobra la plataforma por una operación. En IDEAFY: swap 1%, marketplace 2.5%,
transferencia de $IDEA 1% (burn).

### G

**Gas**
Unidad de medida del costo computacional en Ethereum/Base. Cada operación (suma, escritura
en storage, transferencia) cuesta una cantidad fija de gas. El gas se paga en la moneda
nativa de la red (ETH en Ethereum, ETH en Base).

**Governance**
Sistema de votación donde los holders de un token deciden el futuro de un protocolo.
En IDEAFY: holders de $IDEA votan aprobación de proyectos, cambios de fees, etc.

### H

**Hard Cap**
Máximo a recaudar en una ronda de inversión. Protege al inversor de dilución excesiva.

**Holdear**
Mantener un token sin vender, esperando que suba de precio o para obtener beneficios
(dividendos, governance, etc.).

### I

**Immutable**
Que no se puede cambiar. En Solidity, las variables `immutable` se setean una vez en el
constructor y no pueden modificarse después. Las constantes (`constant`) son inmutables
y además se reemplazan en tiempo de compilación (más baratas en gas).

### K

**KYC (Know Your Customer)**
Proceso de verificación de identidad. Obligatorio por regulación (CNV en Argentina) para
plataformas financieras.

### L

**Liquidity Pool**
Conjunto de tokens depositados en un contrato para permitir intercambios. En IDEAFY,
el pool de $IDEA/USDC en IdeaSwap permite convertir entre ambos tokens.

**Listing**
En el contexto del marketplace, una orden de venta de un sub-token a un precio fijo.

### M

**Mapping**
Estructura de datos en Solidity similar a un HashMap/Map en Java. `mapping(Key => Value)`.
Siempre O(1) en lectura y escritura. No se puede iterar (no hay keys view).

**Mint**
Crear nuevos tokens. En nuestros contratos, el mint solo ocurre en el constructor.
No hay funciones de mint() públicas.

### N

**NonReentrant**
Modifier que previene ataques de reentrancia (cuando un contrato malicioso llama a una
función varias veces antes de que termine la primera ejecución). Lo proporciona
OpenZeppelin's ReentrancyGuard.

### O

**Offering**
Ronda de financiamiento de un proyecto. Incluye soft cap, hard cap, precio por token,
fecha de inicio y fin.

**Order Book**
Lista de órdenes de compra y venta para un activo. En nuestro Marketplace, los vendedores
listan sus sub-tokens a un precio, y los compradores eligen qué orden tomar.

**Oracle**
Servicio que trae datos del mundo real a la blockchain. Ej: Chainlink. Necesario para
automatizar los reportes de ganancia de los creadores.

**Owner**
En el contexto de contratos, la dirección que tiene permisos administrativos. En nuestros
contratos nuevos usamos AccessControl (roles), no Ownable (un solo dueño).

### P

**Pool de Liquidez**
Ver Liquidity Pool.

**Proxy**
Contrato que delega llamadas a otro contrato (la implementación). Permite actualizar
el código de un contrato sin cambiar su dirección. Usado para upgradeabilidad.

### Q

**Quorum**
Mínimo de participación necesaria para que una votación sea válida. En nuestro governance:
10% del total supply de $IDEA debe votar para que la propuesta sea ejecutable.

### R

**Refund**
Devolución de fondos a un inversor cuando una ronda no alcanza su objetivo. En nuestro
OfferingContract, cada inversor puede llamar refund() individualmente.

**Rol**
Permiso específico en el sistema de AccessControl. En IDEAFY: CREATOR_ROLE, ADMIN_ROLE,
ALLOCATOR_ROLE, LIQUIDITY_PROVIDER_ROLE, EXECUTOR_ROLE.

**Rubro**
Categoría del proyecto. Determina el porcentaje de ganancia que va a inversores.
1=Gastro (40%), 2=Tech (20%), 3=Agro (50%), 4=Inmo (60%).

### S

**Slippage**
Diferencia entre el precio esperado y el precio real de un swap. Ocurre porque cada swap
cambia las reservas del pool. A mayor tamaño del swap relativo al pool, mayor slippage.

**Soft Cap**
Mínimo necesario para que una ronda de inversión sea considerada exitosa. Si no se alcanza,
los inversores recuperan su dinero (refund).

**Supply**
Cantidad total de tokens que existen. Puede ser:
- **Total Supply:** todos los tokens creados (incluye quemados)
- **Circulating Supply:** tokens en circulación (excluye quemados)

**Swap**
Intercambio de un token por otro. En IDEAFY: $IDEA ↔ USDC en IdeaSwap.

### T

**Timestamp Unix**
Número de segundos desde el 1 de enero de 1970. En Solidity, `block.timestamp` devuelve
el timestamp del bloque actual. Se usa para medir tiempo en los contratos.

**Token**
Activo digital en una blockchain que sigue un estándar (ej: ERC-20). Representa valor,
participación, o derechos.

**Total Supply**
Ver Supply.

**Treasury**
Billetera de la plataforma que recibe fees y administra fondos del ecosistema.

### U

**UUPS (Universal Upgradeable Proxy Standard)**
Estándar de proxies upgradeables donde la lógica de upgrade está en la implementación,
no en el proxy. Más eficiente en gas que el proxy tradicional. En IDEAFY, SubToken hereda
`UUPSUpgradeable` y `_authorizeUpgrade()` solo permite al owner (la factory) actualizar
la implementación.

**USDC / USDT**
Stablecoins que valen ~1 USD cada una. USDC de Circle, USDT de Tether.

### W

**Wallet**
Dirección en la blockchain que puede tener tokens y hacer transacciones. Controlada por
una clave privada.

**Wei**
Unidad más pequeña de Ether (y de tokens ERC-20 con 18 decimales). 1 token = 10^18 wei.
Todos nuestros cálculos internos usan wei para evitar errores de redondeo.

---


## 2. Stage 2 � Backend Java

### Arquitectura actual

El backend Java se comunica con contratos inteligentes en Sepolia a trav�s de Web3j raw. Los services codifican las llamadas manualmente con `FunctionEncoder.encode()` y decodifican con `FunctionReturnDecoder.decode()`.

### Servicios del backend

El backend expone los siguientes servicios para la capa blockchain:

| Service | Rol |
|---|---|
| `IdeafyFactoryService` | Llama a `IdeafyFactory.launchProject()` para crear un SubToken upgradeable via ERC1967Proxy |
| `OfferingContractService` | Interact�a con `OfferingContract.invest()` para inversiones con soft/hard cap |
| `DividendDistributorService` | Interact�a con `DividendDistributor.distribute()` y `claim()` para dividendos en $IDEA |
| `TokenizationService` | Orquesta la creaci�n de tokens, usando `IdeafyFactoryService` como primario y servicios legacy como fallback |
| `InvestmentService` | Orquesta las inversiones, usando `OfferingContractService` para la interacci�n on-chain |
| `DividendService` | Gestiona reparto y reclamo de dividendos a trav�s de `DividendDistributorService` |
| `IdeaSwapService` | Expone funciones de `IdeaSwap.sol`: swap $IDEA ? USDC, add/remove liquidity |
| `IdeaMarketplaceService` | Expone funciones de `IdeaMarketplace.sol`: listar tokens, comprar, ejecutar �rdenes EIP-712 |
| `IdeaGovernanceService` | Expone funciones de `IdeaGovernance.sol`: crear propuestas, votar, ejecutar |
| `SmartContractService` | Adapter/wrapper para consultas on-chain gen�ricas (getContractInfo, verifyTransaction, deployContract) |
| `BlockchainService` | Wrapper gen�rico para cualquier ERC20 (balanceOf, allowance, etc.) |
| `DynamicPricingService` | Estima precios para mostrar en frontend (el precio final lo determina el contrato) |

### IdeafyFactoryService

El servicio `IdeafyFactoryService` codifica la funci�n `launchProject()` del contrato `IdeafyFactory.sol`:

```solidity
function launchProject(
    uint256 proyectoId,
    uint256 rubroId,
    uint256 dividendBps,
    address creator,
    string calldata nombre,
    string calldata simbolo,
    uint256 supplyInicial
) external onlyRole(CREATOR_ROLE) returns (address)
```

Construye la transacci�n con `FunctionEncoder`, la firma con `Credentials`, la env�a con `web3j.ethSendRawTransaction()`, espera el receipt y parsea el evento `ProjectLaunched` para obtener la direcci�n del SubToken.

### OfferingContractService

El servicio `OfferingContractService` codifica la funci�n `invest()` del contrato `OfferingContract.sol`:

```solidity
function invest(uint256 proyectoId, uint256 ideaAmount, uint256 minTokens, address subToken)
    external nonReentrant
```

El contrato recibe $IDEA del inversor, calcula sub-tokens seg�n el precio, valida soft/hard cap, y retiene los $IDEA en escrow. Si la ronda no alcanza soft cap, el inversor puede llamar `refund()` directamente en el contrato.

### DividendDistributorService

```solidity
function distribute(uint256 proyectoId, uint256 totalDividend) external;
function claim(uint256 proyectoId) external;
function getClaimable(uint256 proyectoId, address user) external view returns (uint256);
```

- `distribute()`: el admin deposita $IDEA, se actualiza `dividendPerToken`
- `claim()`: el holder reclama dividendos acumulados
- `getClaimable()`: consulta cu�nto puede reclamar un usuario (call view, sin gas)

### Refactor de TokenizationService

`TokenizationService` intenta crear el token primero con `IdeafyFactoryService.launchProject()`. Si falla (porque los contratos nuevos no est�n deployados), usa `InvestmentSwapService.crearTokenProyecto()` como fallback legacy. Siempre guarda el resultado en `tokenizationRepository`.

### Refactor de InvestmentService

`InvestmentService` usa `OfferingContractService.invest()` para la inversi�n on-chain. El contrato maneja soft/hard cap, refund y distribuci�n de tokens autom�ticamente. El backend guarda en DB para tener datos queryables (listado de inversiones de un usuario, etc.).

### Refactor de DividendService

`DividendService` utiliza `DividendDistributorService` para todas las operaciones:
- `crearReparto()` ? llama a `DividendDistributor.distribute()` y guarda el txHash en DB
- `reclamarDividendos()` ? consulta `getClaimable()`, verifica que haya saldo, llama a `claim()`, guarda registro hist�rico
- `consultarDividendosPendientes()` ? llama a `getClaimable()` (view, sin gas)

### IdeaSwapService

Expone las funciones del contrato `IdeaSwap.sol`:
- `swapUsdcForExactIdea(BigInteger ideaAmount)` ? comprar $IDEA con USDC
- `swapIdeaForExactUsdc(BigInteger usdcAmount)` ? vender $IDEA por USDC
- `addLiquidity(BigInteger ideaAmount, BigInteger usdcAmount)` ? agregar liquidez al pool
- `removeLiquidity(BigInteger ideaAmount, BigInteger usdcAmount)` ? remover liquidez
- `getPrice()` ? consultar precio actual del pool (view)

### IdeaMarketplaceService

Expone las funciones del contrato `IdeaMarketplace.sol`:
- `listTokens(address subToken, BigInteger amount, BigInteger price)` ? listar tokens para venta
- `buyTokens(BigInteger listingId, BigInteger amount)` ? comprar tokens listados
- `getListingsPaginated(int offset, int limit)` ? consultar listings activos
- `executeOrder(...)` ? ejecutar �rdenes firmadas con EIP-712

### IdeaGovernanceService

Expone las funciones del contrato `IdeaGovernance.sol`:
- `createProposal(...)` ? crear propuesta (requiere 1.000 $IDEA)
- `vote(BigInteger proposalId, boolean support)` ? votar propuesta
- `executeProposal(BigInteger proposalId)` ? ejecutar propuesta aprobada
- `getProposals()` ? consultar propuestas activas

### Servicios sin cambios

- `SmartContractService`: se mantiene como adapter para consultas on-chain gen�ricas
- `BlockchainService`: se mantiene como wrapper gen�rico para ERC20
- `PaymentEventService`: escucha eventos de `PaymentGateway` para reconciliaci�n
- `DynamicPricingService`: estima precios para el frontend (el precio final lo determina el contrato)
- `SecurityConfig`, `AuthServiceClient`: sin cambios en la capa de seguridad

### Configuraci�n

`BlockchainProperties` incluye las direcciones de los 7 contratos nuevos:

```
blockchain.idea-token-address=${BLOCKCHAIN_IDEA_TOKEN}
blockchain.ideafy-factory-address=${BLOCKCHAIN_IDEAFY_FACTORY}
blockchain.offering-contract-address=${BLOCKCHAIN_OFFERING_CONTRACT}
blockchain.dividend-distributor-address=${BLOCKCHAIN_DIVIDEND_DISTRIBUTOR}
blockchain.idea-swap-address=${BLOCKCHAIN_IDEA_SWAP}
blockchain.idea-marketplace-address=${BLOCKCHAIN_IDEA_MARKETPLACE}
blockchain.idea-governance-address=${BLOCKCHAIN_IDEA_GOVERNANCE}
```

### Stack tecnol�gico

Web3j raw para todas las interacciones con contratos. No se usan wrappers generados con `web3j-maven-plugin` para mantener consistencia con el c�digo existente y tener control fino sobre parsing de logs, manejo de errores y estimaci�n de gas.

---


## 3. Stage 4 � Dynamic Pricing con Factor de Rendimiento del Proyecto

# Stage 4 — Dynamic Pricing con Factor de Rendimiento del Proyecto

## Índice

1. [¿Qué es este documento?](#1-qué-es-este-documento)
2. [¿Qué problema resuelve?](#2-qué-problema-resuelve)
3. [Conceptos básicos (para principiantes)](#3-conceptos-básicos-para-principiantes)
4. [El modelo anterior: solo oferta/demanda](#4-el-modelo-anterior-solo-oferta-demanda)
5. [El nuevo modelo: oferta/demanda + rendimiento](#5-el-nuevo-modelo-oferta-demanda--rendimiento)
6. [Mapa de archivos involucrados](#6-mapa-de-archivos-involucrados)
7. [Paso 1: DynamicPricingService.java](#7-paso-1-dynamicpricingservicejava)
8. [Paso 2: SubtokenService.java](#8-paso-2-subtokenservicejava)
9. [Paso 3: InvestmentService.java](#9-paso-3-investmentservicejava)
10. [El flujo completo paso a paso](#10-el-flujo-completo-paso-a-paso)
11. [Ejemplos numéricos](#11-ejemplos-numéricos)
12. [Tabla de factores por estado](#12-tabla-de-factores-por-estado)
13. [Nuevo endpoint: GET /api/tokens/{proyectoId}/precio](#13-nuevo-endpoint-get-apitokensproyectoidprecio)
14. [Preguntas frecuentes](#14-preguntas-frecuentes)

---

## 1. ¿Qué es este documento?

Este documento explica **Stage 4**: la modificación del cálculo de precio dinámico de sub-tokens para que incluya **el rendimiento del proyecto** como factor, además de la oferta/demanda que ya existía.

---

## 2. ¿Qué problema resuelve?

### El problema

El profe dijo en clase:

> *"Si estás construyendo un edificio, tenés el riesgo de que se termine o no. Si quiero comprar una cancha cuando no se inició, vale un peso; si está terminada a estrenar, tiene otro precio."*

Y también:

> *"Ustedes asociaron el proyecto a un token. Si el proyecto se fue al tacho y no se terminó, el precio del token cae. Si se terminó, sube."*

**Antes de Stage 4**, el precio solo subía porque se vendían más tokens (oferta/demanda). Pero no importaba si el proyecto estaba en la etapa de "pozo" o "a estrenar". El precio era el mismo.

**Después de Stage 4**, el precio también sube porque el proyecto avanza: pasa de PREPARACIÓN → FINANCIAMIENTO → EJECUCIÓN → FINALIZADO. Cada etapa tiene menos riesgo, y por lo tanto el token vale más.

### Ejemplo del profe (inmobiliario)

| Etapa | Precio por m² | Por qué |
|-------|--------------|---------|
| En pozo (antes de construir) | USD 2.300 | Alto riesgo, no se sabe si termina |
| A estrenar (terminado) | USD 3.500 | Bajo riesgo, ya está construido |

La diferencia es **52% más caro**. Eso es lo que llamamos **factor rendimiento**.

---

## 3. Conceptos básicos (para principiantes)

### ¿Qué es "precio dinámico"?

Es un precio que **cambia solo**, sin que nadie lo actualice manualmente. En nuestro sistema, el precio de un sub-token cambia por dos razones:

1. **Oferta/demanda**: cuantos más tokens se venden, más caros se vuelven (porque quedan menos disponibles)
2. **Rendimiento del proyecto**: cuanto más avanzado está el proyecto, más caro es el token (porque hay menos riesgo)

### ¿Qué es "factor de rendimiento"?

Es un número entre **0.00** y **0.75** que representa qué tan avanzado está el proyecto:

| Factor | Significado |
|--------|-------------|
| 0.00 | Proyecto ni empezó (PREPARACIÓN) |
| 0.15 - 0.35 | En financiamiento (depende de cuánto juntó) |
| 0.50 | En ejecución (ya se está construyendo) |
| 0.75 | Finalizado (riesgo mínimo) |

### Fórmula completa (nueva)

```
precio_final = precio_base × (1 + demanda × volatilidad) × (1 + rendimiento)
```

Se lee así:
- `precio_base`: el precio inicial que puso el creador
- `(1 + demanda × volatilidad)`: cuanto más vendido, más caro (máximo +50%)
- `(1 + rendimiento)`: cuanto más avanzado, más caro (máximo +75%)

---

## 4. El modelo anterior: solo oferta/demanda

### Cómo era antes

Solo había **un factor**: la demanda. La fórmula era:

```
precio = precio_base × (1 + demanda_relativa × factor_volatilidad)
```

| Variable | Significado | Ejemplo |
|----------|-------------|---------|
| `precio_base` | Precio inicial del token | $10.00 |
| `demanda_relativa` | % vendido = vendidos / total | 0.50 (50%) |
| `factor_volatilidad` | Qué tan sensible es el precio | 0.50 |

### Ejemplo antes

```
precio = 10 × (1 + 0.50 × 0.50)
       = 10 × 1.25
       = $12.50
```

El precio subió de $10 a $12.50 porque se vendió el 50% de los tokens. Pero **no importaba si el proyecto estaba en financiamiento o ya terminado**.

### Código antes

```java
// DynamicPricingService.java (VERSIÓN ANTIGUA)
public BigDecimal calcularPrecioDinamico(
        BigDecimal precioBase,
        int suministroTotal,
        int cupoRestante,
        BigDecimal factorVolatilidad
) {
    BigDecimal vendido = suministroTotal - cupoRestante;
    BigDecimal demandaRelativa = vendido / suministroTotal;
    BigDecimal incremento = 1 + (demandaRelativa * factorVolatilidad);
    return precioBase * incremento;
}
```

Solo recibía 4 parámetros. No sabía nada del proyecto.

---

## 5. El nuevo modelo: oferta/demanda + rendimiento

### Cómo es ahora

Ahora hay **dos factores**: la demanda **y** el rendimiento del proyecto. La fórmula es:

```
precio = precio_base × (1 + demanda_relativa × factor_volatilidad) × (1 + factor_rendimiento)
```

### Cómo se calcula el factor rendimiento

El `SubtokenService` consulta la tabla `projects` y saca dos datos:

1. **`estado`** del proyecto (PREPARACIÓN, FINANCIAMIENTO, EJECUCIÓN, FINALIZADO)
2. **`monto_recaudado / monto_requerido`**: qué % del objetivo ya se juntó (solo para FINANCIAMIENTO)

Con eso aplica esta tabla:

| Estado del proyecto | Factor base | Ajuste adicional | Factor final |
|--------------------|-------------|-------------------|--------------|
| PREPARACIÓN | 0.00 | — | **0.00** |
| FINANCIAMIENTO | 0.15 | + hasta 0.20 según % recaudado | **0.15 a 0.35** |
| EJECUCIÓN | 0.50 | — | **0.50** |
| FINALIZADO | 0.75 | — | **0.75** |
| CANCELADO / RECHAZADO | 0.00 | — | **0.00** |

### ¿Por qué estos números?

| Factor | Explicación |
|--------|-------------|
| 0.00 | Riesgo máximo, no hay proyecto real todavía |
| 0.15 | Hay un plan, se está juntando plata. Riesgo alto |
| 0.35 | Ya juntó casi toda la plata. Riesgo medio-alto |
| 0.50 | El proyecto arrancó, se está ejecutando. Riesgo medio |
| 0.75 | El proyecto terminó. Riesgo mínimo |

### Ejemplo ahora

Mismo proyecto, pero ahora con estado EJECUCIÓN (factor rendimiento = 0.50):

```
precio = 10 × (1 + 0.50 × 0.50) × (1 + 0.50)
       = 10 × 1.25 × 1.50
       = $18.75
```

Antes daba $12.50. Ahora da $18.75 porque el proyecto ya está en ejecución (menos riesgo).

---

## 6. Mapa de archivos involucrados

| Archivo | Ruta | Qué cambió |
|---------|------|------------|
| `DynamicPricingService.java` | `src/main/java/com/systeam/tokenization/service/` | Nuevo parámetro `factorRendimiento`, fórmula actualizada |
| `SubtokenService.java` | `src/main/java/com/systeam/tokenization/service/` | Nuevo método `obtenerFactorRendimiento()`, calcula el factor desde la BD |
| `InvestmentService.java` | `src/main/java/com/systeam/investment/service/` | Pasa `proyectoId` a `calcularPrecio()` en las 3 llamadas |
| `modulos-estado.json` | `src/main/resources/` | "Endpoint de cotización" pasa de pendiente → hecho |
| `API_DOCUMENTATION.md` | `./` | Descripción actualizada para incluir factor rendimiento |
| `TokenizationController.java` | `src/main/java/com/systeam/tokenization/controller/` | Nuevo endpoint `GET /api/tokens/{proyectoId}/precio` |
| `SubtokenPriceResponse.java` | `src/main/java/com/systeam/tokenization/dto/` | **NUEVO** — DTO con detalle del precio (factores incluidos) |

### ¿Qué archivos NO cambian?

| Archivo | Por qué no cambia |
|---------|------------------|
| `OfferingContractService.java` | El contrato on-chain recibe el precio ya calculado desde Java |
| `DividendService.java` | No tiene relación con pricing |
| `IdeaToken.sol` / `SubToken.sol` | El pricing es off-chain, no on-chain |
| Base de datos (migraciones) | No se necesita nueva columna, usamos `estado`, `monto_recaudado` y `monto_requerido` que ya existen |
| Frontend | No cambia la API, solo el valor que devuelve |

---

## 7. Paso 1: DynamicPricingService.java

### Antes

```java
public BigDecimal calcularPrecioDinamico(
        BigDecimal precioBase,
        int suministroTotal,
        int cupoRestante,
        BigDecimal factorVolatilidad
) {
    // ... solo calculaba oferta/demanda
    BigDecimal incremento = 1 + (demandaRelativa * factorVolatilidad);
    return precioBase * incremento;
}
```

### Después

```java
public BigDecimal calcularPrecioDinamico(
        BigDecimal precioBase,
        int suministroTotal,
        int cupoRestante,
        BigDecimal factorVolatilidad,
        BigDecimal factorRendimiento      // ← NUEVO
) {
    BigDecimal incrementoDemanda = 1 + (demandaRelativa * factorVolatilidad);
    BigDecimal incrementoRendimiento = 1 + factorRendimiento;   // ← NUEVO

    BigDecimal precioDinamico = precioBase
            .multiply(incrementoDemanda)
            .multiply(incrementoRendimiento)  // ← NUEVO
            .setScale(2, RoundingMode.HALF_UP);

    return precioDinamico;
}
```

### ¿Qué cambió exactamente?

1. **Nuevo parámetro**: `BigDecimal factorRendimiento` — lo recibe desde `SubtokenService`
2. **Nuevo cálculo**: multiplica por `(1 + factorRendimiento)` además del factor de demanda
3. **Nuevo log**: ahora también muestra `factorRendimiento` en el debug

### ¿Por qué `(1 + factorRendimiento)`?

Porque si el factor es 0.00 (proyecto en preparación), `(1 + 0) = 1`, no afecta el precio. Si el factor es 0.50, `(1 + 0.50) = 1.50`, el precio sube 50%.

---

## 8. Paso 2: SubtokenService.java

### Antes

```java
public BigDecimal calcularPrecio(BigDecimal precioBase, int suministroTotal,
                                  int cupoRestante, BigDecimal factorVolatilidad) {
    return pricingService.calcularPrecioDinamico(
        precioBase, suministroTotal, cupoRestante, factorVolatilidad
    );
}
```

### Después

```java
public BigDecimal calcularPrecio(BigDecimal precioBase, int suministroTotal,
                                  int cupoRestante, BigDecimal factorVolatilidad,
                                  Long proyectoId) {               // ← NUEVO
    BigDecimal factorRendimiento = obtenerFactorRendimiento(proyectoId);  // ← NUEVO
    return pricingService.calcularPrecioDinamico(
        precioBase, suministroTotal, cupoRestante, factorVolatilidad,
        factorRendimiento     // ← NUEVO
    );
}

public BigDecimal obtenerFactorRendimiento(Long proyectoId) {     // ← NUEVO MÉTODO
    // Consulta projects: estado, monto_recaudado, monto_requerido
    // Según el estado, devuelve un factor entre 0.00 y 0.75
}
```

### ¿Qué hace `obtenerFactorRendimiento`?

1. Consulta la tabla `projects` con `SELECT estado, monto_requerido, monto_recaudado WHERE id = ?`
2. Aplica la lógica según el estado:

```java
switch (estado) {
    case "PREPARACION":
        return 0.00;    // Riesgo máximo, precio base

    case "FINANCIAMIENTO":
        progreso = monto_recaudado / monto_requerido
        return 0.15 + (progreso * 0.20);   // Entre 0.15 y 0.35

    case "EJECUCION":
        return 0.50;    // Riesgo medio

    case "FINALIZADO":
        return 0.75;    // Riesgo mínimo, precio máximo
}
```

### ¿Por qué FINANCIAMIENTO es variable (0.15 a 0.35)?

Porque dentro de la etapa de financiamiento, el riesgo cambia:
- **Recién arranca** (0% recaudado) → factor 0.15 (no se sabe si va a juntar la plata)
- **Casi completo** (90% recaudado) → factor 0.15 + 0.18 = 0.33 (casi seguro que se financia)
- **Completo** (100% recaudado) → factor 0.15 + 0.20 = 0.35 (máximo para esta etapa)

El multiplicador 0.20 es deliberado: si el proyecto recaudó el 100%, el factor sube 0.20 adicionales (de 0.15 a 0.35). Pero no puede pasar de 0.35 porque todavía no arrancó la ejecución.

---

## 9. Paso 3: InvestmentService.java

### ¿Qué cambió?

Muy simple: las llamadas a `calcularPrecio()` ahora pasan **también el `proyectoId`** como quinto parámetro.

### Antes (3 llamadas)

```java
// Llamada 1 — validateInvestment()
subtokenService.calcularPrecio(precioBase, suministroTotal, cupoRestante, factorVolatilidad);

// Llamada 2 — createInvestment() (calcular precio antes de invertir)
subtokenService.calcularPrecio(precioBase, suministroTotal, cupoRestante, factorVolatilidad);

// Llamada 3 — createInvestment() (recalcular precio después de descontar cupo)
subtokenService.calcularPrecio(precioBase, suministroTotal, nuevoCupo, factorVolatilidad);
```

### Después

```java
// Llamada 1 — validateInvestment()
subtokenService.calcularPrecio(precioBase, suministroTotal, cupoRestante,
    factorVolatilidad, request.getProyectoId());

// Llamada 2 — createInvestment()
subtokenService.calcularPrecio(precioBase, suministroTotal, cupoRestante,
    factorVolatilidad, request.getProyectoId());

// Llamada 3 — createInvestment() (recalcular después de vender)
subtokenService.calcularPrecio(precioBase, suministroTotal, nuevoCupo,
    factorVolatilidad, request.getProyectoId());
```

Solo se agregó `request.getProyectoId()` al final de cada llamada.

---

## 10. El flujo completo paso a paso

Este es el camino que recorre el precio desde que un inversor quiere comprar hasta que se actualiza en la base de datos:

```
INVERSOR
  │
  ▼
1. validateInvestment(request)
  │  request = { proyectoId: 5, montoIdea: 1000 }
  │
  ▼
2. InvestmentService.validateInvestment()
  │
  ├──► findProjectRowOrThrow(5)          → consulta projects
  │     Devuelve: { estado: "EJECUCION", monto_requerido: 50000, monto_recaudado: 30000 }
  │
  ├──► subtokenService.findSubtokenByProject(5)  → consulta subtokens
  │     Devuelve: { precio_base: 10.00, suministro_total: 10000,
  │                 cupo_restante: 7000, factor_volatilidad: 0.50 }
  │
  ├──► subtokenService.calcularPrecio(10.00, 10000, 7000, 0.50, 5)
  │     │
  │     ├──► obtenerFactorRendimiento(5)
  │     │     Consulta projects WHERE id = 5
  │     │     estado = "EJECUCION" → devuelve 0.50
  │     │
  │     └──► DynamicPricingService.calcularPrecioDinamico(10.00, 10000, 7000, 0.50, 0.50)
  │           │
  │           ├── demanda_relativa = (10000 - 7000) / 10000 = 0.30
  │           ├── incremento_demanda = 1 + (0.30 × 0.50) = 1.15
  │           ├── incremento_rendimiento = 1 + 0.50 = 1.50
  │           └── precio = 10.00 × 1.15 × 1.50 = 17.25
  │
  └──► Devuelve: { valido: true, precioSubtoken: 17.25, ... }
        Al inversor se le muestra: "El token sale $17.25"
```

### Después de que el inversor confirma

```
INVERSOR CONFIRMA
  │
  ▼
3. InvestmentService.createInvestment(request, usuarioId)
  │
  ├──► (mismo cálculo de precio: da $17.25)
  │
  ├──► Descuenta el cupo: cupo_restante = 7000 - subTokensComprados
  │
  ├──► subtokenService.calcularPrecio(10.00, 10000, nuevoCupo, 0.50, 5)
  │     El nuevo precio refleja que ahora hay menos cupo disponible
  │
  └──► subtokenService.updateQuotaAndPrice(subtokenId, subTokens, nuevoPrecio)
        UPDATE subtokens SET cupo_restante = cupo_restante - ?,
                             precio_actual = ? WHERE id = ?
```

---

## 11. Ejemplos numéricos

### Ejemplo 1: Proyecto en FINANCIAMIENTO, recién arranca

| Variable | Valor |
|----------|-------|
| precio_base | $10.00 |
| suministro_total | 10.000 tokens |
| cupo_restante | 9.500 (se vendieron 500) |
| factor_volatilidad | 0.50 |
| estado proyecto | FINANCIAMIENTO |
| monto_recaudado | $5.000 |
| monto_requerido | $50.000 |
| progreso | 5.000 / 50.000 = 0.10 (10%) |

```
demanda_relativa = (10000 - 9500) / 10000 = 0.05
factor_rendimiento = 0.15 + (0.10 × 0.20) = 0.17

precio = 10.00 × (1 + 0.05 × 0.50) × (1 + 0.17)
       = 10.00 × 1.025 × 1.17
       = $11.99
```

### Ejemplo 2: Mismo proyecto, ahora en EJECUCIÓN

| Variable | Valor |
|----------|-------|
| precio_base | $10.00 |
| suministro_total | 10.000 tokens |
| cupo_restante | 5.000 (se vendieron 5.000) |
| factor_volatilidad | 0.50 |
| estado proyecto | EJECUCIÓN |

```
demanda_relativa = (10000 - 5000) / 10000 = 0.50
factor_rendimiento = 0.50

precio = 10.00 × (1 + 0.50 × 0.50) × (1 + 0.50)
       = 10.00 × 1.25 × 1.50
       = $18.75
```

### Ejemplo 3: Proyecto FINALIZADO, casi todo vendido

| Variable | Valor |
|----------|-------|
| precio_base | $10.00 |
| suministro_total | 10.000 tokens |
| cupo_restante | 500 (se vendieron 9.500) |
| factor_volatilidad | 0.50 |
| estado proyecto | FINALIZADO |

```
demanda_relativa = (10000 - 500) / 10000 = 0.95
factor_rendimiento = 0.75

precio = 10.00 × (1 + 0.95 × 0.50) × (1 + 0.75)
       = 10.00 × 1.475 × 1.75
       = $25.81
```

### Comparativa

| Escenario | Solo demanda | Demanda + Rendimiento | Diferencia |
|-----------|-------------|----------------------|------------|
| Recién empieza (5% vendido, FINANCIAMIENTO) | $10.25 | $11.99 | +17% |
| Mitad vendido (50%, EJECUCIÓN) | $12.50 | $18.75 | +50% |
| Casi agotado (95%, FINALIZADO) | $14.75 | $25.81 | +75% |

---

## 12. Tabla de factores por estado

### Mapeo estado → factor rendimiento

| Estado en DB | Significado | Factor rendimiento | ¿Por qué este número? |
|-------------|-------------|-------------------|----------------------|
| `PREPARACION` | El creador está armando el proyecto, todavía no se financia | 0.00 | Riesgo total: el proyecto puede no arrancar nunca |
| `FINANCIAMIENTO` | Se está juntando plata. Hay inversores comprando tokens | 0.15 a 0.35 | Depende del progreso de recaudación. A más recaudado, menor riesgo |
| `EJECUCION` | El proyecto arrancó (ej: están construyendo) | 0.50 | Riesgo medio: el proyecto existe pero puede tener problemas |
| `FINALIZADO` | El proyecto se completó | 0.75 | Riesgo mínimo: el proyecto está terminado |
| `CANCELADO` | El proyecto se canceló | 0.00 | No hay proyecto, el token no debería tener valor |
| `RECHAZADO` | El proyecto fue rechazado | 0.00 | Idem |

### Fórmula para FINANCIAMIENTO

```
factor_rendimiento = 0.15 + (monto_recaudado / monto_requerido) × 0.20
```

Con límite máximo de **0.35**.

### Tabla de ejemplo para FINANCIAMIENTO

| % Recaudado | Cálculo | Factor | Efecto en precio (base $10, 50% vendido) |
|-------------|---------|--------|----------------------------------------|
| 0% | 0.15 + 0.00 | 0.15 | $10.00 × 1.25 × 1.15 = $14.38 |
| 25% | 0.15 + 0.05 | 0.20 | $10.00 × 1.25 × 1.20 = $15.00 |
| 50% | 0.15 + 0.10 | 0.25 | $10.00 × 1.25 × 1.25 = $15.63 |
| 75% | 0.15 + 0.15 | 0.30 | $10.00 × 1.25 × 1.30 = $16.25 |
| 100% | 0.15 + 0.20 | 0.35 | $10.00 × 1.25 × 1.35 = $16.88 |

---

## 13. Nuevo endpoint: GET /api/tokens/{proyectoId}/precio

### ¿Por qué un endpoint específico?

Antes de Stage 4, el precio dinámico solo se calculaba cuando alguien **validaba una inversión** o **invertía**. Si el frontend quería mostrar el precio actualizado en el listado de proyectos, no tenía dónde consultarlo.

Ahora hay un endpoint específico que devuelve el precio calculado **en el momento**, con los factores de demanda y rendimiento ya aplicados.

### El endpoint

```
GET /api/tokens/{proyectoId}/precio
```

### Respuesta

```json
{
  "proyectoId": 5,
  "precioActual": 18.75,
  "precioBase": 10.00,
  "suministroTotal": 10000,
  "cupoRestante": 5000,
  "vendidos": 5000,
  "factorDemanda": 0.25,
  "factorRendimiento": 0.50
}
```

### Campos de la respuesta

| Campo | Tipo | Significado |
|-------|------|-------------|
| `proyectoId` | Long | ID del proyecto |
| `precioActual` | BigDecimal | Precio final = base × (1 + demanda) × (1 + rendimiento) |
| `precioBase` | BigDecimal | Precio inicial que puso el creador |
| `suministroTotal` | int | Total de tokens del proyecto |
| `cupoRestante` | int | Cuántos tokens quedan sin vender |
| `vendidos` | int | suministroTotal - cupoRestante |
| `factorDemanda` | BigDecimal | demanda_relativa × factor_volatilidad (0.00 a 0.50) |
| `factorRendimiento` | BigDecimal | Según estado del proyecto (0.00 a 0.75) |

### Cómo se usa desde el frontend

Cuando el usuario entra a "Explorar proyectos" y ve la lista, el frontend puede:

**Opción A (recomendada):** llamar al endpoint para cada proyecto al renderizar la lista.

```javascript
// Ejemplo conceptual con fetch
async function obtenerPrecio(proyectoId) {
  const res = await fetch(`/api/tokens/${proyectoId}/precio`);
  return res.json();
}
```

**Opción B (mejor performance):** crear un endpoint batch que acepte múltiples IDs.

> Por ahora está la opción A. Si hay problemas de performance con muchos proyectos, se puede agregar un endpoint batch después.

### Archivos nuevos

| Archivo | Ruta | Propósito |
|---------|------|-----------|
| `SubtokenPriceResponse.java` | `src/main/java/com/systeam/tokenization/dto/` | DTO con todos los detalles del precio |
| `TokenizationController.java` | (modificado) | Nuevo método `getCurrentPrice()` |

### Código del controlador

```java
@GetMapping("/{proyectoId}/precio")
public SubtokenPriceResponse getCurrentPrice(@PathVariable Long proyectoId) {
    return subtokenService.obtenerPrecioConDetalle(proyectoId);
}
```

### Código del servicio

```java
public SubtokenPriceResponse obtenerPrecioConDetalle(Long proyectoId) {
    Map<String, Object> subtoken = findSubtokenByProject(proyectoId);
    if (subtoken == null) return null;

    BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
    int suministroTotal = (int) subtoken.get("suministro_total");
    int cupoRestante = (int) subtoken.get("cupo_restante");
    BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

    int vendidos = suministroTotal - cupoRestante;
    BigDecimal demandaRelativa = suministroTotal > 0
        ? BigDecimal.valueOf(vendidos).divide(BigDecimal.valueOf(suministroTotal), 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    BigDecimal factorDemanda = demandaRelativa.multiply(factorVolatilidad);

    BigDecimal factorRendimiento = obtenerFactorRendimiento(proyectoId);
    BigDecimal precio = calcularPrecio(precioBase, suministroTotal, cupoRestante,
        factorVolatilidad, proyectoId);

    return new SubtokenPriceResponse(proyectoId, precio, precioBase,
        suministroTotal, cupoRestante, factorDemanda, factorRendimiento);
}
```

### ¿Qué ventaja tiene?

| Situación | Antes | Ahora |
|-----------|-------|-------|
| Frontend muestra lista de proyectos | Precio fijo de la BD (desactualizado si cambió estado) | Precio calculado en el momento, siempre actualizado |
| Frontend muestra detalle de proyecto | No había precio visible sin pasar por `validate` | Llamada simple al endpoint |
| Audit trail de factores | No se podía ver cuánto influyó rendimiento vs demanda | El JSON muestra ambos factores |

---

## 14. Preguntas frecuentes

### ¿El precio puede bajar?

Depende:
- **Por oferta/demanda**: no, el precio solo sube a medida que se venden tokens (nunca se "devuelven" tokens al cupo).
- **Por rendimiento**: el estado del proyecto solo avanza (PREPARACIÓN → FINANCIAMIENTO → EJECUCIÓN → FINALIZADO). Nunca retrocede, así que el factor rendimiento nunca baja.

**El precio del sub-token nunca baja.** Solo sube o se mantiene.

### ¿Qué pasa si el proyecto se cancela?

Si un proyecto se cancela, el estado pasa a `CANCELADO` y el factor de rendimiento pasa a 0.00. El precio vuelve a depender solo de la oferta/demanda. Pero como el proyecto está cancelado, los inversores reciben refund (reembolso) y los tokens se queman.

### ¿Esto afecta el contrato en区块链?

No. El pricing dinámico es **off-chain** (se calcula en Java). El contrato en区块链 recibe el precio ya calculado. No se necesita modificar ningún `.sol`.

### ¿Qué datos necesita la fórmula?

Datos de **dos tablas**:

| Tabla | Columnas | Para qué |
|-------|----------|----------|
| `subtokens` | `precio_base`, `suministro_total`, `cupo_restante`, `factor_volatilidad` | Calcular oferta/demanda |
| `projects` | `estado`, `monto_recaudado`, `monto_requerido` | Calcular rendimiento |

### ¿El factor rendimiento puede ser mayor a 0.75?

No, está limitado. El máximo es 0.75 (estado FINALIZADO). Esto evita que el precio se dispare sin control.

### ¿Qué pasa si el proyecto está en PREPARACIÓN?

El factor es 0.00, así que `(1 + 0) = 1`, el precio se calcula solo por oferta/demanda. Esto está bien porque en PREPARACIÓN todavía no se venden tokens (el proyecto no está en FINANCIAMIENTO).

### ¿Dónde se ve este precio en el frontend?

Cuando un inversor usa `/api/investments/validate` antes de invertir, el precio devuelto ya incluye el factor rendimiento. El frontend solo tiene que mostrar el `precioSubtoken` que recibe.


## 4. Stage 5 � Marketplace de Sub-Tokens

# STAGE 5 — Marketplace de Sub-Tokens (HU-14)

## Índice

1. [¿Qué es un Marketplace?](#1-qué-es-un-marketplace)
2. [Arquitectura General](#2-arquitectura-general)
3. [MKT-03: Base de Datos — Tabla order_book](#3-mkt-03-base-de-datos--tabla-order_book)
4. [MKT-04: Smart Contract — IdeaMarketplace.sol](#4-mkt-04-smart-contract--ideamarketplacesol)
5. [MKT-05: Backend — Service + Controller + DTOs](#5-mkt-05-backend--service--controller--dtos)
6. [Flujo Completo: Vender y Comprar](#6-flujo-completo-vender-y-comprar)
7. [Errores Comunes y Soluciones](#7-errores-comunes-y-soluciones)
8. [Próximos Pasos](#8-próximos-pasos)

---

## 1. ¿Qué es un Marketplace?

### El problema

Imaginate que invertiste 100 $IDEA en el proyecto "EcoTech". Te dieron 50 sub-tokens como representación de tu participación. Pasa un mes y EcoTech va muy bien. Ahora querés **vender** tus sub-tokens porque:
- Necesitás plata
- Creés que el proyecto ya no va a subir más
- Encontraste otro proyecto mejor

Sin marketplace, tendrías que:
1. Contactar a cada persona que conozcas y preguntarles si quieren comprar
2. Negociar el precio de palabra
3. Hacer la transferencia manual

Eso es inviable. Necesitás un **mercado centralizado** donde:
- Los vendedores publiquen "Vendo X sub-tokens a Y $IDEA cada uno"
- Los compradores vean todas las ofertas disponibles
- El sistema maneje la transferencia automáticamente

### Analogía

```
Mercado de pulgas tradicional:

  Vendedor → pone un puesto con precios
  Comprador → camina, mira, y compra
  Dueño del mercado → cobra una comisión

Marketplace digital (nuestro sistema):

  Vendedor → POST /api/marketplace/listings  →  "Publico venta"
  Comprador → GET /api/marketplace/listings   →  "Veo lo que hay"
  Comprador → POST /api/marketplace/listings/5/buy  →  "Compro!"
  Sistema   → descuenta $IDEA, transfiere sub-tokens, cobra comisión
```

### Diferencia con Inversión (HU-11)

| Concepto | Inversión (Stage 3) | Marketplace (Stage 5) |
|----------|---------------------|----------------------|
| ¿A quién le compro? | Al proyecto (nuevos sub-tokens) | A otro usuario (usados) |
| ¿Quién recibe la plata? | El proyecto | El vendedor |
| ¿Cuándo? | Solo durante FINANCIAMIENTO | Siempre que haya una orden activa |
| Precio | Dinámico (oferta del proyecto) | El que pone el vendedor |
| ¿Nacen sub-tokens nuevos? | Sí (se mintean) | No (se transfieren) |

---

## 2. Arquitectura General

```
┌──────────────────────────────────────────────────────┐
│                    FRONTEND                           │
│  (React + Wagmi + MetaMask)                          │
└──────┬────────────────────────────┬──────────────────┘
       │ HTTP                        │ Web3
       ▼                             ▼
┌──────────────────┐       ┌────────────────────────┐
│   BACKEND (Java) │       │   BLOCKCHAIN           │
│                  │       │   (Base Sepolia)       │
│  MarketplaceController  │                        │
│  MarketplaceService     │    IdeaMarketplace.sol  │
│  IdeaMarketplaceService │    ├─ listTokens()      │
│  SubtokenService        │    ├─ buyTokens()       │
│  DynamicPricingService  │    ├─ cancelListing()   │
│                  │       │    └─ collectFees()    │
└──────┬──────────┘       └────────────────────────┘
       │ JDBC
       ▼
┌──────────────────┐
│   PostgreSQL     │
│                  │
│  order_book      │
│  portfolio_activos│
│  users (saldo)   │
│  subtokens       │
└──────────────────┘
```

### Estrategia: Híbrido Off-Chain + On-Chain

Usamos dos capas:

1. **Off-Chain (PostgreSQL):** Guardamos todas las órdenes de compra/venta, los portfolios, y los saldos. Esto es rápido, económico, y no requiere MetaMask.

2. **On-Chain (Ethereum/Base):** El contrato `IdeaMarketplace.sol` es la versión descentralizada. Se ejecuta cuando el usuario firma con MetaMask.

Nuestra implementación actual **prioriza off-chain** (no requiere que el usuario tenga MetaMask). El backend intenta llamar al contrato, pero si falla porque no está deployado, sigue funcionando con la DB sola.

---

## 3. MKT-03: Base de Datos — Tabla order_book

### ¿Qué problema resuelve?

Necesitamos guardar en la base de datos las órdenes de venta que publican los usuarios. Cada orden es un registro que dice:

> El usuario 5 quiere vender 50 sub-tokens del proyecto 3 a 10 $IDEA cada uno

### La tabla

```sql
CREATE TABLE IF NOT EXISTS order_book (
    id BIGSERIAL PRIMARY KEY,
    on_chain_id BIGINT UNIQUE,
    seller_id BIGINT NOT NULL REFERENCES users(id),
    subtoken_id BIGINT NOT NULL REFERENCES subtokens(id),
    cantidad BIGINT NOT NULL CHECK (cantidad > 0),
    cantidad_inicial BIGINT NOT NULL CHECK (cantidad_inicial > 0),
    precio_unitario DECIMAL(40,0) NOT NULL CHECK (precio_unitario > 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    tx_hash VARCHAR(66),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Columna por columna

| Columna | Tipo | ¿Qué guarda? |
|---------|------|-------------|
| `id` | BIGSERIAL | Número autoincremental (1, 2, 3...). Es la clave primaria |
| `on_chain_id` | BIGINT | ID de la orden en el contrato blockchain. Es `NULL` si no se pudo enviar a blockchain |
| `seller_id` | BIGINT | FK al usuario que vende. REFERENCES users(id) |
| `subtoken_id` | BIGINT | FK al sub-token que se vende. REFERENCES subtokens(id) |
| `cantidad` | BIGINT | Cuántos sub-tokens quedan disponibles para vender. Si alguien compra 10 de 50, acá queda 40 |
| `cantidad_inicial` | BIGINT | Cuántos sub-tokens se publicaron originalmente. No cambia nunca |
| `precio_unitario` | DECIMAL(40,0) | Precio de CADA sub-token en $IDEA. Ej: 10 significa 10 $IDEA por sub-token |
| `estado` | VARCHAR(20) | `ACTIVE` (activa), `EXECUTED` (vendida toda), `CANCELLED` (cancelada por el vendedor) |
| `tx_hash` | VARCHAR(66) | Hash de la transacción blockchain (si se ejecutó on-chain) |
| `created_at` | TIMESTAMP | Cuándo se creó |
| `updated_at` | TIMESTAMP | Cuándo se actualizó por última vez |

### Índices

```sql
CREATE INDEX idx_order_book_estado ON order_book(estado);
CREATE INDEX idx_order_book_seller ON order_book(seller_id);
CREATE INDEX idx_order_book_subtoken ON order_book(subtoken_id);
```

Los índices hacen que las búsquedas sean más rápidas. Sin índice, PostgreSQL leería toda la tabla para encontrar las activas. Con índice, salta directo a las filas que nos interesan.

### Estados de una orden

```
          ┌──────────┐
          │  ACTIVE  │  ← Se crea cuando el vendedor publica
          └────┬─────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌──────────┐    ┌───────────┐
│EXECUTED  │    │CANCELLED  │
│(vendida  │    │(cancelada │
│ total)   │    │ por dueño)│
└──────────┘    └───────────┘
```

---

## 4. MKT-04: Smart Contract — IdeaMarketplace.sol

### ¿Qué es un Smart Contract?

Un programa que vive en la blockchain. Es como un robot que:
- No se puede apagar
- Cualquiera puede ver su código
- Las reglas no se pueden cambiar
- Ejecuta automáticamente cuando alguien lo llama

### ¿Para qué lo necesitamos?

Para que la compra/venta sea **descentralizada**. En vez de confiar en nuestro servidor (que podría caerse o hackearse), la gente puede operar directo en la blockchain.

### El Contrato

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/EIP712.sol";

contract IdeaMarketplace is AccessControl, ReentrancyGuard, EIP712 {
```

**Línea 1-2:** Licencia MIT y versión de Solidity (0.8.20+, la que usamos).

**Línea 4-8:** Imports de OpenZeppelin (bibliotecas estándar de Ethereum):
- `IERC20`: interface para manejar tokens ERC-20 ($IDEA y sub-tokens)
- `AccessControl`: maneja roles (quién puede hacer qué)
- `ReentrancyGuard`: protege contra ataques de reentrancia
- `ECDSA` y `EIP712`: para firmar órdenes off-chain (no vamos a profundizar acá)

```solidity
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    IERC20 public immutable idea;
    uint256 public feeBps = 250;
```

**ADMIN_ROLE:** Define el rol de administrador.

**immutable idea:** Referencia al token $IDEA. `immutable` significa que se setea una vez en el constructor y no cambia más (como ponerle nombre a tu perro).

**feeBps = 250:** Comisión de la plataforma. 250 basis points = 2.5%. Cada vez que alguien compra, el contrato retiene 2.5% del total y se lo queda.

```solidity
    uint256 public listingCounter;
    mapping(uint256 => Listing) public listings;
```

**listingCounter:** Contador de órdenes. Empieza en 0. Cada vez que alguien crea una orden, suma 1.

**mapping listings:** Es como un diccionario. Guarda las órdenes. La clave es un número (el ID de la orden), el valor es la estructura Listing.

```solidity
    struct Listing {
        address seller;
        address subToken;
        uint256 amount;
        uint256 pricePerToken;
        bool active;
    }
```

**struct Listing:** Define qué datos tiene cada orden:
- `seller`: dirección del vendedor (0x123...)
- `subToken`: dirección del contrato del sub-token
- `amount`: cuántos sub-tokens se venden
- `pricePerToken`: precio por sub-token en $IDEA
- `active`: si la orden sigue activa (true/false)

### Función listTokens() — Publicar venta

```solidity
    function listTokens(address subToken, uint256 amount, uint256 pricePerToken) external nonReentrant {
        require(amount > 0, "MP: amount > 0");
        require(pricePerToken > 0, "MP: price > 0");
        require(IERC20(subToken).balanceOf(msg.sender) >= amount, "MP: insufficient balance");

        IERC20(subToken).transferFrom(msg.sender, address(this), amount);

        listingCounter++;
        listings[listingCounter] = Listing({
            seller: msg.sender,
            subToken: subToken,
            amount: amount,
            pricePerToken: pricePerToken,
            active: true
        });

        emit Listed(listingCounter, msg.sender, subToken, amount, pricePerToken);
    }
```

**¿Qué hace?**
1. Valida que amount > 0, price > 0, y que el vendedor tiene suficientes sub-tokens
2. Transfiere los sub-tokens del vendedor al contrato (los "bloquea")
3. Incrementa el contador
4. Guarda la orden en el mapping
5. Emite un evento (notificación que cualquiera puede escuchar)

### Función buyTokens() — Comprar

```solidity
    function buyTokens(uint256 listingId, uint256 amount) external nonReentrant {
        Listing storage listing = listings[listingId];
        require(listing.active, "MP: listing not active");
        require(amount <= listing.amount, "MP: not enough tokens");
        require(amount > 0, "MP: amount > 0");

        uint256 totalPrice = amount * listing.pricePerToken;
        _executeTrade(listing.seller, msg.sender, listing.subToken, amount, totalPrice);

        listing.amount -= amount;
        if (listing.amount == 0) {
            listing.active = false;
        }

        emit TradeExecuted(listingId, msg.sender, amount, totalPrice);
    }
```

**¿Qué hace?**
1. Busca la orden por su ID
2. Valida que esté activa y tenga suficientes tokens
3. Calcula el precio total (cantidad × precio unitario)
4. Ejecuta el intercambio: transfiere $IDEA del comprador al vendedor + fee, y sub-tokens al comprador
5. Resta la cantidad comprada de la orden
6. Si ya no quedan, marca la orden como inactiva

### La función privada _executeTrade()

```solidity
    function _executeTrade(address seller, address buyer, address subToken, uint256 amount, uint256 totalPrice) private {
        uint256 fee = (totalPrice * feeBps) / 10000;
        uint256 sellerProceeds = totalPrice - fee;

        require(idea.transferFrom(buyer, address(this), totalPrice),
            "MP: IDEA transfer failed");
        require(idea.transfer(seller, sellerProceeds),
            "MP: seller transfer failed");

        IERC20(subToken).transfer(buyer, amount);
    }
```

Este es el corazón del marketplace. Ocurre todo en una transacción atómica (o se hace todo o no se hace nada):

1. Calcula la comisión: si totalPrice = 100 $IDEA y feeBps = 250 → fee = (100 × 250) / 10000 = 2.5 $IDEA
2. El comprador paga 100 $IDEA al contrato
3. El contrato envía 97.5 $IDEA al vendedor (la plataforma retiene 2.5)
4. El contrato envía los sub-tokens al comprador

---

## 5. MKT-05: Backend — Service + Controller + DTOs

### Arquitectura de capas

```
┌────────────────────────────────────┐
│     MarketplaceController          │  ← Recibe HTTP, delega al Service
│  (@RestController)                  │
├────────────────────────────────────┤
│     MarketplaceService             │  ← Lógica de negocio
│  (@Service)                         │
├────────────────────────────────────┤
│     IdeaMarketplaceService         │  ← Conexión con blockchain
│  (Web3j)                            │
├────────────────────────────────────┤
│     SubtokenService                │  ← Manejo de portfolios
│  (JdbcTemplate)                     │
└────────────────────────────────────┘
```

### DTO: CreateListingRequest (lo que manda el frontend)

```java
public class CreateListingRequest {
    @NotNull
    private Long subtokenId;        // ID del sub-token a vender

    @NotNull @Positive
    private BigInteger cantidad;      // Cuántos sub-tokens vendo

    @NotNull @Positive
    private BigInteger precioUnitario;  // Precio de cada uno
}
```

**Validaciones:**
- `@NotNull`: no puede ser null
- `@Positive`: tiene que ser > 0

### DTO: ListingResponse (lo que devuelve el backend)

```java
public class ListingResponse {
    private Long id;
    private BigInteger onChainId;
    private Long sellerId;
    private String sellerName;
    private Long subtokenId;
    private String projectName;
    private BigInteger cantidad;
    private BigInteger cantidadInicial;
    private BigInteger precioUnitario;
    private BigInteger precioTotal;     // cantidad * precioUnitario (calculado en SQL)
    private String estado;
    private String txHash;
    private LocalDateTime createdAt;
}
```

### MarketplaceService: createListing() — Publicar venta

```java
@Transactional
public ListingResponse createListing(Long sellerId, CreateListingRequest request) {
```

**Paso 1: Validar que el vendedor existe**
```java
    Map<String, Object> seller = findUserOrThrow(sellerId);
```
Busca al usuario en la tabla `users`. Si no existe, lanza ResourceNotFoundException.

**Paso 2: Validar que el sub-token existe**
```java
    Map<String, Object> subtoken = findSubtokenOrThrow(request.getSubtokenId());
```
Busca el sub-token en la tabla `subtokens`. Necesitamos la dirección del contrato (para on-chain).

**Paso 3: Validar que el vendedor tiene suficientes sub-tokens en su portfolio**
```java
    BigInteger portfolioBalance = jdbc.queryForObject(
        "SELECT COALESCE(cantidad, 0) FROM portfolio_activos WHERE usuario_id = ? AND subtoken_id = ?",
        BigInteger.class, sellerId, request.getSubtokenId()
    );
    if (portfolioBalance == null || portfolioBalance.compareTo(request.getCantidad()) < 0) {
        throw new ConflictException("No tienes suficientes sub-tokens para vender");
    }
```
`COALESCE` devuelve 0 si el registro no existe (por si el usuario nunca tuvo ese sub-token).
`compareTo()` devuelve negativo si portfolioBalance < cantidad.

**Paso 4: Intentar registrar en blockchain (fallback si no está deployado)**
```java
    String txHash = null;
    try {
        String subtokenAddress = (String) subtoken.get("contract_address");
        if (subtokenAddress != null && !subtokenAddress.isBlank()) {
            txHash = ideaMarketplaceService.listTokens(subtokenAddress, request.getCantidad(), request.getPrecioUnitario());
        }
    } catch (Exception e) {
        log.warn("listTokens on-chain no disponible: {}", e.getMessage());
    }
```
Si el contrato está deployado, llama a `listTokens()` en la blockchain.
Si falla (contrato no deployado, red caída, etc.), sigue con txHash = null.

**Paso 5: Insertar la orden en la DB**
```java
    Long listingId = jdbc.queryForObject("""
        INSERT INTO order_book (seller_id, subtoken_id, cantidad, cantidad_inicial, precio_unitario, estado, tx_hash, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, NOW(), NOW())
        RETURNING id
        """, Long.class, sellerId, request.getSubtokenId(), request.getCantidad(), request.getCantidad(), request.getPrecioUnitario(), txHash);
```
`RETURNING id` es una feature de PostgreSQL que devuelve el ID generado automáticamente.

**Paso 6: Bloquear los sub-tokens en el portfolio del vendedor**
```java
    jdbc.update("""
        UPDATE portfolio_activos SET cantidad = cantidad - ?, updated_at = NOW()
        WHERE usuario_id = ? AND subtoken_id = ?
        """, request.getCantidad(), sellerId, request.getSubtokenId());
```
Sacamos los sub-tokens del portfolio del vendedor. Cuando alguien los compre, se los vamos a sumar al comprador. Si se cancela la orden, se los devolvemos al vendedor.

**¿Por qué bloqueamos los tokens?**
Para evitar que el vendedor publique una venta de 100 sub-tokens, y mientras tanto los gaste o los venda dos veces. Es como cuando ponés un producto en consignación: el producto ya no está en tu casa, está en la tienda.

### MarketplaceService: buyFromListing() — Comprar

```java
@Transactional
public ListingResponse buyFromListing(Long buyerId, Long listingId, BigInteger cantidad) {
```

**Paso 1: Validar la orden**
```java
    Map<String, Object> listing = findListingOrThrow(listingId);
    if (!"ACTIVE".equals(listing.get("estado"))) {
        throw new ConflictException("La orden ya no esta activa");
    }
    Long sellerId = ((Number) listing.get("seller_id")).longValue();
    if (sellerId.equals(buyerId)) {
        throw new ConflictException("No puedes comprar tus propios sub-tokens");
    }
    BigInteger available = (BigInteger) listing.get("cantidad");
    if (cantidad.compareTo(available) > 0) {
        throw new ConflictException("La cantidad solicitada excede la disponible");
    }
```
Validaciones: que la orden esté activa, que no te compres a vos mismo, que haya suficiente cantidad.

**Paso 2: Validar saldo del comprador**
```java
    Map<String, Object> buyer = findUserOrThrow(buyerId);
    BigDecimal buyerBalance = (BigDecimal) buyer.get("saldo_idea");
    BigDecimal totalPriceBD = new BigDecimal(totalPrice);
    if (buyerBalance.compareTo(totalPriceBD) < 0) {
        throw new ConflictException("Saldo insuficiente de tokens IDEA");
    }
```
El comprador necesita tener al menos `cantidad × precioUnitario` $IDEA en su cuenta.

**Paso 3: Transferir $IDEA**
```java
    jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?", totalPriceBD, buyerId);
    jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?", totalPriceBD, sellerId);
```
Descontamos del comprador, acreditamos al vendedor. Todo en la misma transacción (por eso `@Transactional`).

**Paso 4: Transferir sub-tokens al comprador**
```java
    subtokenService.addPortfolioEntry(buyerId, subtokenId, cantidad.intValue());
```
El `addPortfolioEntry` hace un UPSERT (INSERT ... ON CONFLICT DO UPDATE):
- Si el comprador nunca tuvo ese sub-token, crea un registro con esa cantidad
- Si ya tenía, suma la cantidad a lo que tenía

**Paso 5: Actualizar la orden**
```java
    BigInteger newCantidad = available.subtract(cantidad);
    if (newCantidad.compareTo(BigInteger.ZERO) == 0) {
        jdbc.update("UPDATE order_book SET cantidad = 0, estado = 'EXECUTED', updated_at = NOW() WHERE id = ?", listingId);
    } else {
        jdbc.update("UPDATE order_book SET cantidad = ?, updated_at = NOW() WHERE id = ?", newCantidad, listingId);
    }
```
Si se vendió todo, la orden pasa a EXECUTED. Si solo una parte, se actualiza la cantidad restante.

**Paso 6: Ejecutar en blockchain si es posible**
```java
    try {
        ideaMarketplaceService.buyTokens(BigInteger.valueOf(listingId), cantidad);
    } catch (Exception e) {
        log.warn("buyTokens on-chain no disponible: {}", e.getMessage());
    }
```

### MarketplaceService: cancelListing() — Cancelar venta

```java
@Transactional
public void cancelListing(Long userId, Long listingId) {
    Map<String, Object> listing = findListingOrThrow(listingId);

    if (!userId.equals(((Number) listing.get("seller_id")).longValue())) {
        throw new ConflictException("Esta orden no te pertenece");
    }
    if (!"ACTIVE".equals(listing.get("estado"))) {
        throw new ConflictException("La orden ya no esta activa");
    }

    BigInteger remaining = (BigInteger) listing.get("cantidad");
    Long subtokenId = ((Number) listing.get("subtoken_id")).longValue();

    jdbc.update("UPDATE order_book SET estado = 'CANCELLED', updated_at = NOW() WHERE id = ?", listingId);
    subtokenService.addPortfolioEntry(userId, subtokenId, remaining.intValue());
}
```

**¿Qué hace?**
1. Verifica que la orden sea del usuario que la quiere cancelar
2. Verifica que esté activa
3. Cambia el estado a CANCELLED
4. Devuelve los sub-tokens bloqueados al portfolio del vendedor

### MarketplaceController — Los endpoints REST

```java
@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {
```

**Endpoints públicos (no requieren autenticación):**

| Endpoint | Método | ¿Qué hace? |
|----------|--------|------------|
| `GET /api/marketplace/listings` | GET | Lista todas las órdenes activas (paginado) |
| `GET /api/marketplace/listings/{id}` | GET | Detalle de una orden específica |
| `GET /api/marketplace/listings/by-subtoken/{subtokenId}` | GET | Órdenes activas para un sub-token específico |
| `GET /api/marketplace/quote?proyectoId=X` | GET | Precio actual del sub-token (cotización) |

**Endpoints autenticados (requieren JWT):**

| Endpoint | Método | ¿Qué hace? |
|----------|--------|------------|
| `POST /api/marketplace/listings` | POST | Crear una nueva orden de venta |
| `POST /api/marketplace/listings/{id}/buy?cantidad=10` | POST | Comprar sub-tokens de una orden |
| `POST /api/marketplace/listings/{id}/cancel` | POST | Cancelar una orden propia |

### El RowMapper — Cómo se transforma SQL a Java

```java
private static class ListingRowMapper implements RowMapper<ListingResponse> {
    @Override
    public ListingResponse mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        ListingResponse r = new ListingResponse();
        r.setId(rs.getLong("id"));
        r.setSellerId(rs.getLong("seller_id"));
        r.setSellerName(rs.getString("seller_name"));
        r.setProjectName(rs.getString("project_name"));
        r.setCantidad(BigInteger.valueOf(rs.getLong("cantidad")));
        r.setPrecioUnitario(BigInteger.valueOf(rs.getLong("precio_unitario")));
        r.setPrecioTotal(BigInteger.valueOf(rs.getLong("precio_total")));
        r.setEstado(rs.getString("estado"));
        r.setTxHash(rs.getString("tx_hash"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
        return r;
    }
}
```

**¿Qué es un RowMapper?** Spring JDBC usa esto para convertir cada fila del resultado SQL en un objeto Java. `mapRow` se llama una vez por cada fila que devuelve la consulta.

**El JOIN en la query:**
```sql
SELECT ob.id, ob.cantidad, ob.precio_unitario,
       u.nombre AS seller_name,
       p.titulo AS project_name,
       (ob.cantidad * ob.precio_unitario) AS precio_total
FROM order_book ob
JOIN users u ON u.id = ob.seller_id
JOIN subtokens s ON s.id = ob.subtoken_id
JOIN projects p ON p.id = s.proyecto_id
```

Hacemos JOIN de 4 tablas para obtener:
- Datos de la orden (order_book)
- Nombre del vendedor (users)
- Nombre del proyecto (projects) a través del sub-token (subtokens)

---

## 6. Flujo Completo: Vender y Comprar

### Escenario

- Alice tiene 100 sub-tokens de "EcoTech"
- Alice quiere vender 50 a 8 $IDEA cada una
- Bob quiere comprar 20 de esas

### Vendedor: Alice publica su venta

```
POST /api/marketplace/listings
Authorization: Bearer <jwt_de_alice>
Body: { "subtokenId": 3, "cantidad": 50, "precioUnitario": 8 }
```

**Qué pasa adentro del backend:**

```
1. MarketplaceController recibe el POST
2. Verifica que Alice está autenticada (JWT válido)
3. Llama a MarketplaceService.createListing(userId=5, request)
   ├─ Busca al usuario 5 (Alice) → existe
   ├─ Busca sub-token 3 (EcoTech) → existe
   ├─ Verifica portfolio: Alice tiene 100 → OK
   ├─ Intenta IdeaMarketplaceService.listTokens() → falla (contrato no deployado) → txHash=null
   ├─ INSERT INTO order_book (seller_id=5, subtoken_id=3, cantidad=50, ...) → id=7
   ├─ UPDATE portfolio_activos SET cantidad = 100 - 50 = 50 WHERE usuario_id=5
   └─ Devuelve ListingResponse
```

**Estado final:**
- Portfolio de Alice: 50 sub-tokens de EcoTech (los otros 50 están bloqueados en la orden)
- order_book: id=7, seller=Alice, subtoken=EcoTech, cantidad=50, precio=8, estado=ACTIVE

### Comprador: Bob ve las órdenes activas

```
GET /api/marketplace/listings
```

**Respuesta:**
```json
{
  "content": [
    {
      "id": 7,
      "sellerId": 5,
      "sellerName": "Alice",
      "projectName": "EcoTech",
      "cantidad": 50,
      "precioUnitario": 8,
      "precioTotal": 400,
      "estado": "ACTIVE"
    }
  ]
}
```

### Comprador: Bob compra 20 sub-tokens

```
POST /api/marketplace/listings/7/buy?cantidad=20
Authorization: Bearer <jwt_de_bob>
```

**Qué pasa adentro:**

```
1. MarketplaceController recibe el POST
2. Verifica que Bob está autenticado
3. Llama a MarketplaceService.buyFromListing(userId=8, listingId=7, cantidad=20)
   ├─ Busca la orden 7 → ACTIVE, seller=Alice(5), cantidad=50, precio=8
   ├─ Verifica que Bob(8) ≠ Alice(5) → OK
   ├─ Verifica que 20 ≤ 50 → OK
   ├─ Calcula: totalPrice = 20 × 8 = 160 $IDEA
   ├─ Busca a Bob → saldo_idea = 500 → 500 ≥ 160 → OK
   ├─ UPDATE saldo_idea: Bob -160 = 340, Alice +160 = 240
   ├─ addPortfolioEntry(Bob, subtoken=3, cantidad=20)
   │  └─ Bob tenía 0 → ahora tiene 20
   ├─ UPDATE order_book: cantidad = 50-20 = 30
   ├─ Intenta buyTokens on-chain → falla → log
   └─ Devuelve ListingResponse (cantidad=30, estado=ACTIVE)
```

**Estado final:**
- Portfolio Bob: 20 sub-tokens de EcoTech
- Portfolio Alice: 50 sub-tokens (los que no vendió, +160 $IDEA)
- Orden 7: cantidad=30, sigue ACTIVE (quedan 30 por vender)

### Diagrama de secuencia

```
Alice                    Backend                    DB                       Blockchain
  │                        │                        │                          │
  │  POST /listings        │                        │                          │
  │───────────────────────>│                        │                          │
  │                        │  Validar usuario        │                          │
  │                        │  Validar subtoken       │                          │
  │                        │  Verificar portfolio    │                          │
  │                        │───────────────────────>│                          │
  │                        │<───────────────────────│                          │
  │                        │                        │                          │
  │                        │  Intentar listTokens()  │                          │
  │                        │───────────────────────────────────────────────────>│
  │                        │<────────────────────────────────────────────────────│ (falla = sigue igual)
  │                        │                        │                          │
  │                        │  INSERT order_book      │                          │
  │                        │───────────────────────>│                          │
  │                        │  UPDATE portfolio       │                          │
  │                        │───────────────────────>│                          │
  │  ListingResponse       │                        │                          │
  │<───────────────────────│                        │                          │
  │                        │                        │                          │
Bob                        │                        │                          │
  │  GET /listings         │                        │                          │
  │───────────────────────>│                        │                          │
  │                        │  SELECT * FROM order_book                          │
  │                        │───────────────────────>│                          │
  │<─── Lista de órdenes ──│<───────────────────────│                          │
  │                        │                        │                          │
  │  POST /listings/7/buy  │                        │                          │
  │───────────────────────>│                        │                          │
  │                        │  Validar orden + saldo │                          │
  │                        │───────────────────────>│                          │
  │                        │<───────────────────────│                          │
  │                        │                        │                          │
  │                        │  UPDATE saldo_idea     │                          │
  │                        │  (Bob -160, Alice +160)│                          │
  │                        │───────────────────────>│                          │
  │                        │                        │                          │
  │                        │  UPSERT portfolio      │                          │
  │                        │  (Bob +20 subtokens)   │                          │
  │                        │───────────────────────>│                          │
  │                        │                        │                          │
  │                        │  UPDATE order_book     │                          │
  │                        │  (cantidad 50→30)      │                          │
  │                        │───────────────────────>│                          │
  │                        │                        │                          │
  │  ListingResponse       │                        │                          │
  │<───────────────────────│                        │                          │
```

---

## 7. Errores Comunes y Soluciones

### Error 1: "No tienes suficientes sub-tokens para vender"

```
HTTP 409 Conflict
Cuerpo: "No tienes suficientes sub-tokens para vender"
```

**Causa:** El usuario quiere vender más sub-tokens de los que tiene en su portfolio. O no tiene ninguno, o tiene menos de los que intenta vender.

**Solución:** Verificar el portfolio del usuario en `portfolio_activos`. Recordar que los sub-tokens ya puestos en venta están bloqueados y no aparecen en el portfolio.

### Error 2: "La orden ya no esta activa"

```
HTTP 409 Conflict
Cuerpo: "La orden ya no esta activa"
```

**Causa:** Alguien intenta comprar o cancelar una orden que ya fue ejecutada o cancelada.

**Solución:** Refrescar la lista de órdenes activas (GET /api/marketplace/listings) antes de operar.

### Error 3: "No puedes comprar tus propios sub-tokens"

```
HTTP 409 Conflict
Cuerpo: "No puedes comprar tus propios sub-tokens"
```

**Causa:** El comprador es el mismo que el vendedor. Técnicamente podría comprarse a sí mismo, pero no tiene sentido.

### Error 4: "Saldo insuficiente de tokens IDEA"

```
HTTP 409 Conflict
Cuerpo: "Saldo insuficiente de tokens IDEA"
```

**Causa:** El comprador no tiene suficientes $IDEA para pagar `cantidad × precioUnitario`.

**Solución:** Verificar el saldo en `users.saldo_idea`. Si necesita más, puede comprar $IDEA (cuando implementemos la pasarela de pago).

### Error 5: Autenticación requerida

```
HTTP 403 Forbidden
Cuerpo: "Access Denied"
```

**Causa:** El endpoint requiere JWT y no se envió, o el token expiró.

**Solución:** Enviar `Authorization: Bearer <token>` en el header.

### Error 6: Error de transacción (todo se revierte)

```
HTTP 500 Internal Server Error
```

**Causa:** Un error inesperado en medio de una operación. Ejemplo: la DB se cae, un constraint violado.

**Protección:** Usamos `@Transactional`. Si algo falla, Spring revierte automáticamente todos los cambios (no se descuenta $IDEA sin acreditar, no se transfieren sub-tokens a medias).

---

## 8. Próximos Pasos

### Pendiente: Frontend (MKT-06)

El frontend de Marketplace debería tener:

1. **OrderBookPage:** Lista de órdenes activas con filtros por proyecto
2. **CreateListingModal:** Formulario para publicar una venta
3. **BuyButton:** Botón para comprar sub-tokens de una orden
4. **MyListings:** Lista de mis órdenes (para cancelar)

### Pendiente: Deploy del contrato en Base Sepolia

Cuando esté deployado, actualizar en `.env`:
```
IDEA_MARKETPLACE_ADDRESS=0xnuevadireccion
```

El `IdeaMarketplaceService` automáticamente empezará a registrar órdenes on-chain.

### Pendiente: Comisión de plataforma

Hoy no cobramos comisión en el backend off-chain (el contrato sí cobra 2.5%). Para implementar:
1. Crear una cuenta de `platform_fees`
2. Cuando se ejecuta una compra, retener el 2.5% del total
3. Acumularlo en la cuenta de plataforma
4. Endpoint `POST /api/admin/marketplace/collect-fees` para retirar

---

## Resumen rápido

| Concepto | Explicación |
|----------|-------------|
| Marketplace | Mercado secundario para comprar/vender sub-tokens entre usuarios |
| MKT-03 | Tabla `order_book` en PostgreSQL que guarda las órdenes de venta |
| MKT-04 | Contrato `IdeaMarketplace.sol` para intercambio descentralizado on-chain |
| MKT-05 | API REST con 6 endpoints para operar el marketplace |
| Bloqueo | Al publicar una venta, los sub-tokens se bloquean del portfolio del vendedor |
| Híbrido | El backend intenta blockchain, pero funciona solo con DB si no está disponible |
| Transaccional | Todo se ejecuta en una transacción: si algo falla, nada se modifica |


## 5. Stage 6 � Governance REST API

# STAGE 6 — Governance REST API

## Manual completo para principiantes absolutos

> 📖 **¿Qué es esto?**
> Este documento es una **clase escrita** para que cualquier persona del equipo,
> incluso si nunca vio el código ni sabe qué es un contrato inteligente, pueda
> entender **todo** lo que se hizo en el módulo de Gobernanza, **por qué** se hizo,
> **cómo** funciona cada pieza, **qué archivos** crear y **qué código** poner.
>
> Si venís de 0, empezá por la Introducción. Si ya sabés algo, saltá a la sección
> que te interese.

---

## Tabla de contenidos

1. [Introducción: ¿Qué es la gobernanza y por qué la necesitamos?](#1-introducción)
2. [Historias de Usuario (HU) que cubrimos](#2-historias-de-usuario)
3. [Arquitectura general: el modelo Two-Tier](#3-arquitectura-general)
4. [Paso 1: El Contrato Inteligente (IdeaGovernance.sol)](#4-paso-1-contrato-inteligente-ideagovernancesol)
5. [Paso 2: La Base de Datos (Flyway V18)](#5-paso-2-base-de-datos-flyway-v18)
6. [Paso 3: El Servicio Blockchain (IdeaGovernanceService.java)](#6-paso-3-servicio-blockchain-ideagovernanceservicejava)
7. [Paso 4: El Servicio Off-Chain (GovernanceService.java)](#7-paso-4-servicio-off-chain-governanceservicejava)
8. [Paso 5: Los DTOs (CreateProposalRequest, VoteRequest, ProposalResponse)](#8-paso-5-los-dtos)
9. [Paso 6: El Controlador REST (GovernanceController.java)](#9-paso-6-controlador-rest-governancecontrollerjava)
10. [Flujo completo paso a paso](#10-flujo-completo-paso-a-paso)
11. [Errores comunes y cómo solucionarlos](#11-errores-comunes)
12. [Próximos pasos](#12-próximos-pasos)

---

## 1. Introducción

### ¿Qué es "gobernanza" en blockchain?

Imaginá que un grupo de personas invierte en proyectos a través de la plataforma
IDEAFY. Llega un momento en que hay que tomar decisiones colectivas:

- ¿Aprobamos un nuevo proyecto para que reciba financiamiento?
- ¿Cambiamos algún parámetro del sistema (ej: comisión)?
- ¿Usamos fondos de la tesorería para algo?

En un sistema tradicional, estas decisiones las tomaría un administrador con una
base de datos. Pero en blockchain **no hay un administrador central**: las
decisiones las toman los poseedores del token **$IDEA** votando.

**Eso es la gobernanza**: un sistema donde los usuarios proponen cambios y votan,
y el peso del voto de cada persona depende de cuántos tokens $IDEA tiene.

### ¿Qué hace este módulo?

Este módulo implementa un **sistema de gobernanza** con dos capas:

| Capa | ¿Qué hace? | Tecnología |
|------|-----------|------------|
| **On-chain** (en blockchain) | Guarda las propuestas, los votos y la ejecución. Es la verdad inmutable: nadie puede mentir sobre quién votó qué. | Contrato `IdeaGovernance.sol` en Base Sepolia |
| **Off-chain** (en nuestra BD) | Guarda una copia para poder hacer consultas rápidas (listar propuestas, buscar, etc.). Leer de blockchain es lento y caro. | PostgreSQL + Flyway |

### ¿Por qué dos capas? (explicación simple)

Leer datos de blockchain es como ir a un archivo nacional cada vez que querés
saber algo: podés hacerlo, pero es lento y cada viaje cuesta plata (gas fees).

En cambio, leer de PostgreSQL es como tener una fotocopia del archivo en tu
escritorio: mucho más rápido y gratis.

Por eso **guardamos la propuesta en ambos lados**:
1. En blockchain (para que sea inmutable y transparente)
2. En PostgreSQL (para poder listar/buscar rápido)

---

## 2. Historias de Usuario

### HU-01: Crear una propuesta de gobernanza

> **Como** usuario con más de 1000 tokens $IDEA
> **Quiero** crear una propuesta de gobernanza con título, descripción y tipo
> **Para** que la comunidad pueda votar si la aprueba o no

**Criterios de aceptación:**
- La propuesta se guarda en la blockchain (inmutable)
- La propuesta se guarda en PostgreSQL (consulta rápida)
- Solo usuarios con ≥ 1000 $IDEA pueden crear propuestas (lo exige el contrato)
- Se devuelve el detalle de la propuesta creada + el hash de la transacción

**Archivos involucrados:**
- `IdeaGovernance.sol` → función `createProposal()`
- `IdeaGovernanceService.java` → método `createProposal()`
- `GovernanceService.java` → método `createProposalOffChain()`
- `GovernanceController.java` → `POST /api/governance/proposals`
- `CreateProposalRequest.java` → DTO de entrada
- `ProposalResponse.java` → DTO de salida

### HU-02: Votar una propuesta

> **Como** usuario con tokens $IDEA
> **Quiero** votar a favor o en contra de una propuesta activa
> **Para** influir en las decisiones de la plataforma

**Criterios de aceptación:**
- Cada wallet solo puede votar una vez por propuesta
- El peso del voto es la cantidad de $IDEA que tiene la wallet
- No se puede votar antes del inicio ni después del fin del período
- Se devuelve el hash de la transacción

**Archivos involucrados:**
- `IdeaGovernance.sol` → función `vote()`
- `IdeaGovernanceService.java` → método `vote()`
- `GovernanceController.java` → `POST /api/governance/vote`
- `VoteRequest.java` → DTO de entrada

### HU-03: Ejecutar una propuesta aprobada

> **Como** usuario con rol EXECUTOR
> **Quiero** ejecutar una propuesta que alcanzó quorum y fue aprobada
> **Para** que el cambio propuesto se aplique en la blockchain

**Criterios de aceptación:**
- Solo puede ejecutar quien tenga el rol EXECUTOR_ROLE en el contrato
- La propuesta debe haber terminado su período de votación
- Debe haber alcanzado quorum (≥ 10% del total supply de $IDEA votó)
- Los votos a favor deben superar a los votos en contra
- No se puede ejecutar dos veces
- Se actualiza el estado en PostgreSQL

**Archivos involucrados:**
- `IdeaGovernance.sol` → función `executeProposal()`
- `IdeaGovernanceService.java` → método `executeProposal()`
- `GovernanceService.java` → método `markExecuted()`
- `GovernanceController.java` → `POST /api/governance/proposals/{id}/execute`

### HU-04: Listar propuestas

> **Como** usuario de la plataforma
> **Quiero** ver todas las propuestas con su estado y resultados
> **Para** estar informado de las decisiones que se están tomando

**Criterios de aceptación:**
- Listado paginado
- Filtro opcional por estado (ACTIVE, EXECUTED)
- Ordenado por fecha de creación descendente
- Muestra nombre del proponente (joineado de la tabla users)

**Archivos involucrados:**
- `GovernanceService.java` → método `listProposals()`
- `GovernanceController.java` → `GET /api/governance/proposals`
- `ProposalResponse.java` → DTO de salida

### HU-05: Ver detalle de una propuesta

> **Como** usuario de la plataforma
> **Quiero** ver el detalle completo de una propuesta específica
> **Para** decidir mi voto informado

**Criterios de aceptación:**
- Muestra todos los campos: título, descripción, tipo, estado, votos, fechas, tx hash
- Muestra el nombre del usuario que propuso

**Archivos involucrados:**
- `GovernanceService.java` → método `getProposalById()`
- `GovernanceController.java` → `GET /api/governance/proposals/{id}`

---

## 3. Arquitectura General

### Diagrama de capas

```
┌─────────────────────────────────────────────────────────────────────┐
│                        FRONTEND (React)                             │
│              (No implementado aún en este módulo)                    │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  HTTP (JSON)
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   GOVERNANCE CONTROLLER                              │
│              GovernanceController.java                               │
│              Expone endpoints REST                                   │
│              Lee el usuario del JWT (JwtPrincipal)                   │
│              Delega en los servicios                                 │
└──────────┬──────────────────────────────────┬───────────────────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────┐       ┌──────────────────────────┐
│ IDEA GOVERNANCE      │       │ GOVERNANCE SERVICE        │
│ SERVICE (on-chain)   │       │ (off-chain / DB)          │
│ IdeaGovernanceService│       │ GovernanceService.java    │
│ .java                │       │                           │
│                      │       │ JdbcTemplate → PostgreSQL │
│ Web3j +              │       │ CRUD de proposals         │
│ RawTransactionManager│       │ Paginación, filtros       │
│ ethCall / sendTx     │       │                           │
└──────────┬───────────┘       └──────────┬────────────────┘
           │                               │
           ▼                               ▼
┌──────────────────────┐       ┌──────────────────────────┐
│ Base Sepolia         │       │ PostgreSQL                │
│ IdeaGovernance.sol   │       │ Tabla: proposals          │
│                      │       │                           │
│ Funciones:           │       │ Columnas:                 │
│ - createProposal     │       │ id, on_chain_id,          │
│ - vote               │       │ proposer_user_id,         │
│ - executeProposal    │       │ title, description,       │
│ - getProposal        │       │ status, for_votes,        │
│ - proposalCounter    │       │ against_votes, tx_hash    │
└──────────────────────┘       └──────────────────────────┘
```

### ¿Qué es cada cosa?

| Componente | ¿Qué es? | Analogía |
|-----------|---------|----------|
| **Contrato inteligente** | Código que vive en la blockchain, no se puede modificar | Un libro de actas público donde cada página está sellada |
| **Web3j** | Biblioteca de Java para hablar con blockchain | Un teléfono para llamar al libro de actas |
| **RawTransactionManager** | Objeto de Web3j que firma y envía transacciones | Un escribano que firma las páginas del libro |
| **Credentials** | La clave privada de nuestra wallet | La firma digital de nuestra organización |
| **JdbcTemplate** | Clase de Spring para hacer consultas SQL | Un asistente que busca en nuestros archivos locales |
| **Flyway** | Herramienta que crea y actualiza tablas automáticamente | Un constructor que arma los muebles cuando arranca la app |

### Flujo de datos para cada operación

#### Crear propuesta
```
Frontend → POST /api/governance/proposals
  → GovernanceController
    → 1. IdeaGovernanceService.createProposal()  [ESCRIBE en blockchain]
    → 2. GovernanceService.createProposalOffChain() [GUARDA en PostgreSQL]
  → Devuelve ProposalResponse (con datos + txHash)
```

#### Votar
```
Frontend → POST /api/governance/vote
  → GovernanceController
    → 1. IdeaGovernanceService.vote()  [ESCRIBE en blockchain]
  → Devuelve txHash (string)
```

#### Ejecutar propuesta
```
Frontend → POST /api/governance/proposals/{id}/execute
  → GovernanceController
    → 1. IdeaGovernanceService.executeProposal()  [ESCRIBE en blockchain]
    → 2. GovernanceService.markExecuted()  [ACTUALIZA PostgreSQL]
  → Devuelve txHash (string)
```

#### Listar propuestas
```
Frontend → GET /api/governance/proposals?status=ACTIVE&page=0&size=20
  → GovernanceController
    → 1. GovernanceService.listProposals()  [LEE de PostgreSQL]
  → Devuelve List<ProposalResponse>
```

#### Ver detalle
```
Frontend → GET /api/governance/proposals/{id}
  → GovernanceController
    → 1. GovernanceService.getProposalById()  [LEE de PostgreSQL]
  → Devuelve ProposalResponse
```

---

## 4. Paso 1: Contrato Inteligente (IdeaGovernance.sol)

### ¿Qué es un contrato inteligente?

Un **contrato inteligente** (smart contract) es un programa que vive dentro de
la blockchain. Una vez desplegado:

- **No se puede modificar** (es inmutable)
- **Cualquiera puede ver su código** (es transparente)
- **Se ejecuta automáticamente** cuando alguien envía una transacción
- **Cada ejecución cuesta plata** (gas fee)

Pensalo como una máquina expendedora: vos ponés plata (gas), apretás un botón
(llamás a una función), y la máquina hace lo que tiene que hacer. Nadie puede
abrir la máquina y robarse las monedas.

### Archivo: `blockchain/contracts/IdeaGovernance.sol`

Vamos línea por línea:

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;
```

**Línea 1**: Licencia del código (MIT = permisiva, cualquiera puede usarlo).
**Línea 2**: Versión de Solidity (el lenguaje de los contratos). `^0.8.20`
significa "compilar con cualquier versión 0.8.x donde x ≥ 20".

```solidity
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
```

**Importamos** dos cosas de OpenZeppelin (una biblioteca de contratos
probados y seguros):
- `IERC20`: La interfaz del token $IDEA. La necesitamos para consultar
  balances (`balanceOf`) y el total de tokens en circulación (`totalSupply`).
- `AccessControl`: Un sistema de roles. Nos permite decir "solo las personas
  con el rol EXECUTOR pueden ejecutar propuestas".

```solidity
contract IdeaGovernance is AccessControl {
```

**Declaramos** el contrato. `is AccessControl` significa "hereda todo el
sistema de roles de OpenZeppelin".

```solidity
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant EXECUTOR_ROLE = keccak256("EXECUTOR_ROLE");
```

**Definimos dos roles:**
- `ADMIN_ROLE`: Puede asignar y quitar roles a otros (el admin del sistema)
- `EXECUTOR_ROLE`: Puede ejecutar propuestas aprobadas

`bytes32` es un tipo de dato de 32 bytes. `keccak256` es una función hash
que convierte un texto en un identificador único. `public constant` significa
que cualquiera puede leer estos valores y no cambian nunca.

```solidity
    IERC20 public immutable idea;
```

**Guardamos** una referencia al token $IDEA. `immutable` significa que se
asigna una sola vez (en el constructor) y después no cambia. Esto ahorra gas.

```solidity
    uint256 public constant PROPOSAL_THRESHOLD = 1000e18;
    uint256 public constant QUORUM_BPS = 1000;
    uint256 public constant VOTING_PERIOD = 7 days;
```

**Tres constantes del sistema:**
- `PROPOSAL_THRESHOLD = 1000e18`: Para crear una propuesta necesitás tener
  al menos 1000 tokens $IDEA (el `e18` es porque los tokens tienen 18 decimales)
- `QUORUM_BPS = 1000`: El quorum es 10% del total supply (`1000` de `10000` BPS)
- `VOTING_PERIOD = 7 days`: Las votaciones duran 7 días desde que se crea
  la propuesta

```solidity
    enum ProposalType { ProjectApproval, ParameterChange, TreasuryAction }
```

**Un enum** (como una lista de opciones): una propuesta puede ser de tipo:
- `ProjectApproval` (0): Aprobar un proyecto nuevo
- `ParameterChange` (1): Cambiar un parámetro del sistema
- `TreasuryAction` (2): Usar fondos de la tesorería

```solidity
    struct Proposal {
        uint256 id;
        address proposer;
        string description;
        ProposalType proposalType;
        bytes data;
        uint256 startTime;
        uint256 endTime;
        uint256 forVotes;
        uint256 againstVotes;
        bool executed;
    }
```

**Un struct** es como un "formulario" o "registro". Cada propuesta tiene:

| Campo | Tipo | ¿Qué guarda? |
|-------|------|-------------|
| `id` | `uint256` | Número de propuesta (1, 2, 3...) |
| `proposer` | `address` | Dirección de la wallet que creó la propuesta |
| `description` | `string` | Descripción textual de la propuesta |
| `proposalType` | `ProposalType` | Tipo: 0=ProjectApproval, 1=ParameterChange, 2=TreasuryAction |
| `data` | `bytes` | Datos opcionales para ejecutar si se aprueba |
| `startTime` | `uint256` | Cuándo empieza la votación (timestamp UNIX) |
| `endTime` | `uint256` | Cuándo termina la votación (timestamp UNIX) |
| `forVotes` | `uint256` | Total de votos a favor (en tokens $IDEA) |
| `againstVotes` | `uint256` | Total de votos en contra (en tokens $IDEA) |
| `executed` | `bool` | Si ya fue ejecutada o no |

```solidity
    uint256 public proposalCounter;
    mapping(uint256 => Proposal) public proposals;
    mapping(uint256 => mapping(address => bool)) public hasVoted;
```

**Tres variables de estado** (se guardan en la blockchain):

1. `proposalCounter`: Un contador que va aumentando. Es la próxima ID
   disponible para una propuesta.

2. `proposals`: Un **mapping** (como un diccionario o HashMap). Asocia un
   número de propuesta (`uint256`) con los datos de la propuesta (`Proposal`).

   > **IMPORTANTE**: Como `proposals` es `public`, Solidity genera
   > automáticamente un getter `proposals(uint256 id)` que devuelve el struct
   > completo. No necesitamos escribir una función para leer propuestas
   > individuales.

3. `hasVoted`: Un mapping de dos niveles. `hasVoted[proposalId][walletAddress]`
   nos dice si una wallet ya votó en una propuesta específica.

### Eventos

```solidity
    event ProposalCreated(uint256 indexed id, address indexed proposer, string description, ProposalType proposalType);
    event Voted(uint256 indexed proposalId, address indexed voter, bool support, uint256 weight);
    event ProposalExecuted(uint256 indexed id);
```

Los **eventos** en Solidity son como "gritos" que el contrato lanza cuando
algo importante pasa. Las aplicaciones (como nuestro backend) pueden
"escuchar" estos eventos para reaccionar. `indexed` significa que se puede
filtrar por ese campo cuando se buscan eventos.

### Constructor

```solidity
    constructor(address _idea) {
        require(_idea != address(0), "GV: invalid IDEA");
        idea = IERC20(_idea);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }
```

El **constructor** se ejecuta UNA SOLA VEZ cuando se despliega el contrato:
1. Recibe la dirección del token $IDEA
2. Verifica que no sea `address(0)` (dirección vacía)
3. Guarda el token en la variable `idea`
4. Le da al `msg.sender` (quien despliega) los roles DEFAULT_ADMIN_ROLE y
   ADMIN_ROLE

### Función: createProposal

```solidity
    function createProposal(
        string calldata description,
        ProposalType proposalType,
        bytes calldata data
    ) external {
        require(idea.balanceOf(msg.sender) >= PROPOSAL_THRESHOLD,
            "GV: need 1000 IDEA to propose");

        proposalCounter++;
        proposals[proposalCounter] = Proposal({
            id: proposalCounter,
            proposer: msg.sender,
            description: description,
            proposalType: proposalType,
            data: data,
            startTime: block.timestamp,
            endTime: block.timestamp + VOTING_PERIOD,
            forVotes: 0,
            againstVotes: 0,
            executed: false
        });

        emit ProposalCreated(proposalCounter, msg.sender, description, proposalType);
    }
```

**Explicación línea por línea:**

1. `external` → Solo se puede llamar desde fuera del contrato (no desde otras
   funciones del mismo contrato). `calldata` es una forma eficiente de pasar
   datos en read-only.

2. `require(idea.balanceOf(msg.sender) >= PROPOSAL_THRESHOLD)` → Antes de hacer
   algo, verificamos que quien llama (`msg.sender`) tenga al menos 1000 $IDEA.
   Si no tiene, la transacción **revierte** (se cancela y el gas se pierde).

3. `proposalCounter++` → Aumentamos el contador. `++` significa "sumar 1".

4. `proposals[proposalCounter] = Proposal({...})` → Guardamos la propuesta en
   el mapping con la ID que acabamos de generar.

5. `block.timestamp` → El momento actual en la blockchain (timestamp UNIX,
   segundos desde 1970).

6. `block.timestamp + VOTING_PERIOD` → La fecha de fin es 7 días después de ahora.

7. `emit ProposalCreated(...)` → Lanzamos el evento para que el mundo sepa
   que se creó una propuesta.

### Función: vote

```solidity
    function vote(uint256 proposalId, bool support) external {
        Proposal storage prop = proposals[proposalId];
        require(prop.id != 0, "GV: proposal not found");
        require(block.timestamp >= prop.startTime, "GV: voting not started");
        require(block.timestamp <= prop.endTime, "GV: voting ended");
        require(!hasVoted[proposalId][msg.sender], "GV: already voted");

        uint256 weight = idea.balanceOf(msg.sender);
        require(weight > 0, "GV: need IDEA to vote");

        hasVoted[proposalId][msg.sender] = true;

        if (support) {
            prop.forVotes += weight;
        } else {
            prop.againstVotes += weight;
        }

        emit Voted(proposalId, msg.sender, support, weight);
    }
```

**Explicación:**

1. `Proposal storage prop = proposals[proposalId]` → Obtenemos la propuesta.
   `storage` significa que trabajamos directamente con los datos en la
   blockchain (no una copia).

2. **Cuatro validaciones** (los `require`): la propuesta debe existir, la
   votación debe estar activa (entre start y end time), y no debe haber votado
   ya esta persona.

3. `uint256 weight = idea.balanceOf(msg.sender)` → El peso del voto es
   CUÁNTOS $IDEA tiene la wallet en este momento.

4. `if (support) { prop.forVotes += weight }` → Si vota a favor, se suma
   al contador de forVotes. Si no, a againstVotes.

### Función: executeProposal

```solidity
    function executeProposal(uint256 proposalId) external onlyRole(EXECUTOR_ROLE) {
        Proposal storage prop = proposals[proposalId];
        require(prop.id != 0, "GV: proposal not found");
        require(block.timestamp > prop.endTime, "GV: voting still active");
        require(!prop.executed, "GV: already executed");

        uint256 totalVotes = prop.forVotes + prop.againstVotes;
        uint256 totalSupply = IERC20(idea).totalSupply();
        require(totalVotes >= (totalSupply * QUORUM_BPS) / 10000, "GV: quorum not met");
        require(prop.forVotes > prop.againstVotes, "GV: proposal defeated");

        prop.executed = true;

        (bool success, ) = address(this).call(prop.data);
        require(success, "GV: execution failed");

        emit ProposalExecuted(proposalId);
    }
```

**Explicación:**

1. `onlyRole(EXECUTOR_ROLE)` → **Modificador** de AccessControl: solo pueden
   llamar esta función las wallets que tengan el rol EXECUTOR_ROLE.

2. `onlyRole(EXECUTOR_ROLE)`
   **Validaciones:**
   - La propuesta debe existir
   - La votación debe haber terminado (`block.timestamp > prop.endTime`)
   - No debe haber sido ejecutada ya

3. **Cálculo de quorum:** `totalVotes >= (totalSupply * 1000) / 10000`
   - `totalSupply` = cuántos $IDEA existen en total
   - Multiplicamos por 1000 (QUORUM_BPS) y dividimos por 10000
   - Esto da el 10% del total supply
   - La cantidad total de votos debe ser ≥ ese 10%

4. `require(prop.forVotes > prop.againstVotes)` → Debe ganar el "sí"

5. `address(this).call(prop.data)` → Ejecuta los datos de la propuesta.
   Esto permite que la propuesta haga cualquier cosa (ej: transferir tokens,
   cambiar parámetros, etc.).

### Función: getProposal

```solidity
    function getProposal(uint256 proposalId) external view returns (Proposal memory) {
        return proposals[proposalId];
    }
```

- `view` → No modifica el estado, solo lee. No cuesta gas llamarla (la llamamos
  con `ethCall`, no con una transacción).
- `returns (Proposal memory)` → Devuelve el struct completo.
- Simplemente devuelve lo que hay en `proposals[proposalId]`.

---

## 5. Paso 2: Base de Datos (Flyway V18)

### ¿Qué es Flyway?

Flyway es una herramienta que **versiona** los cambios en la base de datos.
Cada archivo SQL se llama "migración" y se ejecuta una sola vez en orden.

Por ejemplo:
- `V1__create_projects_table.sql` → Crea la tabla de proyectos
- `V2__add_project_permissions.sql` → Agrega columnas de permisos
- ...
- `V18__create_governance_tables.sql` → Crea la tabla de propuestas

Las migraciones están en: `src/main/resources/db/proyectos/`

### Archivo: `V18__create_governance_tables.sql`

```sql
CREATE TABLE IF NOT EXISTS proposals (
    id BIGSERIAL PRIMARY KEY,
    on_chain_id BIGINT UNIQUE,
    proposer_address VARCHAR(42) NOT NULL,
    proposer_user_id BIGINT REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    proposal_type VARCHAR(50) NOT NULL DEFAULT 'ProjectApproval',
    data_bytes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    for_votes DECIMAL(40,0) NOT NULL DEFAULT 0,
    against_votes DECIMAL(40,0) NOT NULL DEFAULT 0,
    total_votes DECIMAL(40,0) NOT NULL DEFAULT 0,
    start_time TIMESTAMP NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    tx_hash VARCHAR(66),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Columna por columna

| Columna | Tipo SQL | ¿Para qué sirve? |
|---------|---------|-----------------|
| `id` | `BIGSERIAL PRIMARY KEY` | ID autoincremental de nuestra base de datos (no confundir con la ID de la blockchain) |
| `on_chain_id` | `BIGINT UNIQUE` | ID de la propuesta en el contrato (se llena después de crear la propuesta on-chain) |
| `proposer_address` | `VARCHAR(42)` | Dirección de la wallet que creó la propuesta (42 chars = 0x + 40 hex) |
| `proposer_user_id` | `BIGINT REFERENCES users(id)` | ID del usuario en nuestra tabla `users` (para mostrar el nombre) |
| `title` | `VARCHAR(200)` | Título corto de la propuesta |
| `description` | `TEXT` | Descripción larga de la propuesta |
| `proposal_type` | `VARCHAR(50)` | Tipo: 'ProjectApproval', 'ParameterChange', 'TreasuryAction' |
| `data_bytes` | `TEXT` | Datos opcionales para ejecución (guardados como texto) |
| `status` | `VARCHAR(20)` | Estado: 'ACTIVE' (activa), 'EXECUTED' (ejecutada), 'FAILED' (falló) |
| `for_votes` | `DECIMAL(40,0)` | Votos a favor (guardamos como decimal de hasta 40 dígitos) |
| `against_votes` | `DECIMAL(40,0)` | Votos en contra |
| `total_votes` | `DECIMAL(40,0)` | Suma de for_votes + against_votes |
| `start_time` | `TIMESTAMP` | Cuándo empezó la votación |
| `end_time` | `TIMESTAMP` | Cuándo termina la votación |
| `executed_at` | `TIMESTAMP` | Cuándo se ejecutó (nullable, NULL si no se ejecutó) |
| `tx_hash` | `VARCHAR(66)` | Hash de la transacción on-chain (66 chars = 0x + 64 hex) |
| `created_at` | `TIMESTAMP` | Cuándo se creó el registro en nuestra BD |
| `updated_at` | `TIMESTAMP` | Cuándo se actualizó por última vez |

### ¿Por qué DECIMAL(40,0) y no BIGINT?

Los votos en blockchain son `uint256`, que puede almacenar números de hasta
**78 dígitos**. PostgreSQL `BIGINT` solo soporta hasta 18 dígitos.
`DECIMAL(40,0)` nos da 40 dígitos, suficiente para la mayoría de los casos.

### ¿Por qué tenemos dos IDs (id y on_chain_id)?

- `id` = ID de nuestra base de datos (secuencial: 1, 2, 3...)
- `on_chain_id` = ID que le asignó el contrato en la blockchain

Se podría usar el mismo valor, pero separarlos nos da flexibilidad. Por
ejemplo, si migramos de base de datos, el `on_chain_id` sigue siendo válido.

---

## 6. Paso 3: Servicio Blockchain (IdeaGovernanceService.java)

### ¿Qué hace este archivo?

Este servicio **se conecta a la blockchain** para ejecutar funciones del
contrato `IdeaGovernance.sol`. Usa la biblioteca **Web3j** para:

1. **Codificar** las llamadas a funciones en formato ABI (el idioma que
   entiende Ethereum)
2. **Enviar** transacciones firmadas con nuestra wallet (RawTransactionManager)
3. **Decodificar** las respuestas de las funciones `view` (las que solo leen)

### Archivo: `IdeaGovernanceService.java`

#### Paquete e imports

```java
package com.systeam.blockchain.service;
```

Este archivo está en el paquete `blockchain.service`, junto con otros
servicios que hablan con la blockchain (como `InvestmentSwapService`,
`DividendDistributorService`, etc.).

Los **imports** son las clases que necesitamos. Las más importantes:

```java
import org.web3j.abi.FunctionEncoder;       // Codifica funciones en ABI
import org.web3j.abi.FunctionReturnDecoder;  // Decodifica respuestas ABI
import org.web3j.abi.TypeReference;          // Referencia a tipos Solidity
import org.web3j.abi.datatypes.*;            // Tipos: Address, Uint256, Bool, etc.
import org.web3j.protocol.Web3j;             // Conexión al nodo blockchain
import org.web3j.protocol.core.methods.request.Transaction;  // Para ethCall
import org.web3j.protocol.core.methods.response.EthCall;     // Respuesta de ethCall
import org.web3j.protocol.core.methods.response.EthSendTransaction; // Respuesta de tx
import org.web3j.crypto.Credentials;         // Nuestra clave privada
import org.web3j.tx.RawTransactionManager;   // Firma y envía transacciones
```

#### Inyección de dependencias

```java
@Service
public class IdeaGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(IdeaGovernanceService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    public IdeaGovernanceService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }
```

- `Web3j`: El cliente blockchain (se conecta a Base Sepolia)
- `Credentials`: Nuestra wallet (la clave privada del backend)
- `RawTransactionManager`: El objeto que firma y envía transacciones
- `BlockchainProperties`: Lee las direcciones de los contratos desde `.env`

#### createProposal()

```java
public String createProposal(String description, Integer proposalType, byte[] data) throws Exception {
    String governanceAddress = props.getIdeaGovernanceAddress();
    assertConfigured(governanceAddress, "IdeaGovernance");

    Function fn = new Function(
        "createProposal",
        List.of(
            new Utf8String(description),
            new Uint8(proposalType),
            new org.web3j.abi.datatypes.DynamicBytes(data)
        ),
        List.of()
    );
    return sendTransaction(governanceAddress, fn, BigInteger.valueOf(400_000L));
}
```

**Paso a paso:**

1. Obtenemos la dirección del contrato desde las propiedades. Si no está
   configurada, `assertConfigured` lanza una excepción.

2. Creamos un objeto `Function` con:
   - **Nombre**: `"createProposal"` (debe coincidir exactamente con el nombre
     en Solidity)
   - **Entradas** (inputs): Los 3 parámetros que espera la función:
     - `description` → `Utf8String` (string en Solidity)
     - `proposalType` → `Uint8` (uint8, el enum)
     - `data` → `DynamicBytes` (bytes de longitud variable)
   - **Salidas** (outputs): Vacío `List.of()` porque `createProposal` no
     devuelve nada (solo emite un evento)

3. `sendTransaction()` envía la transacción con un límite de gas de 400,000.

#### vote()

```java
public String vote(BigInteger proposalId, Boolean support) throws Exception {
    String governanceAddress = props.getIdeaGovernanceAddress();
    assertConfigured(governanceAddress, "IdeaGovernance");

    Function fn = new Function(
        "vote",
        List.of(new Uint256(proposalId), new org.web3j.abi.datatypes.Bool(support)),
        List.of()
    );
    return sendTransaction(governanceAddress, fn, BigInteger.valueOf(200_000L));
}
```

Igual que `createProposal` pero con:
- Input 1: `proposalId` como `Uint256`
- Input 2: `support` como `Bool`
- Gas: 200,000 (menos porque es una operación más simple)

#### executeProposal()

```java
public String executeProposal(BigInteger proposalId) throws Exception {
    // ... obtiene dirección ...
    Function fn = new Function(
        "executeProposal",
        List.of(new Uint256(proposalId)),
        List.of()
    );
    return sendTransaction(governanceAddress, fn, BigInteger.valueOf(200_000L));
}
```

Solo recibe `proposalId` como `Uint256`.

#### getProposalCount() [CORREGIDO]

```java
public BigInteger getProposalCount() throws Exception {
    String governanceAddress = props.getIdeaGovernanceAddress();
    assertConfigured(governanceAddress, "IdeaGovernance");

    Function fn = new Function(
        "proposalCounter",   // ← ESTE ES EL NOMBRE CORRECTO
        List.of(),           // Sin parámetros de entrada
        List.of(new TypeReference<Uint256>() {})  // Espera un Uint256 como salida
    );
    List<org.web3j.abi.datatypes.Type> result = executeCall(governanceAddress, fn);
    if (!result.isEmpty() && result.get(0) instanceof Uint256 val) {
        return val.getValue();
    }
    return BigInteger.ZERO;
}
```

> **⚠️ ERROR QUE TUVIMOS AL PRINCIPIO:**
>
> Originalmente escribimos `"getProposalCount"` como nombre de función.
> Pero en Solidity, cuando declarás `uint256 public proposalCounter;`,
> el compilador **genera automáticamente** un getter llamado
> **`proposalCounter()`**, NO `getProposalCount()`.
>
> Como `"getProposalCount"` no existe en el contrato, Web3j devolvía datos
> vacíos o basura. La corrección fue cambiar el nombre a `"proposalCounter"`.

**Lectura vs escritura**: Esta función usa `executeCall()` (ethCall) en lugar
de `sendTransaction()` porque solo **lee** datos (es una función `view` en
Solidity). Las lecturas no cuestan gas.

#### getProposal()

```java
public List<org.web3j.abi.datatypes.Type> getProposal(BigInteger proposalId) throws Exception {
    // ... obtiene dirección ...

    Function fn = new Function(
        "getProposal",
        List.of(new Uint256(proposalId)),
        List.of(
            new TypeReference<Uint256>() {},                    // id
            new TypeReference<Address>() {},                    // proposer
            new TypeReference<Utf8String>() {},                 // description
            new TypeReference<Uint8>() {},                      // proposalType
            new TypeReference<org.web3j.abi.datatypes.DynamicBytes>() {}, // data
            new TypeReference<Uint256>() {},                    // startTime
            new TypeReference<Uint256>() {},                    // endTime
            new TypeReference<Uint256>() {},                    // forVotes
            new TypeReference<Uint256>() {},                    // againstVotes
            new TypeReference<org.web3j.abi.datatypes.Bool>() {}  // executed
        )
    );
    return executeCall(governanceAddress, fn);
}
```

**¿Por qué 10 TypeReference?**

Porque el struct `Proposal` en Solidity tiene 10 campos. Al llamar a
`getProposal()`, el contrato devuelve una **tupla** con esos 10 valores en
orden. Necesitamos decirle a Web3j cómo decodificar cada uno.

**Orden = el mismo que en el struct de Solidity:**
```
Proposal( id, proposer, description, proposalType, data, startTime, endTime, forVotes, againstVotes, executed )
```

#### Métodos privados auxiliares

```java
private void assertConfigured(String address, String name) {
    if (address == null || address.equals("0x0000000000000000000000000000000000000000")) {
        throw new IllegalStateException(name + " no configurado");
    }
}
```

Verifica que la dirección del contrato no sea nula ni `address(0)`.

```java
private String sendTransaction(String contractAddress, Function fn, BigInteger gasLimit) throws Exception {
    String encodedFunction = FunctionEncoder.encode(fn);       // 1. Codificar función
    EthSendTransaction response = txManager.sendTransaction(   // 2. Firmar y enviar
        web3j.ethGasPrice().send().getGasPrice(),              //    Gas price actual
        gasLimit,                                              //    Límite de gas
        contractAddress,                                       //    Dirección del contrato
        encodedFunction,                                       //    Función codificada
        BigInteger.ZERO                                        //    Valor (0 ether)
    );
    if (response.hasError()) {
        throw new RuntimeException("Error en tx: " + response.getError().getMessage());
    }
    String txHash = response.getTransactionHash();
    log.info("Tx enviada: {} contrato: {}", txHash, contractAddress);
    return txHash;
}
```

**Paso a paso de sendTransaction:**

1. `FunctionEncoder.encode(fn)` → Convierte la función en un string hexadecimal
   que el contrato entiende (ABI encoding).

2. `txManager.sendTransaction(...)` → Firma la transacción con nuestra clave
   privada y la envía al nodo blockchain.

3. **Gas price**: Lo consultamos en vivo con `ethGasPrice()`. Alternativamente
   podríamos usar un valor fijo.

4. **Gas limit**: 400,000 para createProposal, 200,000 para vote y execute.
   Si el gas es insuficiente, la tx falla con "out of gas".

5. `ZERO` → No enviamos ether, solo datos.

6. Si hay error, lanzamos excepción. Si no, devolvemos el hash.

```java
private List<org.web3j.abi.datatypes.Type> executeCall(String contractAddress, Function fn) throws Exception {
    String encodedFunction = FunctionEncoder.encode(fn);
    EthCall response = web3j.ethCall(
        Transaction.createEthCallTransaction(credentials.getAddress(), contractAddress, encodedFunction),
        DefaultBlockParameterName.LATEST
    ).send();
    if (response.hasError()) {
        throw new RuntimeException("Error en ethCall: " + response.getError().getMessage());
    }
    return FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());
}
```

**Diferencia clave**: `executeCall()` usa `ethCall` en lugar de `sendTransaction`.
`ethCall` **no envía una transacción**, solo simula la llamada. Es gratis.
Solo funciona para funciones `view` o `pure` (que no modifican estado).

---

## 7. Paso 4: Servicio Off-Chain (GovernanceService.java)

### ¿Qué hace este archivo?

Maneja todas las operaciones contra la base de datos PostgreSQL relacionadas
con propuestas. Usa `JdbcTemplate` (de Spring) para hacer consultas SQL.

### Archivo: `GovernanceService.java`

#### Paquete e imports

```java
package com.systeam.governance.service;
```

Este archivo está en el paquete `governance.service`, separado del servicio
blockchain. Los servicios que necesita:

```java
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
```

#### Constructor

```java
@Service
public class GovernanceService {

    private final JdbcTemplate jdbc;

    public GovernanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
```

`JdbcTemplate` es la clase de Spring que ejecuta SQL. Spring la crea
automáticamente porque tenemos configurada la conexión a PostgreSQL.

#### createProposalOffChain()

```java
@Transactional
public ProposalResponse createProposalOffChain(CreateProposalRequest request, Long userId,
                                                 String walletAddress, String txHash) {
    LocalDateTime endTime = LocalDateTime.now().plusDays(7);
    jdbc.update("""
        INSERT INTO proposals (proposer_address, proposer_user_id, title, description,
                               proposal_type, data_bytes, status, start_time, end_time, tx_hash)
        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', NOW(), ?, ?)
        """,
        walletAddress, userId, request.getTitle(), request.getDescription(),
        proposalTypeName(request.getProposalType()),
        request.getData() != null ? new String(request.getData()) : null,
        endTime, txHash
    );

    Long id = jdbc.queryForObject("SELECT LASTVAL()", Long.class);
    return getProposalById(id);
}
```

**Paso a paso:**

1. `@Transactional` → Todo esto se ejecuta en una transacción de base de datos.
   Si algo falla, todo se deshace (rollback).

2. `LocalDateTime.now().plusDays(7)` → La propuesta dura 7 días (igual que en
   el contrato: `VOTING_PERIOD = 7 days`).

3. `jdbc.update(...)` → Ejecuta un INSERT con parámetros. Los `?` se reemplazan
   por los valores en orden. Esto evita SQL injection.

4. `SELECT LASTVAL()` → Obtiene el ID que PostgreSQL generó automáticamente
   para esta fila (es el `id BIGSERIAL PRIMARY KEY`).

5. `getProposalById(id)` → Devuelve la propuesta recién creada.

#### updateOnChainId()

```java
@Transactional
public void updateOnChainId(Long offChainId, BigInteger onChainId) {
    jdbc.update("UPDATE proposals SET on_chain_id = ? WHERE id = ?",
        onChainId.longValueExact(), offChainId);
}
```

Después de crear una propuesta en la blockchain, actualizamos el `on_chain_id`
en nuestra BD para tener la referencia cruzada.

#### getProposalById()

```java
public ProposalResponse getProposalById(Long id) {
    List<Map<String, Object>> rows = jdbc.query(
        "SELECT p.*, u.username AS proposer_name " +
        "FROM proposals p LEFT JOIN users u ON p.proposer_user_id = u.id WHERE p.id = ?",
        ... // mapeo de filas ...
        id
    );
    return rows.isEmpty() ? null : mapToResponse(rows.get(0));
}
```

Usamos `LEFT JOIN users` para traer el nombre del usuario que creó la
propuesta. `LEFT JOIN` significa que si el usuario no existe (por ejemplo,
se borró), igual mostramos los datos de la propuesta.

#### listProposals()

```java
public List<ProposalResponse> listProposals(String status, int page, int size) {
    String sql = "SELECT p.*, u.username AS proposer_name " +
                 "FROM proposals p LEFT JOIN users u ON p.proposer_user_id = u.id";
    List<Object> params = new java.util.ArrayList<>();

    if (status != null && !status.isBlank()) {
        sql += " WHERE p.status = ?";
        params.add(status.toUpperCase());
    }
    sql += " ORDER BY p.created_at DESC LIMIT ? OFFSET ?";
    params.add(size);
    params.add(page * size);

    List<Map<String, Object>> rows = jdbc.query(sql, params.toArray(), ...);
    return rows.stream().map(this::mapToResponse).collect(Collectors.toList());
}
```

**Paginación**: `LIMIT` indica cuántas filas traer, `OFFSET` cuántas saltarse.
Si `page=0` y `size=20`, trae las primeras 20. Si `page=1`, trae las
siguientes 20 (saltándose las primeras 20).

#### updateVotes() y markExecuted()

```java
@Transactional
public void updateVotes(Long proposalId, BigInteger forVotes, BigInteger againstVotes) {
    BigInteger total = forVotes.add(againstVotes);
    jdbc.update(
        "UPDATE proposals SET for_votes = ?, against_votes = ?, total_votes = ?, updated_at = NOW() WHERE id = ?",
        forVotes, againstVotes, total, proposalId
    );
}

@Transactional
public void markExecuted(Long proposalId, LocalDateTime executedAt) {
    jdbc.update(
        "UPDATE proposals SET status = 'EXECUTED', executed_at = ?, updated_at = NOW() WHERE id = ?",
        executedAt, proposalId
    );
}
```

Dos métodos simples para actualizar el estado on-chain en nuestra copia
off-chain.

#### mapToResponse()

```java
private ProposalResponse mapToResponse(Map<String, Object> m) {
    ProposalResponse r = new ProposalResponse();
    r.setId((Long) m.get("id"));
    if (m.get("on_chain_id") != null) {
        r.setOnChainId(BigInteger.valueOf(((Number) m.get("on_chain_id")).longValue()));
    }
    r.setProposerAddress((String) m.get("proposer_address"));
    r.setProposerUserId((Long) m.get("proposer_user_id"));
    r.setProposerName((String) m.get("proposer_name"));
    r.setTitle((String) m.get("title"));
    r.setDescription((String) m.get("description"));
    r.setProposalType((String) m.get("proposal_type"));
    r.setStatus((String) m.get("status"));
    r.setForVotes(toBigInt(m.get("for_votes")));
    r.setAgainstVotes(toBigInt(m.get("against_votes")));
    r.setTotalVotes(toBigInt(m.get("total_votes")));
    r.setStartTime(ldt(m.get("start_time")));
    r.setEndTime(ldt(m.get("end_time")));
    r.setExecutedAt(ldt(m.get("executed_at")));
    r.setTxHash((String) m.get("tx_hash"));
    r.setCreatedAt(ldt(m.get("created_at")));
    return r;
}
```

Convierte un `Map` (fila de SQL) en un `ProposalResponse` (objeto Java).

**Problema resuelto**: PostgreSQL almacena `for_votes` como `BigDecimal`,
pero en Java los votos son `BigInteger`. El método `toBigInt()` convierte:

```java
private BigInteger toBigInt(Object v) {
    if (v == null) return BigInteger.ZERO;
    return ((java.math.BigDecimal) v).toBigInteger();
}
```

#### proposalTypeName()

```java
private String proposalTypeName(Integer type) {
    if (type == null) return "ProjectApproval";
    return switch (type) {
        case 0 -> "ProjectApproval";
        case 1 -> "ParameterChange";
        case 2 -> "TreasuryAction";
        default -> "ProjectApproval";
    };
}
```

Convierte el número del enum (0, 1, 2) en el nombre legible.

---

## 8. Paso 5: Los DTOs

### ¿Qué es un DTO?

**DTO** = Data Transfer Object. Es una clase simple que sirve para **transportar
datos** entre el frontend y el backend (o entre capas del backend). No tiene
lógica, solo campos.

### CreateProposalRequest.java

```java
package com.systeam.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateProposalRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private Integer proposalType;

    private byte[] data;
```

**Validaciones con Jakarta Validation:**
- `@NotBlank` → El campo no puede ser null, vacío ni solo espacios
- `@NotNull` → El campo no puede ser null

**Campos:**
- `title`: Título de la propuesta (ej: "Aprobar proyecto EcoTech")
- `description`: Descripción larga
- `proposalType`: 0=ProjectApproval, 1=ParameterChange, 2=TreasuryAction
- `data`: Datos binarios opcionales (para ejecutar si se aprueba)

### VoteRequest.java

```java
package com.systeam.governance.dto;

import jakarta.validation.constraints.NotNull;

public class VoteRequest {

    @NotNull
    private Long proposalId;

    @NotNull
    private Boolean support;
```

**Campos:**
- `proposalId`: ID de la propuesta a votar (de nuestra BD)
- `support`: `true` = a favor, `false` = en contra

### ProposalResponse.java

Es el DTO de **salida**: contiene todos los datos que devolvemos al frontend.

```java
public class ProposalResponse {
    private Long id;                    // ID en nuestra BD
    private BigInteger onChainId;       // ID en la blockchain
    private String proposerAddress;     // Dirección de la wallet
    private Long proposerUserId;        // ID del usuario en users
    private String proposerName;        // Nombre del usuario (JOIN con users)
    private String title;               // Título
    private String description;         // Descripción
    private String proposalType;        // Tipo: ProjectApproval, etc.
    private String status;              // ACTIVE, EXECUTED
    private BigInteger forVotes;        // Votos a favor (en $IDEA)
    private BigInteger againstVotes;    // Votos en contra
    private BigInteger totalVotes;      // Total de votos
    private LocalDateTime startTime;    // Inicio de votación
    private LocalDateTime endTime;      // Fin de votación
    private LocalDateTime executedAt;   // Cuándo se ejecutó
    private String txHash;              // Hash de la tx on-chain
    private LocalDateTime createdAt;    // Cuándo se creó en BD
}
```

Cada campo tiene su getter y setter (no los copiamos para no hacer el doc
interminable, pero están en el archivo real).

---

## 9. Paso 6: Controlador REST (GovernanceController.java)

### ¿Qué es un Controller en Spring?

Es una clase que **recibe peticiones HTTP** y las convierte en llamadas a
servicios. Spring usa anotaciones para mapear rutas:

- `@RestController` → Esta clase es un controlador REST
- `@RequestMapping("/api/governance")` → Todas las rutas empiezan con `/api/governance`
- `@PostMapping("/proposals")` → Mapea POST a `/api/governance/proposals`
- `@GetMapping("/proposals")` → Mapea GET a `/api/governance/proposals`

### Archivo: `GovernanceController.java`

#### Paquete e imports

```java
package com.systeam.governance.controller;
```

Los más importantes:

```java
import org.springframework.security.access.prepost.PreAuthorize;    // Seguridad
import org.springframework.security.core.annotation.AuthenticationPrincipal;  // Usuario actual
import org.springframework.web.bind.annotation.*;

import com.systeam.blockchain.service.IdeaGovernanceService;   // Servicio blockchain
import com.systeam.governance.dto.*;                            // DTOs
import com.systeam.governance.service.GovernanceService;        // Servicio BD
import com.systeam.security.JwtPrincipal;                       // Usuario autenticado

import jakarta.validation.Valid;  // Para validar @NotBlank, @NotNull
```

#### Constructor

```java
@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final IdeaGovernanceService onChainService;
    private final GovernanceService offChainService;

    public GovernanceController(IdeaGovernanceService onChainService,
                                 GovernanceService offChainService) {
        this.onChainService = onChainService;
        this.offChainService = offChainService;
    }
```

Inyectamos los dos servicios:
- `onChainService` (IdeaGovernanceService): Habla con la blockchain
- `offChainService` (GovernanceService): Habla con PostgreSQL

#### POST /api/governance/proposals

```java
@PostMapping("/proposals")
@ResponseStatus(HttpStatus.CREATED)
@PreAuthorize("hasAuthority('governance:create')")
public ProposalResponse createProposal(@RequestBody @Valid CreateProposalRequest request,
                                        @AuthenticationPrincipal JwtPrincipal user) {
    try {
        String txHash = onChainService.createProposal(
            request.getDescription(),
            request.getProposalType(),
            request.getData() != null ? request.getData() : new byte[0]
        );
        ProposalResponse saved = offChainService.createProposalOffChain(
            request, user.userId(), null, txHash
        );
        return saved;
    } catch (Exception e) {
        throw new RuntimeException("Error al crear propuesta en blockchain: " + e.getMessage(), e);
    }
}
```

**Explicación detallada:**

1. `@PostMapping("/proposals")` → Atiende POST en `/api/governance/proposals`

2. `@ResponseStatus(HttpStatus.CREATED)` → Responde con código 201 (Created)
   en lugar del 200 por defecto

3. `@PreAuthorize("hasAuthority('governance:create')")` → Solo usuarios con el
   permiso `governance:create` pueden crear propuestas. Este permiso viene del
   JWT que el frontend envía en el header `Authorization: Bearer <jwt>`.

4. `@RequestBody @Valid CreateProposalRequest request` → Spring convierte
   automáticamente el JSON del body en el objeto Java (gracias a Jackson).
   `@Valid` activa las validaciones (`@NotBlank`, `@NotNull`).

5. `@AuthenticationPrincipal JwtPrincipal user` → Spring Security inyecta
   el usuario autenticado. `JwtPrincipal` tiene `userId()` y `email()`.

6. `onChainService.createProposal(...)` → Llama a la blockchain. Si falla
   (sin conexión, sin gas, wallet sin 1000 $IDEA), lanza excepción.

7. `offChainService.createProposalOffChain(...)` → Guarda en PostgreSQL.
   Solo se ejecuta si la tx on-chain fue exitosa.

8. Si todo funciona, devuelve `ProposalResponse` con los datos + el txHash.

#### POST /api/governance/vote

```java
@PostMapping("/vote")
@PreAuthorize("hasAuthority('governance:vote')")
public String vote(@RequestBody @Valid VoteRequest request) {
    try {
        String txHash = onChainService.vote(
            BigInteger.valueOf(request.getProposalId()),
            request.getSupport()
        );
        return txHash;
    } catch (Exception e) {
        throw new RuntimeException("Error al votar: " + e.getMessage(), e);
    }
}
```

Devuelve solo el hash de la transacción (un string). La actualización de
votos en la BD se podría hacer después escuchando eventos.

#### POST /api/governance/proposals/{id}/execute

```java
@PostMapping("/proposals/{id}/execute")
@PreAuthorize("hasAuthority('governance:execute')")
public String executeProposal(@PathVariable Long id) {
    try {
        String txHash = onChainService.executeProposal(BigInteger.valueOf(id));
        offChainService.markExecuted(id, java.time.LocalDateTime.now());
        return txHash;
    } catch (Exception e) {
        throw new RuntimeException("Error al ejecutar propuesta: " + e.getMessage(), e);
    }
}
```

`@PathVariable Long id` → Toma el `{id}` de la URL (`/proposals/5/execute`
→ `id = 5`).

Después de ejecutar on-chain, actualiza el estado en PostgreSQL.

#### GET /api/governance/proposals

```java
@GetMapping("/proposals")
@PreAuthorize("hasAuthority('governance:read')")
public List<ProposalResponse> listProposals(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return offChainService.listProposals(status, page, size);
}
```

`@RequestParam(required = false)` → Parámetro opcional. Ej:
`GET /api/governance/proposals?status=ACTIVE&page=0&size=20`

#### GET /api/governance/proposals/{id}

```java
@GetMapping("/proposals/{id}")
@PreAuthorize("hasAuthority('governance:read')")
public ProposalResponse getProposal(@PathVariable Long id) {
    ProposalResponse r = offChainService.getProposalById(id);
    if (r == null) {
        throw new RuntimeException("Propuesta no encontrada: " + id);
    }
    return r;
}
```

Si no encuentra la propuesta, lanza error 500. Idealmente sería un 404,
pero por ahora usamos RuntimeException.

#### GET /api/governance/count

```java
@GetMapping("/count")
@PreAuthorize("hasAuthority('governance:read')")
public long getCount() {
    try {
        return offChainService.listProposals(null, 0, Integer.MAX_VALUE).size();
    } catch (Exception e) {
        return 0;
    }
}
```

Cuenta todas las propuestas. No es eficiente (trae todas y cuenta), pero
funciona como punto de partida.

---

## 10. Flujo Completo Paso a Paso

### Escenario: Un usuario crea una propuesta para aprobar un proyecto

#### Paso 1: El frontend envía la petición

```
POST /api/governance/proposals
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "title": "Aprobar proyecto EcoTech",
  "description": "EcoTech es un proyecto de energía renovable...",
  "proposalType": 0,
  "data": null
}
```

#### Paso 2: SecurityConfig + TokenValidationFilter

Antes de llegar al controller, Spring Security:
1. Toma el header `Authorization`
2. Llama al servicio de auth (`AuthServiceClient`) para validar el JWT
3. Si es válido, crea un `JwtPrincipal` con el userId y email del usuario
4. Verifica que el usuario tenga el permiso `governance:create`

#### Paso 3: GovernanceController recibe la petición

Spring convierte el JSON en un objeto `CreateProposalRequest` y asigna
el usuario autenticado a `JwtPrincipal user`.

#### Paso 4: IdeaGovernanceService.createProposal()

El servicio:
1. Toma la dirección del contrato `IdeaGovernance` desde `BlockchainProperties`
2. Crea un objeto `Function` con nombre `"createProposal"` y los parámetros
3. Codifica la función con `FunctionEncoder.encode()`
4. Firma y envía la transacción con `RawTransactionManager`
5. Si la transacción es exitosa, devuelve el hash (ej: `0xabc123...`)

El contrato en la blockchain:
1. Verifica que `msg.sender` tenga ≥ 1000 $IDEA
2. Incrementa `proposalCounter` (pasa de 0 a 1)
3. Guarda la propuesta en `proposals[1]`
4. Emite el evento `ProposalCreated`

#### Paso 5: GovernanceService.createProposalOffChain()

El servicio:
1. Calcula `endTime = ahora + 7 días`
2. Hace un `INSERT` en la tabla `proposals`
3. Obtiene el ID generado con `SELECT LASTVAL()`
4. Devuelve el `ProposalResponse` completo

#### Paso 6: El controller devuelve la respuesta

```json
{
  "id": 1,
  "onChainId": null,
  "proposerAddress": null,
  "proposerUserId": 42,
  "proposerName": "juanperez",
  "title": "Aprobar proyecto EcoTech",
  "description": "EcoTech es un proyecto de energía renovable...",
  "proposalType": "ProjectApproval",
  "status": "ACTIVE",
  "forVotes": 0,
  "againstVotes": 0,
  "totalVotes": 0,
  "startTime": "2026-05-19T23:58:00",
  "endTime": "2026-05-26T23:58:00",
  "executedAt": null,
  "txHash": "0xabc123def456...",
  "createdAt": "2026-05-19T23:58:00"
}
```

### Escenario: Un usuario vota a favor de la propuesta

```
POST /api/governance/vote
Body: { "proposalId": 1, "support": true }
```

1. Controller recibe `VoteRequest` con `proposalId=1` y `support=true`
2. `IdeaGovernanceService.vote(1, true)` envía la transacción
3. El contrato verifica:
   - La propuesta existe (proposals[1].id != 0)
   - La votación está activa (entre startTime y endTime)
   - La wallet no votó antes
   - El votante tiene > 0 $IDEA
4. Si todo ok, suma `balanceOf(votante)` a `forVotes`
5. Devuelve el txHash

### Escenario: Se ejecuta la propuesta (después de 7 días)

```
POST /api/governance/proposals/1/execute
```

1. Controller llama a `IdeaGovernanceService.executeProposal(1)`
2. El contrato verifica:
   - Quien llama tiene `EXECUTOR_ROLE`
   - La propuesta existe
   - La votación terminó (`block.timestamp > endTime`)
   - No se ejecutó antes
   - Quorum alcanzado (≥ 10% del total supply votó)
   - Ganó el "sí" (forVotes > againstVotes)
3. Si todo ok, marca `executed = true` y ejecuta `prop.data`
4. Controller actualiza PostgreSQL: `status = 'EXECUTED'`

---

## 11. Errores Comunes

### Error 1: `"proposalCounter"` vs `"getProposalCount"`

**Problema**: Llamar a una función que no existe en el contrato.

```java
// ❌ INCORRECTO: Esta función no existe en IdeaGovernance.sol
Function fn = new Function("getProposalCount", ...);

// ✅ CORRECTO: proposalCounter es una variable public de Solidity,
//              el compilador genera el getter automáticamente
Function fn = new Function("proposalCounter", ...);
```

**¿Por qué pasa?** En Java estamos acostumbrados a `getX()` para obtener
el valor de X. Pero en Solidity, una variable `public` genera un getter
con el **mismo nombre de la variable**, no con `get` + nombre.

### Error 2: La wallet no tiene 1000 $IDEA

**Problema**: `createProposal` falla porque el contrato requiere
`balanceOf(msg.sender) >= 1000e18`.

**Solución**: Antes de crear propuestas, asegurarse de que la treasury wallet
tenga al menos 1000 $IDEA. Esto se hace transfiriendo tokens a la dirección
de la wallet (configurada en `.env` como `BLOCKCHAIN_PRIVATE_KEY`).

### Error 3: El struct Proposal se decodifica mal

**Problema**: `getProposal()` devuelve datos incorrectos o vacíos.

**Causa**: Los `TypeReference` no coinciden con el orden de los campos del
struct en Solidity.

**Solución**: Verificar que los 10 `TypeReference` estén en el **mismo orden**
que los campos en `struct Proposal` en `IdeaGovernance.sol`.

### Error 4: No se puede conectar a la blockchain

**Problema**: `Web3j` no puede conectarse al nodo de Base Sepolia.

**Causas posibles:**
- La URL del nodo está mal en `.env` (`BLOCKCHAIN_NODE_URL`)
- No hay internet
- El servicio de Infura/Alchemy está caído

**Solución**: Verificar la URL en `.env` y que el servicio esté operativo.

### Error 5: El contrato no está desplegado

**Problema**: `IdeaGovernanceService` lanza "IdeaGovernance no configurado".

**Causa**: La dirección del contrato en `.env` es `0x0000...` o null.

**Solución**: Desplegar el contrato y actualizar la variable en `.env`.

---

## 12. Próximos Pasos

### Inmediatos (dependen de infraestructura)

1. **Desplegar IdeaGovernance.sol en Base Sepolia**
   - Usar Foundry: `forge create`
   - Pasar la dirección del token $IDEA como parámetro del constructor
   - Actualizar `.env`: `BLOCKCHAIN_IDEA_GOVERNANCE=0x...`

2. **Asignar EXECUTOR_ROLE a la treasury wallet**
   - Llamar a `grantRole(EXECUTOR_ROLE, treasuryAddress)` desde la wallet admin
   - Esto permite que el backend ejecute propuestas

3. **Transferir 1000+ $IDEA a la treasury wallet**
   - Para que el backend pueda crear propuestas

### Mejoras futuras

4. **Sincronizar votos periódicamente**
   - Crear un `@Scheduled` que lea los eventos `Voted` de la blockchain
   - Actualizar `for_votes` y `against_votes` en PostgreSQL

5. **Votación desde wallet del usuario (EIP-712)**
   - Actualmente el backend vota con su propia wallet
   - Lo ideal es que cada usuario firme su voto desde MetaMask
   - El backend solo relaya la transacción firmada

6. **Escuchar eventos en tiempo real**
   - Usar WebSocket de Web3j para escuchar `ProposalCreated`, `Voted`,
     `ProposalExecuted`
   - Actualizar la BD automáticamente cuando ocurren

7. **Mejorar manejo de errores**
   - Devolver 404 cuando no se encuentra una propuesta
   - Mensajes de error más descriptivos
   - Capturar errores específicos de blockchain (gas, revert, etc.)


## 6. API Gateway � Explicaci�n completa

# API Gateway — Explicación completa para la exposición

## Índice

1. [¿Cómo funcionaba ANTES del Gateway?](#1-cómo-funcionaba-antes-del-gateway)
2. [¿Cuál era el problema?](#2-cuál-era-el-problema)
3. [¿Qué es un API Gateway? (explicación para no técnicos)](#3-qué-es-un-api-gateway-explicación-para-no-técnicos)
4. [Las opciones que consideramos](#4-las-opciones-que-consideramos)
5. [¿Por qué elegimos la Opción B?](#5-por-qué-elegimos-la-opción-b)
6. [Arquitectura final](#6-arquitectura-final)
7. [Paso a paso: lo que hicimos](#7-paso-a-paso-lo-que-hicimos)
8. [Ventajas de este cambio](#8-ventajas-de-este-cambio)
9. [Lo que NO cambió](#9-lo-que-no-cambió)
10. [Lo que falta / Próximos pasos](#10-lo-que-falta--próximos-pasos)
11. [Script para la exposición oral](#11-script-para-la-exposición-oral)
12. [Preguntas y respuestas preparadas](#12-preguntas-y-respuestas-preparadas)

---

## 1. ¿Cómo funcionaba ANTES del Gateway?

Para entender por qué hicimos el cambio, primero hay que entender cómo funcionaba antes.

### El "viejo" esquema

```
Frontend (React)
    │
    │  llama a:
    │  POST /api/auth/login
    ▼
User Service (repo de usuario, puerto 8080)
    │
    │  devuelve: JWT (accessToken)
    ▼
Frontend (guarda el token)
    │
    │  ahora llama a:
    │  GET /api/proyectos/...
    │  Header: Authorization: Bearer <JWT>
    ▼
Project Service (puerto 8081)
    │
    │  TokenValidationFilter.java:
    │  1. Saca el header Authorization
    │  2. Llama a repo de usuario: "¿Este token es válido?"
    │     → GET /auth/validate
    │     → Header: Authorization: Bearer <JWT>
    │
    ▼
User Service (repo de usuario, puerto 8080)
    │
    │  "Sí, el token es válido. El usuario es pepito@mail.com, rol: USER"
    ▼
Project Service (continúa)
    │  3. Crea un JwtPrincipal con los datos
    │  4. Lo mete en el SecurityContext de Spring
    │  5. Ejecuta el controlador (crear proyecto, invertir, etc.)
    ▼
    Base de datos
```

### Los archivos que hacían esto

En el Project Service (`Gestion_de_proyectos-Systeam`):

| Archivo | ¿Qué hace? |
|---|---|
| `TokenValidationFilter.java` | Intercepta cada request, saca el JWT, llama a repo de usuario |
| `AuthServiceClient.java` | Hace la llamada HTTP a repo de usuario (`GET /auth/validate`) |
| `ValidatedUser.java` | Record que mapea la respuesta de repo de usuario (userId, email, roles, permissions) |
| `JwtPrincipal.java` | Record con userId + email que se guarda en el SecurityContext |
| `SecurityConfig.java` | Conecta el TokenValidationFilter en la cadena de Spring Security |

---

## 2. ¿Cuál era el problema?

**Problema 1: Cada request = 1 llamada HTTP a repo de usuario**

Si hay 100 usuarios usando la app al mismo tiempo, el Project Service hace 100 llamadas a repo de usuario solo para preguntar "che, este token está bien?".

Si repo de usuario se cae o está lento, NADIE puede usar el Project Service, aunque el resto del sistema funcione perfecto.

**Problema 2: El Project Service hace dos trabajos**

```
Project Service
├── Trabajo 1: SEGURIDAD → validar JWT (NO es su responsabilidad)
└── Trabajo 2: NEGOCIO → proyectos, inversiones, tokens (SÍ es su responsabilidad)
```

En microservicios, **cada servicio hace una sola cosa**. Mezclar seguridad con negocio está mal.

**Problema 3: Si agregamos más servicios, el problema escala**

Imaginá que tenemos 10 servicios (wallet, marketplace, dividendos, etc.). Cada uno tendría que:
- Tener su propio `TokenValidationFilter.java`
- Llamar a repo de usuario en cada request
- 10 servicios × 100 usuarios = 1000 llamadas a repo de usuario por minuto

**Problema 4: El frontend conoce la arquitectura interna**

El frontend sabe que el Project Service está en el puerto 8081, el Wallet en el 8082, etc. Si cambiamos algún puerto, hay que actualizar el frontend. El frontend no debería saber nada de la estructura interna del backend.

---

## 3. ¿Qué es un API Gateway? (explicación para no técnicos)

### La analogía del edificio

Imaginá un **edificio de oficinas** con varias empresas:

- **Oficina 1:** User Service (login, registro)
- **Oficina 2:** Project Service (proyectos)
- **Oficina 3:** Wallet Service (billetera)
- **Oficina 4:** Marketplace Service (compra/venta de tokens)

**Sin Gateway:** Cada persona que llega al edificio tiene que:
1. Golpear la puerta de cada oficina
2. Mostrar el DNI en cada una
3. Preguntar "acá se hacen proyectos?"
4. Si se equivoca de oficina, le dicen "no, es la de al lado"

Esto es ineficiente y molesto.

**Con Gateway:** Hay un **recepcionista** en la entrada. Vos llegás y decís:
- "Hola, vengo a invertir en el proyecto 5"

El recepcionista:
1. Te pide el DNI **una sola vez** y lo verifica
2. Te dice "tome, vaya a la oficina 2, piso 1"
3. **Le avisa por teléfono a la oficina 2** que ya llegaste

La oficina 2 ya sabe quién sos, no te vuelve a pedir el DNI.

### Traduciendo a código

| Analogía | Código |
|---|---|
| Recepcionista | **API Gateway** (Spring Cloud Gateway) |
| Pedir DNI una vez | **Validar JWT** llamando a repo de usuario |
| Avisarle a la oficina | **Inyectar headers** (X-User-Id, X-User-Roles) |
| Decir "oficina 2, piso 1" | **Rutear** (`/api/projects/*` → Project Service) |
| Oficina confía en el recepcionista | `GatewayHeaderFilter` lee los headers, **no llama a repo de usuario** |

---

## 4. Las opciones que consideramos

Frente al problema, teníamos dos caminos.

### Opción A: Gateway parcial (fácil, pero incompleta)

```
Frontend
    ├── Login → DIRECTAMENTE → repo de usuario (8080)
    │                            (sin pasar por Gateway)
    └── Proyectos → Gateway (8989) → valida JWT → Project Service (8081)
```

**Ventaja:** No tocamos repo de usuario para nada. Sigue funcionando igual.

**Desventaja:** El Gateway no es la única entrada. El frontend tiene dos caminos distintos. No cumplimos del todo con la arquitectura de microservicios.

### Opción B: Gateway total (la que elegimos)

```
Frontend
    └── TODO → Gateway (8989)
                ├── /api/auth/* → (sin JWT) → repo de usuario (interno)
                └── /api/* → valida JWT → Servicios
```

**Ventaja:** Un solo punto de entrada. El frontend no sabe nada de los servicios internos.

**Desventaja:** Hay que cambiar el puerto de repo de usuario (hoy está en 8080, hay que pasarlo a un puerto interno).

### ¿Cuál elegimos?

Elegimos la **Opción B** porque:

1. **Es lo que se pide :** "Quiero un Gateway como único punto de entrada"
2. **Escala mejor:** Cuando agreguemos Wallet, Marketplace, etc., ya están todas atrás del Gateway
3. **El frontend se simplifica:** Solo tiene que apuntar a `http://localhost:8989` para todo
4. **Seguridad centralizada:** Si queremos bloquear un IP, rate limiting, blacklist, lo hacemos en un solo lugar

**Lo único que falta (y es tarea de repo de usuario):** Cambiar su puerto de 8080 a uno interno (ej: 8090) para que el Gateway sea la única entrada pública.

---

## 5. ¿Por qué elegimos la Opción B?

### Decisión 1: Gateway en repo SEPARADO

Podríamos haber hecho el Gateway adentro del mismo Project Service (un módulo más). Pero decidimos:

**→ Repositorio separado (`Systeam-Gateway`)**

¿Por qué?
- El Gateway es un microservicio independiente
- Puede escalarse por separado (más recursos si hay mucho tráfico)
- No mezcla tecnologías (Gateway usa Spring Cloud Gateway que es reactivo, no Spring MVC)
- Es la práctica recomendada en microservicios

### Decisión 2: El Gateway valida JWT llamando a repo de usuario (NO con librería JWT)

Podríamos haber validado el JWT directamente en el Gateway usando la librería `jjwt` (io.jsonwebtoken). No necesitábamos llamar a repo de usuario.

**→ Elegimos llamar a repo de usuario**

¿Por qué?
- repo de usuario es el dueño de la autenticación. Él sabe si un token está válido, si expiró, si el usuario fue baneado
- Si validamos con librería JWT, el Gateway tendría que tener la misma secret key que repo de usuario, y si repo de usuario la cambia, se rompe todo
- El Gateway no debería saber cómo se generan los tokens, solo preguntar "¿está bien este token?" y confiar en la respuesta

### Decisión 3: Inyectar headers en vez de pasar el token

**→ Elegimos X-User-Id, X-User-Roles, X-User-Permissions como headers**

¿Por qué?
- Los servicios de atrás no necesitan saber nada de JWT
- Solo necesitan saber quién es el usuario (userId) y qué puede hacer (roles)
- Es más simple: un `GatewayHeaderFilter` que lee headers es mucho más liviano que un `TokenValidationFilter` que llama a repo de usuario
- Si cambia el mecanismo de autenticación (ej: de JWT a SAML, a OAuth2), los servicios de atrás no se enteran

### Decisión 4: Project Service cambió a puerto 8081 (interno)

**→ Elegimos 8081**

Originalmente estaba en 8081. Después lo pasamos a 8082, pero volvimos a 8081 porque es el puerto que ya conocía. Al estar atrás del Gateway, el puerto da igual — el frontend nunca lo ve.

---

## 6. Arquitectura final

### Diagrama

```
Frontend (React)
    │
    │  SOLO llama a: http://localhost:8989
    ▼
┌────────────────────────────────────────────────┐
│         API GATEWAY (Spring Cloud Gateway)      │
│         Puerto: 8989 (PÚBLICO)                  │
│                                                 │
│  Funciones:                                     │
│  ┌──────────────────────────────────────────┐  │
│  │ ✅ Valida JWT → llama a repo de usuario           │  │
│  │ ✅ Inyecta headers (X-User-Id, etc.)     │  │
│  │ ✅ Rutea a cada servicio                 │  │
│  │ ✅ CORS configurado (un solo lugar)      │  │
│  │ ✅ (Futuro) Rate limiting                │  │
│  │ ✅ (Futuro) Logging centralizado         │  │
│  └──────────────────────────────────────────┘  │
└────────────────────┬───────────────────────────┘
                     │
       ┌─────────────┼─────────────┐
       │             │             │
       ▼             ▼             ▼
  ┌──────────┐ ┌──────────┐ ┌──────────┐
  │  User    │ │ Project  │ │ Wallet   │
  │  Service │ │ Service  │ │ Service  │
  │ (repo de usuario) │ │ (nos)    │ │ (futuro) │
  │          │ │          │ │          │
  │ Puerto:  │ │ Puerto:  │ │ Puerto:  │
  │  8080*   │ │  8081    │ │  8083    │
  └──────────┘ └──────────┘ └──────────┘
       │             │             │
       └─────────────┼─────────────┘
                     │
                     ▼
         ┌─────────────────────┐
         │   Base de datos     │
         │   (PostgreSQL)      │
         └─────────────────────┘
```

> **\* repo de usuario en 8080 es temporal.** Para que la arquitectura sea 100% correcta, repo de usuario debería moverse a un puerto interno (ej: 8090) y el Gateway ser el único puerto público (8989).

### Mapa de puertos final

| Servicio | Puerto | ¿Público? | Repo |
|---|---|---|---|
| **API Gateway** | **8989** | **Sí (único)** | `Systeam-Gateway` |
| User Service (repo de usuario) | 8080 | No (debería) | Suyo |
| Project Service | 8081 | No | `Gestion_de_proyectos-Systeam` |
| Wallet Service | 8083 | No | Mismo repo (futuro) |
| Otros servicios | 8084+ | No | Mismo repo (futuro) |

### Flujo de un request (paso a paso)

**Ejemplo:** Un usuario quiere crear un proyecto.

```
1. Frontend → POST http://localhost:8989/api/proyectos/crear
               Header: Authorization: Bearer <JWT>
               Body: { titulo: "Mi proyecto", monto: 1000 }

2. Gateway recibe el request
   │
   3. JwtAuthGatewayFilterFactory.java
      ├── Saca el header "Authorization"
      ├── ¿Tiene "Bearer "? Si no → 401
      ├── Llama a AuthServiceClient.validate(authHeader)
      │   └── GET http://localhost:8080/auth/validate
      │       Header: Authorization: Bearer <JWT>
      │
      └── repo de usuario responde:
          ✅ Válido → { userId: 5, email: "a@b.com",
                        roles: ["USER"], permissions: ["project:create"] }
          ❌ Inválido → 401

4.  Gateway muta el request (AGREGA headers):
    X-User-Id: 5
    X-User-Email: a@b.com
    X-User-Roles: USER
    X-User-Permissions: project:create

5.  Gateway rutea a:
    http://localhost:8081/api/proyectos/crear
    (con los headers nuevos)

6.  Project Service recibe el request
    │
    7. GatewayHeaderFilter.java
       ├── Lee X-User-Id: 5
       ├── Lee X-User-Roles: USER
       ├── Crea JwtPrincipal(5, "a@b.com")
       ├── Crea Authentication con roles
       └── Lo mete en SecurityContext

8.  El controlador ejecuta la lógica de negocio
    └── "El usuario 5 quiere crear un proyecto. Tiene permiso? Sí → OK"

9.  Respuesta: 201 Created
```

---

## 7. Paso a paso: lo que hicimos

### Paso 1: Entender el problema

Lo primero que hicimos fue analizar el código existente para entender cómo funcionaba la validación JWT.

Leímos estos archivos:
- `TokenValidationFilter.java` — el filtro que intercepta cada request
- `AuthServiceClient.java` — el cliente HTTP que llama a repo de usuario
- `ValidatedUser.java` — el record que mapea la respuesta
- `JwtPrincipal.java` — el record del usuario autenticado
- `SecurityConfig.java` — la configuración de seguridad
- `application.properties` — el puerto y la URL de repo de usuario

### Paso 2: Decidir la solución

Evaluamos dos opciones (Gateway parcial vs Gateway total). Elegimos la total.

**Decisiones concretas:**

| Decisión | Elegimos | ¿Por qué? |
|---|---|---|
| ¿Gateway adentro o afuera? | Repo separado | Microservicio independiente |
| ¿Validar JWT con librería o llamando a repo de usuario? | Llamando a repo de usuario | repo de usuario es el dueño de la auth |
| ¿Pasar token o headers? | Headers | Más simple, desacoplado |
| ¿Qué headers? | X-User-Id, X-User-Email, X-User-Roles, X-User-Permissions | Lo justo para autorizar |

### Paso 3: Crear el proyecto Gateway

Creamos `Systeam-Gateway` desde cero con:

**Tecnologías:**
- Spring Boot 3.3.5
- Spring Cloud Gateway 2023.0.3
- Java 17
- jjwt (solo para parsear, no validar — lo usamos para extraer info del token si repo de usuario no responde)
- RestClient (para llamar a repo de usuario)

**Archivos creados:**

| Archivo | ¿Qué hace? |
|---|---|
| `pom.xml` | Dependencias: spring-cloud-starter-gateway, jjwt |
| `ApiGatewayApplication.java` | Clase main, @SpringBootApplication |
| `application.yml` | Config: puerto 8989, rutas, CORS |
| `AuthServiceClient.java` | Llama a repo de usuario para validar JWT |
| `ValidatedUser.java` | Record con userId, email, roles, permissions |
| `JwtAuthGatewayFilterFactory.java` | Filtro que valida JWT e inyecta headers |
| `.gitignore` | Ignora .env, target, etc. |
| `.env.example` | Template de variables de entorno |

### Paso 4: Codificar JwtAuthGatewayFilterFactory

Este es el corazón del Gateway.

```java
@Component
public class JwtAuthGatewayFilterFactory
        extends AbstractGatewayFilterFactory<Object> {

    private final AuthServiceClient authServiceClient;

    // Spring inyecta AuthServiceClient automáticamente
    public JwtAuthGatewayFilterFactory(AuthServiceClient authServiceClient) {
        super(Object.class);
        this.authServiceClient = authServiceClient;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            // 1. Sacar el header Authorization
            String authHeader = exchange.getRequest()
                    .getHeaders().getFirst("Authorization");

            // 2. Si no hay token → 401
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 3. Validar con repo de usuario
            return authServiceClient.validate(authHeader)
                    .map(user -> {
                        // 4. Inyectar headers con datos del usuario
                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-User-Id", user.userId().toString())
                                .header("X-User-Email", user.email())
                                .header("X-User-Roles",
                                        String.join(",", user.roles()))
                                .header("X-User-Permissions",
                                        String.join(",", user.permissions()))
                                .build();
                        return chain.filter(
                                exchange.mutate().request(mutated).build());
                    })
                    // 5. Si repo de usuario rechaza el token → 401
                    .orElseGet(() -> {
                        exchange.getResponse().setStatusCode(
                                HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    });
        };
    }
}
```

**¿Por qué extiende `AbstractGatewayFilterFactory`?**

Spring Cloud Gateway tiene su propio sistema de filtros. No es como los filtros de Spring MVC (OncePerRequestFilter). Si usáramos OncePerRequestFilter, el Gateway no lo ejecutaría correctamente.

**¿Por qué `GatewayFilter` y no `GlobalFilter`?**

`GlobalFilter` se ejecuta en TODAS las rutas. Nosotros necesitamos el filtro solo en las rutas que requieren autenticación (no en `/api/auth/**` que es login). Usando `GatewayFilter` podemos aplicarlo selectivamente en el `application.yml`.

### Paso 5: Configurar las rutas en application.yml

```yaml
server:
  port: 8989

spring:
  cloud:
    gateway:
      routes:
        # Auth Service (repo de usuario) — NO necesita JWT
        - id: auth-service
          uri: http://localhost:8080
          predicates:
            - Path=/api/auth/**

        # Project Service (nosotros) — necesita JWT
        - id: project-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/projects/**
          filters:
            - JwtAuth

        # Wallet Service (futuro)
        - id: wallet-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/wallet/**
          filters:
            - JwtAuth
```

**Detalle importante:** El filtro se llama `JwtAuth`, no `JwtAuthGatewayFilterFactory`. Spring Cloud Gateway automáticamente saca el sufijo `GatewayFilterFactory`. Si la clase se llama `JwtAuthGatewayFilterFactory`, en YAML se escribe `- JwtAuth`.

### Paso 6: Modificar el Project Service

**Cambios en `application.properties`:**

```properties
# Antes:
server.port=8081
auth.service.url=${AUTH_SERVICE_URL:http://localhost:8080}

# Después:
server.port=8081
# auth.service.url eliminado — el Gateway llama a repo de usuario
```

**Archivos ELIMINADOS:**
- `TokenValidationFilter.java` → ya no se necesita (el Gateway valida)
- `AuthServiceClient.java` → se movió al Gateway

**Archivo CREADO: `GatewayHeaderFilter.java`:**

```java
@Component
public class GatewayHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Leer el header que puso el Gateway
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            // No hay Gateway? Seguir sin autenticación
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Leer los demás headers
        String rolesHeader = request.getHeader("X-User-Roles");
        String permissionsHeader = request.getHeader("X-User-Permissions");

        // 3. Construir authorities (roles + permisos)
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        if (rolesHeader != null) {
            Arrays.stream(rolesHeader.split(","))
                    .forEach(r -> authorities.add(
                            new SimpleGrantedAuthority("ROLE_" + r.trim())));
        }
        if (permissionsHeader != null) {
            Arrays.stream(permissionsHeader.split(","))
                    .forEach(p -> authorities.add(
                            new SimpleGrantedAuthority(p.trim())));
        }

        // 4. Crear autenticación y setear en SecurityContext
        JwtPrincipal principal = new JwtPrincipal(Long.parseLong(userId),
                request.getHeader("X-User-Email"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 5. Continuar la cadena de filtros
        filterChain.doFilter(request, response);
    }
}
```

**¿Qué cambió con respecto al `TokenValidationFilter`?**

| Aspecto | TokenValidationFilter (antes) | GatewayHeaderFilter (ahora) |
|---|---|---|
| ¿Llama a repo de usuario? | ✅ Sí, en cada request | ❌ No |
| ¿Qué lee? | Header `Authorization` (JWT) | Headers `X-User-Id`, `X-User-Roles` |
| ¿Lógica? | Validar JWT + llamar HTTP | Solo leer headers |
| ¿Dependencia externa? | AuthServiceClient + Red | Ninguna |
| ¿Rendimiento? | +50ms por request por llamada HTTP | ~0ms (solo leer string) |

### Paso 7: Actualizar SecurityConfig

Cambiamos la referencia de `TokenValidationFilter` a `GatewayHeaderFilter`:

```java
// Antes:
http.addFilterBefore(tokenValidationFilter, UsernamePasswordAuthenticationFilter.class);

// Después:
http.addFilterBefore(gatewayHeaderFilter, UsernamePasswordAuthenticationFilter.class);
```

### Paso 8: Pushear a GitHub

```
git remote add origin https://github.com/tobiasavila04/Systeam-Gateway.git
git branch -M main
git push -u origin main
```

---

## 8. Ventajas de este cambio

### Tabla comparativa

| Característica | Antes (sin Gateway) | Después (con Gateway) |
|---|---|---|
| **Validación JWT** | Cada servicio valida por su cuenta | Solo el Gateway valida |
| **Llamadas a repo de usuario** | 1 por cada request a cada servicio | 1 por cada request al Gateway |
| **¿Qué sabe el frontend?** | Sabe los puertos de cada servicio | Solo sabe `localhost:8989` |
| **Rate limiting** | No hay | Se puede agregar en el Gateway |
| **CORS** | Configurado en cada servicio | Un solo lugar (Gateway) |
| **Logging** | Cada servicio loguea por separado | Gateway loguea todo |
| **Seguridad** | TokenValidationFilter en cada uno | GatewayHeaderFilter (solo headers) |
| **Agregar un servicio nuevo** | Hay que copiar TokenValidationFilter | Ya tiene JWT gratis (solo agregar ruta) |

### Ventaja clave: Escalabilidad

**Escenario hipotético:** Tenemos 5 servicios (Project, Wallet, Marketplace, Dividendos, Notificaciones) y 100 usuarios activos.

**Sin Gateway:**
- Cada usuario hace 10 requests/minuto = 1000 requests/minuto totales
- Cada request valida JWT llamando a repo de usuario
- repo de usuario recibe 1000 llamadas/minuto solo para validar
- Si repo de usuario se cae, NADIE puede usar NADA

**Con Gateway:**
- 1000 requests/minuto llegan al Gateway
- Gateway valida 1 vez cada request → 1000 llamadas a repo de usuario/minuto
- Pero los servicios de atrás **NO llaman a repo de usuario**
- Si repo de usuario se cae, los servicios siguen funcionando (si el Gateway cachea)
- Más importante: los 5 servicios no necesitan código de seguridad

### Ventaja para el equipo

- **repo de usuario** (dueño de User Service): puede cambiar su implementación interna sin afectar a nadie
- **Nosotros** (dueños de Project Service): no dependemos de repo de usuario para funcionar
- **Tobías** (frontend): solo necesita conocer `http://localhost:8989`

---

## 9. Lo que NO cambió

- ✅ La lógica de negocio (controladores, servicios, repositorios)
- ✅ La base de datos (PostgreSQL, Flyway migrations)
- ✅ Los smart contracts en Sepolia
- ✅ El Shared Model (librería común)
- ✅ El User Service de repo de usuario (sigue igual, solo cambia el puerto eventualmente)
- ✅ Los endpoints de la API (siguen siendo los mismos)

---

## 10. Lo que falta / Próximos pasos

### Para la arquitectura ideal (antes de la expo)

| Tarea | Responsable | ¿Por qué? |
|---|---|---|
| **Mover repo de usuario a puerto interno** | repo de usuario | Para que el Gateway sea el único punto de entrada |
| **Rate limiting** | Nosotros | Evitar abusos (ej: máximo 100 requests/minuto por usuario) |
| **Logging centralizado** | Nosotros | Gateway loguea todos los requests en un solo lugar |
| **Nginx / Proxy** | Nosotros | SSL, balanceo de carga |

### Para el futuro (post-expo)

| Servicio | Puerto | Estado |
|---|---|---|
| Wallet Service | 8083 | Pendiente |
| Notification Service | 8084 | Pendiente |
| Marketplace Service | 8085 | Pendiente |
| Dividend Service | 8086 | Pendiente |
| Y los demás... | 8087+ | Pendiente |

---

## 12. Preguntas y respuestas

### P: ¿Por qué no validaron el JWT directamente en el Gateway con una librería?

**R:** Porque el Gateway no debería saber cómo se genera el JWT. Si mañana el repo de usuario cambia el algoritmo de firma, el Gateway se rompe. Es mejor preguntarle al repo de usuario "che, este token está bien?" que intentar validarlo nosotros.

### P: ¿Y si el repo de usuario se cae? ¿El Gateway deja de funcionar?

**R:** Hoy sí, porque el Gateway llama a el repo de usuario en cada request. La solución a futuro es que el Gateway cachee las validaciones (ej: guardar el resultado 5 minutos). Si el repo de usuario se cae, el Gateway sigue funcionando con el cache hasta que expire. Pero para el MVP no lo implementamos.

### P: ¿Por qué Spring Cloud Gateway y no Nginx como gateway?

**R:** Nginx es excelente como balanceador de carga y proxy reverso, pero no puede validar JWT contra un servicio Java ni inyectar headers dinámicamente. Spring Cloud Gateway nos permite escribir lógica personalizada (como el filtro JwtAuth) en el mismo lenguaje que el resto del backend.

### P: ¿El Gateway no es un punto único de falla?

**R:** Sí, lo es. Si el Gateway se cae, no entra nadie. La solución es tener múltiples instancias del Gateway con un balanceador adelante (Nginx). Para el MVP con una sola instancia es suficiente.

### P: ¿Qué pasa si alguien llama directo al Project Service sin pasar por el Gateway?

**R:** El Project Service escucha en 8081, que es un puerto interno. En un entorno de producción, estos puertos no deberían ser accesibles desde afuera (firewall, Docker network interna). Para desarrollo, si alguien llama directo, el `GatewayHeaderFilter` no encuentra los headers X-User-Id y el request sigue sin autenticación — es un riesgo que asumimos en desarrollo.

### P: ¿Cuánto tiempo les llevó implementar esto?

**R:** Unas horas. La parte más larga fue entender el código existente y decidir la estrategia. La implementación fue rápida porque Spring Cloud Gateway abstrae la complejidad del ruteo y solo tuvimos que escribir el filtro personalizado.


## 7. Guia para levantar todo desde cero

### Requisitos previos

- Docker (para correr los servicios con imagenes pre-built)
- Node.js 18+ (para el frontend)
- Foundry (forge, cast) para interactuar con contratos
- Git

### Base de datos (Supabase)

La base de datos es compartida via Supabase. No es necesario levantar PostgreSQL localmente.

| Parametro | Valor |
|-----------|-------|
| Host | aws-1-sa-east-1.pooler.supabase.com |
| Puerto | 6543 |
| Database | postgres |
| Username | postgres.enweqjzmmgxhbsglwbbz |
| Password | semsu8-rezdad-Sixfuj |
| Connection String | jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:6543/postgres?prepareThreshold=0 |

### Paso 1: Project Service (Gestion de Proyectos)

Ejecutar en PowerShell:

`powershell
docker run -d --name systeam-gestion --restart unless-stopped -p 8081:8081 
  -e SPRING_DATASOURCE_URL=""jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:6543/postgres?prepareThreshold=0"" 
  -e SPRING_DATASOURCE_USERNAME=""postgres.enweqjzmmgxhbsglwbbz"" 
  -e SPRING_DATASOURCE_PASSWORD=""semsu8-rezdad-Sixfuj"" 
  -e SERVER_PORT=8081 
  -e BLOCKCHAIN_RPC_URL=""https://ethereum-sepolia-rpc.publicnode.com"" 
  -e BLOCKCHAIN_PROJECT_TOKEN=""0x9f2c766d0bd9bbb640422decdf0125be02c7d144"" 
  -e BLOCKCHAIN_IDEA_TOKEN=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_TOKEN_FACTORY=""0x23a81e62f190993c932ccea79308f8ff1ea558d9"" 
  -e BLOCKCHAIN_PAYMENT_GATEWAY=""0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10"" 
  -e BLOCKCHAIN_TREASURY=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_OFFERING_CONTRACT=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_DIVIDEND_DISTRIBUTOR=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_PRIVATE_KEY=""..."" 
  -e BLOCKCHAIN_USDC=""0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"" 
  -e BLOCKCHAIN_IDEAFY_FACTORY=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_INVESTMENT_SWAP=""0x4c53728b0a625dE5C80bF0807265cA2b91F769fa"" 
  -e BLOCKCHAIN_IDEA_SWAP=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_IDEA_MARKETPLACE=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  -e BLOCKCHAIN_IDEA_GOVERNANCE=""0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"" 
  ulisescasal/gestion-proyectos:latest
`

El servicio arranca en http://localhost:8081. Las migraciones de Flyway se ejecutan automaticamente al iniciar.

### Paso 2: User Service (Ulises)

Ejecutar en PowerShell:

`powershell
docker run -d --name systeam-backend -p 8080:8080 
  -e SPRING_DATASOURCE_URL=""jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:6543/postgres?prepareThreshold=0"" 
  -e SPRING_DATASOURCE_USERNAME=""postgres.enweqjzmmgxhbsglwbbz"" 
  -e SPRING_DATASOURCE_PASSWORD=""semsu8-rezdad-Sixfuj"" 
  -e JWT_PRIVATE_KEY=""..."" 
  -e JWT_PUBLIC_KEY=""..."" 
  -e APP_SECURITY_JWT_EXPIRATION_MS=""3600000"" 
  -e GOOGLE_CLIENT_ID=""..."" 
  -e GOOGLE_CLIENT_SECRET=""..."" 
  -e APP_OAUTH2_REDIRECT_URI=""https://sip-2026-systeam-frontend.vercel.app/oauth2/callback"" 
  ulisescasal/systeam-backend:latest
`

El servicio de autenticacion arranca en http://localhost:8080.

### Paso 3: Gateway

Clonar el repositorio Systeam-Gateway y seguir sus instrucciones. El Gateway corre en http://localhost:8989.

### Paso 4: Frontend

`ash
cd ideafy-frontend
npm install
npm run dev
`

El frontend arranca en http://localhost:5173 y debe apuntar al Gateway (http://localhost:8989) (ya lo hace).

### Paso 5: Orden de levantamiento

1. User Service (puerto 8080)
2. Project Service (puerto 8081)
3. Gateway (puerto 8989)
4. Frontend

### Stack tecnologico completo

| Componente | Tecnologia |
|---|---|
| Backend | Java 17, Spring Boot 3.3.5, Spring Security, Web3j |
| Base de datos | PostgreSQL 15 (Supabase), Flyway |
| Blockchain | Solidity 0.8.20, Foundry, OpenZeppelin |
| API Gateway | Spring Cloud Gateway 2023.0.3 |
| Frontend | React, Wagmi, Vite |
| Autenticacion | JWT, Auth Service|
| Contenedores | Docker Hub (ulisescasal/gestion-proyectos, ulisescasal/systeam-backend) |

## 8. Guia completa: Deployar los 8 contratos en Sepolia

### 1. Entender las dependencias entre contratos

| Contrato | Constructor | Depende de |
|---|---|---|
| IdeaToken.sol | `()` vacio | Nada |
| SubToken.sol | `()` vacio (upgradeable, implementation) | Nada |
| IdeafyFactory.sol | `()` vacio + necesita llamar `setSubTokenImplementation()` post-deploy | Nada |
| OfferingContract.sol | `(address _idea, address _factory)` | IdeaToken, IdeafyFactory |
| IdeaMarketplace.sol | `(address _idea)` | IdeaToken |
| IdeaGovernance.sol | `(address _idea)` | IdeaToken |
| IdeaSwap.sol | `(address _idea, address _usdc)` | IdeaToken, USDC |
| DividendDistributor.sol | `(address _idea, address _factory)` | IdeaToken, IdeafyFactory |

Orden de deploy: primero los que no dependen de nadie, despues los que necesitan direcciones de los anteriores.

### 2. Verificar que Foundry esta instalado

```powershell
$env:PATH = "C:\Users\Usuario\.foundry\bin;$env:PATH"
forge --version
# Debe mostrar algo como: forge Version: 1.7.1
```

### 3. Compilar los contratos

```powershell
cd blockchain
forge build
```

Si hay errores de compilacion, arreglarlos antes de seguir.

### 4. Escribir el script de deploy

Crear `blockchain/script/DeployAll.s.sol`:

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/IdeaToken.sol";
import "../contracts/SubToken.sol";
import "../contracts/IdeafyFactory.sol";
import "../contracts/OfferingContract.sol";
import "../contracts/DividendDistributor.sol";
import "../contracts/IdeaMarketplace.sol";
import "../contracts/IdeaGovernance.sol";
import "../contracts/IdeaSwap.sol";

contract DeployAll is Script {
    function run() external {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address backend = vm.addr(deployerPK);

        vm.startBroadcast(deployerPK);

        // 1. IdeaToken
        IdeaToken ideaToken = new IdeaToken();

        // 2. SubToken (implementation)
        SubToken subTokenImpl = new SubToken();

        // 3. IdeafyFactory
        IdeafyFactory factory = new IdeafyFactory();
        factory.setSubTokenImplementation(address(subTokenImpl));
        factory.grantRole(factory.CREATOR_ROLE(), backend);

        // 4. OfferingContract
        OfferingContract offering = new OfferingContract(address(ideaToken), address(factory));
        offering.grantRole(offering.ADMIN_ROLE(), backend);

        // 5. DividendDistributor
        DividendDistributor distributor = new DividendDistributor(address(ideaToken), address(factory));

        // 6. IdeaMarketplace
        IdeaMarketplace marketplace = new IdeaMarketplace(address(ideaToken));

        // 7. IdeaGovernance
        IdeaGovernance governance = new IdeaGovernance(address(ideaToken));

        // 8. IdeaSwap
        address usdc = vm.envAddress("BLOCKCHAIN_USDC");
        IdeaSwap swap = new IdeaSwap(address(ideaToken), usdc);

        vm.stopBroadcast();

        // Logs para copiar al .env
        console.log("BLOCKCHAIN_IDEA_TOKEN=", address(ideaToken));
        console.log("BLOCKCHAIN_SUB_TOKEN_IMPLEMENTATION=", address(subTokenImpl));
        console.log("BLOCKCHAIN_IDEAFY_FACTORY=", address(factory));
        console.log("BLOCKCHAIN_OFFERING_CONTRACT=", address(offering));
        console.log("BLOCKCHAIN_DIVIDEND_DISTRIBUTOR=", address(distributor));
        console.log("BLOCKCHAIN_IDEA_MARKETPLACE=", address(marketplace));
        console.log("BLOCKCHAIN_IDEA_GOVERNANCE=", address(governance));
        console.log("BLOCKCHAIN_IDEA_SWAP=", address(swap));
    }
}
```

### 5. Verificar que compila

```powershell
forge build
```

### 6. Verificar que la wallet tiene fondos

```powershell
# Obtener la direccion del deployer
cast wallet address --private-key "0xb7ddf518a66ac1b209065717fd07f08aeb78ed3f8235e88bc17935172d72be3e"
# Resultado: 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A

# Verificar saldo en Sepolia
cast balance --rpc-url "https://ethereum-sepolia-rpc.publicnode.com" 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
```

Si no tiene ETH, pedir en un faucet de Sepolia.

### 7. Calcular si alcanza

```powershell
forge script script/DeployAll.s.sol:DeployAll `
  --rpc-url "https://ethereum-sepolia-rpc.publicnode.com" `
  --private-key "0xb7ddf518a66ac1b209065717fd07f08aeb78ed3f8235e88bc17935172d72be3e" `
  --slow
```

El output muestra el estimado de gas. Debe alcanzar ~0.03 ETH para los 8 contratos.

### 8. Deployar

```powershell
$env:BLOCKCHAIN_USDC="0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
$env:BLOCKCHAIN_PRIVATE_KEY="0xb7ddf518a66ac1b209065717fd07f08aeb78ed3f8235e88bc17935172d72be3e"

forge script script/DeployAll.s.sol:DeployAll `
  --rpc-url "https://ethereum-sepolia-rpc.publicnode.com" `
  --private-key "0xb7ddf518a66ac1b209065717fd07f08aeb78ed3f8235e88bc17935172d72be3e" `
  --broadcast --slow
```

Flags importantes:
- `--broadcast` → envia las tx reales a la red
- `--slow` → espera entre cada tx para no rate-limitear
- Sin `--broadcast` solo simula (dry-run)

### 9. Actualizar el .env con las direcciones deployadas

Del output del script copiar las direcciones y reemplazar en `.env`:

```
BLOCKCHAIN_IDEA_TOKEN=0x33B18AEC70b0855B2b74e133e33e5d4Ba2Cd6ED3
BLOCKCHAIN_IDEAFY_FACTORY=0xBaEF76e4b3C27EC49b1530a571A44f2Cdaf74a79
BLOCKCHAIN_OFFERING_CONTRACT=0x85991B2Fb250Cea9D0D65fBa719340f290e0b653
BLOCKCHAIN_DIVIDEND_DISTRIBUTOR=0x0f2d8472b8fEFFd4106d30935D53714E0629950E
BLOCKCHAIN_IDEA_SWAP=0x9e8B8ad9C2210c94923C8df905DFa28e4Dd724d8
BLOCKCHAIN_IDEA_MARKETPLACE=0x9256b06aa14208A9B0172dd5cDe608e8fBEACc16
BLOCKCHAIN_IDEA_GOVERNANCE=0xC084E06697bf855Ce2DBBAbA6DbC0FB23f8b6764
```

### 10. Por que funciona con el backend

El backend (Java) ya tiene servicios que llaman a estos contratos:

| Servicio Java | Contrato que llama | Rol asignado en deploy |
|---|---|---|
| IdeafyFactoryService.java | IdeafyFactory.launchProject() | CREATOR_ROLE |
| OfferingContractService.java | OfferingContract.registerOffering() | ADMIN_ROLE |
| TokenizationService.java | IdeafyFactory (fallback: InvestmentSwap, TokenFactory) | N/A |
| DividendDistributorService | DividendDistributor.claimDividends() | Publico |
| IdeaSwapService | IdeaSwap.swap() | Publico |
| MarketplaceService | IdeaMarketplace.* | Publico |
| GovernanceService | IdeaGovernance.* | Publico |

Los contratos viejos (InvestmentSwap y TokenFactory) se quedan en `.env` pero el sistema los ignora porque IdeafyFactory funciona correctamente (primer nivel de fallback).
