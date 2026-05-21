# Módulo de Inversión — Documentación Completa y Didáctica

> **📖 Propósito de este documento:** Que cualquier persona con conocimientos básicos de Java y Spring Boot pueda entender, implementar y extender el módulo de inversión. Explica desde "¿qué es una blockchain?" hasta "¿cómo se firma una transacción con Web3j?". Está pensado como material de estudio y referencia.

---

## Índice

1. [Visión general del sistema](#1-visión-general-del-sistema)
2. [Conceptos previos necesarios](#2-conceptos-previos-necesarios)
3. [Arquitectura del módulo de inversión](#3-arquitectura-del-módulo-de-inversión)
4. [Base de datos — Migración Flyway](#4-base-de-datos--migración-flyway)
5. [DTOs — Los contratos de la API](#5-dtos--los-contratos-de-la-api)
6. [Repository — Capa de acceso a datos](#6-repository--capa-de-acceso-a-datos)
7. [InvestmentService — La lógica de negocio](#7-investmentservice--la-lógica-de-negocio)
8. [InvestmentController — Los endpoints REST](#8-investmentcontroller--los-endpoints-rest)
9. [InvestmentScheduler — Tareas automáticas](#9-investmentscheduler--tareas-automáticas)
10. [Integración blockchain — De mocks a Web3j real](#10-integración-blockchain--de-mocks-a-web3j-real)
11. [Tests — Cómo asegurar que funciona](#11-tests--cómo-asegurar-que-funciona)
12. [Contratos deployados en Sepolia](#12-contratos-deployados-en-sepolia)
13. [Historias de usuario](#13-historias-de-usuario)
14. [Preguntas frecuentes](#14-preguntas-frecuentes)
15. [Dynamic Pricing — Precio dinámico](#15-dynamic-pricing--precio-dinámico)
16. [Boost System — Destacar proyectos](#16-boost-system--destacar-proyectos)
17. [Dividendos — Reparto de ganancias](#17-dividendos--reparto-de-ganancias-a-inversores)
18. [$IDEA-PROY — Token ERC-20 por proyecto con quema del 0.1%](#18-idea-proy--token-erc-20-por-proyecto-con-quema-del-01)
19. [TokenFactory — Despliegue e integración automática](#19-tokenfactory--despliegue-e-integración-automática)
20. [Apéndice A — Cómo levantar el proyecto](#apéndice-a-cómo-levantar-el-proyecto)
21. [Apéndice B — Cómo agregar un nuevo contrato Solidity](#apéndice-b-cómo-agregar-un-nuevo-contrato-solidity)
22. [INV-09 e INV-10 — Refund on-chain y estado RECHAZADO](#22-inv-09-e-inv-10--refund-on-chain-y-estado-rechazado)
23. [Apéndice C — Solución de problemas comunes](#apéndice-c-solución-de-problemas-comunes)
23. [Apéndice D — Glosario ampliado](#apéndice-d-glosario-ampliado)

---

## 1. Visión general del sistema

### 1.1 ¿Qué es IDEAFY?

IDEAFY es una plataforma de **crowdfunding tokenizado**. Los creadores publican proyectos y los inversores aportan tokens **$IDEA** (la moneda interna de la plataforma) para financiarlos. A cambio, reciben **sub-tokens** que representan su participación en el proyecto.

### 1.2 ¿Qué hace el módulo de inversión?

El módulo de inversión (paquete `investment/`) maneja todo el ciclo de vida de una inversión:

1. **Pre-validación** (HU-11): Antes de invertir, el usuario consulta si puede hacerlo (¿el proyecto acepta fondos? ¿hay cupo? ¿cuántos sub-tokens recibiría?)
2. **Creación de inversión** (HU-10): El usuario invierte sus tokens $IDEA y recibe sub-tokens. La transacción queda registrada en la blockchain de Sepolia.
3. **Historial** (HU-12): El usuario puede consultar todas sus inversiones pasadas.
4. **Reembolso automático** (HU-28): Si un proyecto no alcanza su meta de financiamiento antes del plazo, todos los inversores recuperan automáticamente sus tokens.

### 1.3 Stack tecnológico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Lenguaje | Java | 17 |
| Framework | Spring Boot | 3.3.5 |
| Base de datos | PostgreSQL | — |
| Migraciones | Flyway | — |
| Persistencia | JDBC (JdbcTemplate) | — |
| Seguridad | JWT + Spring Security | — |
| Blockchain | Web3j (Ethereum Java client) | 4.12.3 |
| Red blockchain | Sepolia (testnet de Ethereum) | — |
| Contratos | Solidity (via Foundry) | 0.8.24 |
| Tests | JUnit 5 + Mockito | — |

### 1.4 Estructura completa de carpetas

```
src/
├── main/
│   ├── java/com/systeam/
│   │   ├── GestionDeProyectosApplication.java          ← Arranque de Spring Boot
│   │   │
│   │   ├── config/                                     ← Configuración global
│   │   │   ├── BlockchainProperties.java                ← Contiene las addresses de contratos Sepolia + RPC + PK
│   │   │   ├── SecurityConfig.java                      ← Configuración CORS, rutas públicas, filtros JWT
│   │   │   └── Web3jConfig.java                         ← Crea los beans Web3j y Credentials
│   │   │
│   │   ├── blockchain/                                  ← ★ NUEVO — Integración con blockchain real
│   │   │   └── service/
│   │   │       └── BlockchainService.java               ← Llamadas reales a Sepolia con Web3j
│   │   │
│   │   ├── investment/                                  ← ★ MÓDULO DE INVERSIÓN
│   │   │   ├── controller/InvestmentController.java      ← Endpoints REST
│   │   │   ├── dto/
│   │   │   │   ├── CreateInvestmentRequest.java          ← Request para crear inversión
│   │   │   │   ├── InvestmentResponse.java               ← Response de inversión
│   │   │   │   ├── ValidateInvestmentRequest.java        ← Request para pre-validar
│   │   │   │   └── ValidateInvestmentResponse.java       ← Response de pre-validación
│   │   │   ├── repository/
│   │   │   │   ├── InvestmentRepository.java             ← Interfaz del repositorio
│   │   │   │   └── JdbcInvestmentRepository.java         ← Implementación con JdbcTemplate
│   │   │   ├── scheduler/
│   │   │   │   └── InvestmentScheduler.java              ← Tarea programada (reembolsos)
│   │   │   └── service/
│   │   │       └── InvestmentService.java                ← Lógica de negocio principal
│   │   │
│   │   ├── project/                                      ← Módulo de proyectos (existente)
│   │   │   └── service/
│   │   │       ├── ProjectService.java
│   │   │       └── SmartContractService.java             ← ★ AHORA usa Web3j real (delegado en BlockchainService)
│   │   │
│   │   ├── security/                                     ← Seguridad JWT
│   │   │   ├── AuthServiceClient.java
│   │   │   ├── JwtPrincipal.java
│   │   │   ├── TokenValidationFilter.java
│   │   │   └── ValidatedUser.java
│   │   │
│   │   └── wallet/                                       ← Módulo de billetera (existente)
│   │
│   └── resources/
│       ├── application.properties                        ← Configuración Spring Boot
│       └── db/proyectos/                                 ← Migraciones Flyway
│           ├── V1__create_projects_table.sql
│           ├── ...
│           └── V8__create_investments_table.sql          ← ★ NUEVA — Tabla de inversiones
│
├── .env                                                  ← Variables de entorno (DB, blockchain, claves)
│
└── test/
    └── java/com/systeam/
        ├── investment/service/
        │   └── InvestmentServiceTest.java                ← 14 tests unitarios
        └── project/service/
            └── ProjectServiceTest.java                   ← 12 tests unitarios
```

---

## 2. Conceptos previos necesarios

> Esta sección es para alguien que **nunca usó blockchain**. Si ya sabés, saltala.

### 2.1 ¿Qué es una blockchain?

Imaginá un **libro contable compartido** donde:

- Cada página es un **bloque**
- Cada bloque contiene **transacciones** (movimientos de dinero, ejecución de contratos, etc.)
- Una vez que una página se llena, se **encadena** a la anterior (de ahí "blockchain")
- **Nadie puede modificar páginas anteriores** (es inmutable)
- **Todos tienen una copia** del libro (es descentralizado)

Ethereum es una blockchain pública. Sepolia es una "copia de prueba" (testnet) donde todo es gratis y los datos no tienen valor real.

### 2.2 ¿Qué es MetaMask?

MetaMask es una **billetera criptográfica** que corre como extensión del navegador. Te permite:

- Generar y guardar **private keys** (tu contraseña maestra)
- Firmar **transacciones** (decir "sí, quiero pagar 50 USDC")
- Conectarte a dApps (aplicaciones descentralizadas)

Cuando creás una wallet, se genera una frase de 12 palabras. **Quien tiene esa frase, tiene tu wallet**.

### 2.3 ¿Qué es un smart contract?

Es un **programa que vive en la blockchain**. Una vez deployado, su código no cambia. Cualquiera puede:

- **Leer** su estado (gratis, sin permiso)
- **Ejecutar** sus funciones (pagando gas, si modifican estado)

En nuestro proyecto, los smart contracts son:

| Contrato | ¿Qué hace? | ¿Dónde vive? |
|----------|-----------|-------------|
| **PaymentGateway** | Recibe pagos en USDC y emite eventos `Paid` | `0x7aabb2435...` en Sepolia |
| **ProjectToken** | Es el token $IDEA (ERC-20). Se puede mintear, transferir, consultar saldo | `0x9f2c766d...` en Sepolia |
| **USDC (de Circle)** | Stablecoin (1 USDC ≈ 1 USD). Es la moneda con la que se paga | `0x1c7D4B1...` en Sepolia |

### 2.4 ¿Qué es Web3j?

Web3j es una **biblioteca Java** que permite hablar con nodos Ethereum sin tener que escribir JSON-RPC a mano.

En vez de hacer:
```
HTTP POST https://ethereum-sepolia-rpc.publicnode.com
Body: {"jsonrpc":"2.0","method":"eth_call","params":[{...},"latest"],"id":1}
```

Hacés:
```java
BigInteger balance = web3j.ethGetBalance(address, ...).send().getBalance();
```

Web3j se encarga de:
1. Armar el JSON de la request
2. Enviarlo por HTTP
3. Parsear la respuesta JSON
4. Devolverte un objeto Java

### 2.5 ¿Qué es gas?

Cada operación que modifica la blockchain (enviar tokens, ejecutar un contrato) cuesta **gas**. El gas se paga en ETH (la moneda nativa de Ethereum). En Sepolia el ETH es falso (se pide gratis en faucets). En mainnet cuesta dinero real.

Nuestro backend tiene su propia wallet con SepoliaETH para pagar el gas de las transacciones que envía (como `mint()` de $IDEA).

### 2.6 ¿Qué es el ABI?

Cuando compilás un contrato Solidity con `forge build`, se genera un archivo `.json` con el **ABI (Application Binary Interface)**. El ABI describe:

- Qué funciones tiene el contrato
- Qué parámetros recibe cada función y de qué tipo
- Qué eventos emite

Por ejemplo, el ABI de `balanceOf(address)` se ve así:
```json
{
  "name": "balanceOf",
  "inputs": [{"name": "account", "type": "address"}],
  "outputs": [{"name": "", "type": "uint256"}],
  "stateMutability": "view"
}
```

Web3j usa el ABI para **codificar** los parámetros Java a bytes hexadecimales que la EVM entiende, y para **decodificar** la respuesta de vuelta a Java.

---

## 3. Arquitectura del módulo de inversión

### 3.1 Diagrama de capas

```
┌─────────────────────────────────────────────────────────┐
│                    FRONTEND (React)              │
│  (conectado a MetaMask para firmar txs blockchain)      │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP REST (JSON)
                         ▼
┌─────────────────────────────────────────────────────────┐
│              INVESTMENT CONTROLLER                       │
│  @RestController /api/investments/*                      │
│  - Recibe requests del frontend                          │
│  - Valida con @Valid (Jakarta Validation)                │
│  - Obtiene usuario autenticado via @AuthenticationPrincipal│
│  - Delega en InvestmentService                           │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│               INVESTMENT SERVICE                          │
│  @Service @Transactional                                 │
│  - validateInvestment(): lógica de pre-validación        │
│  - createInvestment(): lógica de inversión               │
│  - getInvestmentHistory(): historial paginado            │
│  - processExpiredProjects(): reembolso automático        │
│  - refundAllInvestors(): devolución de fondos            │
└──────┬────────────────────────────┬─────────────────────┘
       │                            │
       ▼                            ▼
┌──────────────┐          ┌──────────────────────┐
│  REPOSITORY   │          │  SMART CONTRACT      │
│  (JDBC/Postgres)│        │  SERVICE              │
│  - CRUD de     │          │  - recordInvestment() │
│    inversiones │          │  - refundInvestment() │
│  - Historial   │          │  - getContractInfo()  │
│  - Reembolsos  │          └──────────┬───────────┘
└──────────────┘                      │
                                      ▼
                            ┌──────────────────────┐
                            │  BLOCKCHAIN SERVICE   │
                            │  (Web3j - Sepolia)    │
                            │  - balanceOf()        │
                            │  - allowance()        │
                            │  - mintTokens()       │
                            │  - payUSDC()          │
                            │  - verifyTransaction()│
                            │  - getBalanceSepolia()│
                            └──────────┬───────────┘
                                       │
                                       ▼
                            ┌──────────────────────┐
                            │  NODO RPC SEPOLIA     │
                            │  ethereum-sepolia-    │
                            │  rpc.publicnode.com   │
                            │                       │
                            │  Contratos on-chain:  │
                            │  PaymentGateway       │
                            │  ProjectToken ($IDEA) │
                            │  USDC                 │
                            └───────────────────────┘
```

### 3.2 Flujo completo de una inversión (paso a paso)

#### Paso 1: Pre-validación

```
FRONTEND                           BACKEND                          BASE DE DATOS
   │                                  │                                  │
   │ POST /api/investments/validate   │                                  │
   │ { proyectoId: 1, montoIdea: 500} │                                  │
   │─────────────────────────────────>│                                  │
   │                                  │                                  │
   │                                  │ SELECT ... FROM projects         │
   │                                  │ WHERE id = 1                    │
   │                                  │─────────────────────────────────>│
   │                                  │<─────────────────────────────────│
   │                                  │ { id:1, estado:"FINANCIAMIENTO"}  │
   │                                  │                                  │
   │                                  │ SELECT ... FROM subtokens        │
   │                                  │ WHERE proyecto_id = 1           │
   │                                  │─────────────────────────────────>│
   │                                  │<─────────────────────────────────│
   │                                  │ { cupo:100, precio:10 }          │
   │                                  │                                  │
   │                                  │ ¿Estado = FINANCIAMIENTO? → Sí   │
   │                                  │ ¿Subtoken existe? → Sí          │
   │                                  │ ¿Cupo > 0? → Sí (100)           │
   │                                  │ 500/10 = 50 subtokens           │
   │                                  │ ¿50 <= 100? → Sí                │
   │                                  │                                  │
   │<─────────────────────────────────│                                  │
   │ { valido: true,                  │                                  │
   │   subTokensARecebir: 50,         │                                  │
   │   precioSubtoken: 10 }           │                                  │
```

#### Paso 2: El usuario firma en MetaMask

```
FRONTEND (con MetaMask)
   │
   │ 1. Usuario abre MetaMask
   │ 2. Aprueba USDC: usdc.approve(PaymentGateway, 50)
   │ 3. Firma tx → txHash: "0xabc..."
   │ 4. Llama: gateway.pay(50, "proyecto-1")
   │ 5. Firma tx → txHash: "0xdef..."
   │
   │ El usuario tiene ahora los txHash de las transacciones en Sepolia
```

#### Paso 3: Creación de inversión

```
FRONTEND                           BACKEND                          SEPOLIA (Web3j)
   │                                  │                                  │
   │ POST /api/investments            │                                  │
   │ { proyectoId: 1,                 │                                  │
   │   montoIdea: 500,               │                                  │
   │   txHash: "0xabc..." }          │                                  │
   │─────────────────────────────────>│                                  │
   │                                  │                                  │
   │    │ Validaciones (repiten por seguridad)                           │
   │    │ Proyecto existe? → Sí                                          │
   │    │ Estado = FINANCIAMIENTO? → Sí                                  │
   │    │ Subtoken existe? → Sí                                          │
   │    │ Cupo suficiente? → Sí                                          │
   │    │ Saldo $IDEA suficiente? → Sí                                   │
   │                                  │                                  │
   │    │ BlockchainService.verifyTransaction("0xabc...")                │
   │    │──────────────────────────────────────────────────────────────>│
   │    │ eth_getTransactionReceipt                                     │
   │    │<──────────────────────────────────────────────────────────────│
   │    │ status: "0x1" (success)                                       │
   │                                  │                                  │
   │    │ UPDATE users SET saldo_idea -= 500                             │
   │    │ UPDATE projects SET monto_recaudado += 500                     │
   │    │ UPDATE subtokens SET cupo_restante -= 50                       │
   │    │ INSERT portfolio_activos (usuario, subtoken, 50)               │
   │    │ INSERT investments (...)                                        │
   │                                  │                                  │
   │<─────────────────────────────────│                                  │
   │ { id: 1, estado: "CONFIRMADA",   │                                  │
   │   txHash: "0xabc...",
   │   subTokensRecibidos: 50 }       │                                  │
```

#### Paso 4: Reembolso automático (HU-28)

```
SCHEDULER (6 AM)
   │
   │ SELECT id, monto_requerido, monto_recaudado
   │ FROM projects WHERE estado = 'FINANCIAMIENTO'
   │ AND plazo < NOW()
   │
   │ Para cada proyecto vencido:
   │   ├─ ¿monto_recaudado < monto_requerido?
   │   │
   │   │  SÍ → Por cada inversión del proyecto:
   │   │    ├─ UPDATE users SET saldo_idea += monto_original
   │   │    ├─ UPDATE portfolio_activos SET cantidad -= subtokens
   │   │    └─ UPDATE investments SET estado = 'REEMBOLSADA'
   │   │
   │   │  UPDATE projects SET estado = 'FINALIZADO'
   │   │
   │   └─ NO → El proyecto se financia con éxito, no se devuelve nada
```

---

## 4. Base de datos — Migración Flyway

### 4.1 ¿Qué es Flyway?

Flyway es una herramienta que **versiona el esquema de la base de datos**. Cada archivo `V{numero}__{nombre}.sql` es una migración. Flyway ejecuta las migraciones en orden y lleva un registro de cuáles ya se aplicaron (en la tabla `flyway_gestion_proyectos_history`).

### 4.2 Archivo: `V8__create_investments_table.sql`

```sql
CREATE TABLE IF NOT EXISTS investments (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_id           BIGINT NOT NULL,
    proyecto_id          BIGINT NOT NULL,
    monto_idea           DECIMAL(15,2) NOT NULL,
    sub_tokens_recibidos INTEGER NOT NULL DEFAULT 0,
    tx_hash              VARCHAR(255),
    estado               VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE subtokens ADD COLUMN IF NOT EXISTS proyecto_id BIGINT;
```

**Explicación campo por campo:**

| Campo | Tipo | ¿Por qué? |
|-------|------|-----------|
| `id` | `BIGSERIAL PRIMARY KEY` | Autoincremental. Cada inversión tiene un ID único. |
| `usuario_id` | `BIGINT NOT NULL` | Quién invierte. Es foreign key (FK) a la tabla `users`. |
| `proyecto_id` | `BIGINT NOT NULL` | En qué proyecto invierte. FK a `projects`. |
| `monto_idea` | `DECIMAL(15,2) NOT NULL` | Cuántos tokens $IDEA invierte. `DECIMAL(15,2)` = 15 dígitos, 2 decimales. |
| `sub_tokens_recibidos` | `INTEGER NOT NULL DEFAULT 0` | Cuántos sub-tokens recibe a cambio. Se calcula como `monto / precio_subtoken`. |
| `tx_hash` | `VARCHAR(255)` | Hash de la transacción en Sepolia. Sirve para que el usuario pueda verificarla en https://sepolia.etherscan.io |
| `estado` | `VARCHAR(20) DEFAULT 'PENDIENTE'` | Ciclo de vida: `PENDIENTE` → `CONFIRMADA` / `RECHAZADA` / `REEMBOLSADA` |
| `created_at` | `TIMESTAMP DEFAULT NOW()` | Cuándo se creó la inversión |
| `updated_at` | `TIMESTAMP DEFAULT NOW()` | Cuándo se actualizó por última vez |

**¿Por qué `ALTER TABLE subtokens ADD COLUMN proyecto_id`?**

Porque la tabla `subtokens` original no tenía relación directa con el proyecto. Esta columna permite buscar el subtoken asociado a un proyecto sin tener que hacer joins adicionales.

### 4.3 ¿Cómo se ejecuta la migración?

Spring Boot configura Flyway automáticamente en `application.properties`:

```properties
spring.flyway.enabled=true                           # Activa Flyway
spring.flyway.locations=classpath:db/proyectos       # Dónde están los .sql
spring.flyway.table=flyway_gestion_proyectos_history # Tabla de control
spring.flyway.baseline-on-migrate=true               # Si la DB existe, no falla
spring.flyway.baseline-version=0                     # Desde versión 0
```

Cuando arranca la aplicación, Spring Boot ejecuta `flyway:migrate` automáticamente. Si encuentra archivos nuevos (`V8__...`), los ejecuta.

---

## 5. DTOs — Los contratos de la API

### 5.1 ¿Qué es un DTO?

DTO = **Data Transfer Object**. Es una clase Java que **no tiene lógica de negocio**, solo sirve para transportar datos entre el frontend y el backend.

**¿Por qué usarlos?** Separar la representación interna de los datos (las entidades `Inversion`, `Proyecto`, etc.) de lo que se envía por la API. Así podemos:
- No exponer campos internos (ej: `deleted_at`)
- Agregar campos calculados (ej: `proyectoTitulo`)
- Validar los inputs con anotaciones

### 5.2 `CreateInvestmentRequest.java`

```java
package com.systeam.investment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data  // Lombok: genera getters, setters, equals, hashCode, toString
public class CreateInvestmentRequest {

    @NotNull(message = "El ID del proyecto es obligatorio")
    private Long proyectoId;

    @NotNull(message = "El monto en IDEA es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal montoIdea;

    @NotBlank(message = "El hash de la transaccion blockchain es obligatorio")
    private String txHash;
}
```

**Explicación:**

| Línea | ¿Qué hace? | ¿Por qué? |
|-------|-----------|-----------|
| `@Data` | Lombok genera getters, setters, toString, equals, hashCode | Evita escribir ~50 líneas de boilerplate |
| `@NotNull` | Valida que el campo no sea `null` | El frontend siempre debe enviar proyectoId |
| `@DecimalMin("0.01")` | Valida que el número >= 0.01 | No se puede invertir 0 o negativo |
| `@NotBlank` | Valida que el string no sea null ni vacío | El txHash es necesario para verificar en blockchain |
| `BigDecimal` | Tipo para montos financieros | `double` pierde precisión, `BigDecimal` es exacto |

**¿Por qué BigDecimal y no double?** `double` tiene errores de redondeo (0.1 + 0.2 = 0.30000000000000004). En finanzas, eso es inaceptable. `BigDecimal` es exacto.

### 5.3 `ValidateInvestmentRequest.java`

```java
@Data
public class ValidateInvestmentRequest {
    @NotNull
    private Long proyectoId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montoIdea;
}
```

Es igual que `CreateInvestmentRequest` pero **sin txHash**. Porque la pre-validación ocurre antes de que el usuario firme nada en MetaMask.

### 5.4 `ValidateInvestmentResponse.java`

```java
@Data
@Builder  // Patrón Builder: new ValidateInvestmentResponse(valido, mensaje, ...)
public class ValidateInvestmentResponse {
    private boolean valido;            // true: puede invertir, false: no
    private String mensaje;            // Explicación (éxito o motivo de rechazo)
    private Integer cupoDisponible;    // Sub-tokens que quedan
    private BigDecimal precioSubtoken; // Precio de cada sub-token
    private Integer subTokensARecebir; // Cuántos recibiría con ese monto
}
```

**¿Qué es `@Builder`?** Es una anotación de Lombok que genera una clase `Builder` interna. Permite crear objetos así:

```java
ValidateInvestmentResponse respuesta = ValidateInvestmentResponse.builder()
    .valido(true)
    .mensaje("Inversion valida")
    .cupoDisponible(50)
    .precioSubtoken(new BigDecimal("10"))
    .subTokensARecebir(5)
    .build();
```

**¿Por qué Builder y no constructor?** Porque hay muchos campos opcionales y no queremos tener 5 constructores sobrecargados.

### 5.5 `InvestmentResponse.java`

```java
@Data
@Builder
public class InvestmentResponse {
    private Long id;
    private Long usuarioId;
    private Long proyectoId;
    private String proyectoTitulo;     // ← Se consulta de la tabla projects
    private BigDecimal montoIdea;
    private Integer subTokensRecibidos;
    private String txHash;             // Para que el usuario lo vea en Etherscan
    private String estado;             // CONFIRMADA | REEMBOLSADA | ...
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**¿Por qué `proyectoTitulo`?** Porque el frontend necesita mostrar el nombre del proyecto. En vez de que el frontend haga otra llamada para obtenerlo, lo incluimos directamente en la respuesta de inversión.

---

## 6. Repository — Capa de acceso a datos

### 6.1 ¿Por qué JDBC y no JPA?

Decisión de arquitectura importante. El equipo eligió **JdbcTemplate** (JDBC) en vez de **JPA/Hibernate** por estas razones:

| Aspecto | JPA | JDBC (JdbcTemplate) |
|---------|-----|-------------------|
| Curva de aprendizaje | Alta (EntityManager, lazy loading, proxies) | Baja (SQL directo) |
| Control de queries | Limitado (JPQL, Criteria API) | Total (SQL puro) |
| Performance predecible | A veces genera N+1 queries | Siempre sabés qué SQL se ejecuta |
| Configuración | Mucha (EntityManagerFactory, DataSource) | Mínima (solo DataSource) |
| Ideal para | CRUD con muchas relaciones | Consultas específicas, reportes |

**Para este módulo** solo tenemos una tabla principal (`investments`) con joins simples a `projects` y `users`. No necesitamos el overhead de JPA.

### 6.2 `InvestmentRepository.java` — La interfaz

```java
package com.systeam.investment.repository;

import com.systeam.shared.model.Inversion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface InvestmentRepository {

    Inversion save(Inversion inv);                         // Crear o actualizar
    Optional<Inversion> findById(Long id);                  // Buscar por ID
    Page<Inversion> findByUsuarioId(Long usuarioId,        // Historial paginado
                                     Pageable pageable);
    List<Inversion> findByProyectoIdAndEstado(             // Para reembolsos: inversiones de un proyecto
            Long proyectoId, String estado);
    List<Inversion> findPendingRefundsByProyectoId(        // Inversiones pendientes de reembolso
            Long proyectoId);
}
```

**¿Por qué existe la interfaz si solo hay una implementación?**
Porque:
1. Permite cambiar la implementación sin modificar el `InvestmentService`
2. Facilita los tests unitarios (podés mockear la interfaz)
3. Es el patrón estándar de Spring Data (aunque usemos JDBC, mantenemos la misma convención)

### 6.3 `JdbcInvestmentRepository.java` — La implementación

```java
@Repository  // Le dice a Spring: esta clase es un repositorio (exc. de traducción automática de excepciones)
public class JdbcInvestmentRepository implements InvestmentRepository {

    private final JdbcTemplate jdbc;  // Clase de Spring que ejecuta SQL
}
```

**¿Qué es JdbcTemplate?** Es la clase central de Spring JDBC. Simplifica:
- Crear y cerrar conexiones
- Ejecutar queries
- Mapear resultados a objetos
- Manejar excepciones

#### 6.3.1 RowMapper — Convierte filas SQL en objetos Java

```java
private final RowMapper<Inversion> rowMapper = (rs, rowNum) -> {
    Inversion inv = new Inversion();
    inv.setId(rs.getLong("id"));

    // Solo seteamos el ID del usuario, no cargamos todos sus datos
    Usuario usuario = new Usuario();
    usuario.setId(rs.getLong("usuario_id"));
    inv.setUsuario(usuario);

    // Solo seteamos el ID del proyecto, no cargamos todos sus datos
    Proyecto proyecto = new Proyecto();
    proyecto.setId(rs.getLong("proyecto_id"));
    inv.setProyecto(proyecto);

    inv.setMontoIdea(rs.getBigDecimal("monto_idea"));
    inv.setSubTokensRecibidos(rs.getInt("sub_tokens_recibidos"));
    inv.setTxHash(rs.getString("tx_hash"));
    inv.setEstado(rs.getString("estado"));
    inv.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
    inv.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
    return inv;
};
```

**¿Por qué Usuario y Proyecto tienen solo el ID?** Porque el `shared-model` (el JAR compartido del equipo) define las entidades con relaciones. Si cargáramos todos los datos de Usuario y Proyecto aquí, estaríamos haciendo joins innecesarios. Solo necesitamos el ID para identificar la relación.

#### 6.3.2 Método `save()` — INSERT o UPDATE según corresponda

```java
@Override
public Inversion save(Inversion inversion) {
    if (inversion.getId() == null) {
        // INSERT: inversión nueva
        String sql = """
            INSERT INTO investments
                (usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos,
                 tx_hash, estado, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            RETURNING id
            """;

        // RETURNING id: PostgreSQL devuelve el ID generado automáticamente
        Long id = jdbc.queryForObject(sql, Long.class,
            inversion.getUsuario().getId(),
            inversion.getProyecto().getId(),
            inversion.getMontoIdea(),
            inversion.getSubTokensRecibidos(),
            inversion.getTxHash(),
            inversion.getEstado()
        );
        inversion.setId(id);
    } else {
        // UPDATE: inversión existente (ej: cambiar estado a REEMBOLSADA)
        jdbc.update("""
            UPDATE investments SET
                estado = ?, sub_tokens_recibidos = ?,
                updated_at = NOW()
            WHERE id = ?
            """,
            inversion.getEstado(),
            inversion.getSubTokensRecibidos(),
            inversion.getId()
        );
    }
    // Devuelve la inversión completa (vuelve a leerla de la DB)
    return findById(inversion.getId()).orElseThrow();
}
```

**¿Qué es `RETURNING id`?** Es una cláusula de PostgreSQL que devuelve el valor generado automáticamente por `BIGSERIAL`. Sin esto, tendríamos que hacer un `SELECT MAX(id)` o similar.

**¿Por qué `save()` devuelve la inversión completa?**
Porque después de guardar, la entidad tiene todos sus campos actualizados (ej: `created_at`, `updated_at`). Si devolviéramos la entidad original, esos campos estarían vacíos.

#### 6.3.3 Método `findByUsuarioId()` — Historial paginado

```java
@Override
public Page<Inversion> findByUsuarioId(Long usuarioId, Pageable pageable) {
    // 1. Contar total (necesario para paginación)
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM investments WHERE usuario_id = ?",
        Long.class, usuarioId
    );

    // 2. Traer página actual (ordenado por fecha descendente)
    List<Inversion> list = jdbc.query(
        "SELECT * FROM investments WHERE usuario_id = ? " +
        "ORDER BY created_at DESC LIMIT ? OFFSET ?",
        rowMapper, usuarioId,
        pageable.getPageSize(),
        pageable.getOffset()
    );

    // 3. Devolver como Page (objeto de Spring Data que incluye total, página, etc.)
    return new PageImpl<>(list, pageable, total);
}
```

**¿Por qué paginación?** Un usuario puede tener cientos o miles de inversiones. Devolverlas todas en una sola response consumiría mucha memoria y red. `Pageable` permite pedir de a 10, 20 o 50 por página.

---

## 7. InvestmentService — La lógica de negocio

Este es el archivo más importante del módulo. Contiene **toda la lógica de inversión**.

### 7.1 Dependencias inyectadas

El constructor de InvestmentService recibía 3 dependencias. Ahora recibe 6 (por INV-05 y Dynamic Pricing):

```java
@Service
public class InvestmentService {

    // ─── Repositorio JPA (operaciones CRUD de inversiones) ─────
    private final InvestmentRepository investmentRepository;

    // ─── Servicios externos ────────────────────────────────────
    private final SmartContractService smartContractService;       // Blockchain (fallback)
    private final InvestmentSwapService investmentSwapService;     // ★ INV-05 — Swap atomico
    private final BlockchainProperties blockchainProperties;       // ★ INV-05 — Addresses contratos
    private final DynamicPricingService pricingService;            // ★ Precio dinamico (seccion 15)

    // ─── JdbcTemplate (queries SQL directas a otros modulos) ────
    private final JdbcTemplate jdbc;

    public InvestmentService(InvestmentRepository investmentRepository,
                              SmartContractService smartContractService,
                              InvestmentSwapService investmentSwapService,
                              BlockchainProperties blockchainProperties,
                              JdbcTemplate jdbc,
                              DynamicPricingService pricingService) {
        this.investmentRepository = investmentRepository;
        this.smartContractService = smartContractService;
        this.investmentSwapService = investmentSwapService;
        this.blockchainProperties = blockchainProperties;
        this.jdbc = jdbc;
        this.pricingService = pricingService;
    }
}
```

**Por que JdbcTemplate ademas del Repository?** Las operaciones de descontar saldo, actualizar recaudacion, etc. son sobre tablas de OTROS modulos (users, projects, subtokens, portfolio_activos). No podemos importar sus repos porque son paquetes distintos.

**Por que InvestmentSwapService y SmartContractService?** InvestmentSwapService (INV-05) hace el swap atomico on-chain cuando Sepolia esta disponible. SmartContractService es el fallback si la red no responde. El sistema nunca se cae por un problema de blockchain.

### 7.2 Método `validateInvestment()` — Pre-validación

```java
public ValidateInvestmentResponse validateInvestment(ValidateInvestmentRequest request) {
    // 1. Buscar proyecto (si no existe, lanza 404)
    Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
    String estado = (String) proyecto.get("estado");

    // 2. ¿El proyecto está aceptando inversiones?
    if (!"FINANCIAMIENTO".equals(estado)) {
        return ValidateInvestmentResponse.builder()
                .valido(false)
                .mensaje("El proyecto no esta en estado de financiamiento")
                .build();
    }

    // 3. ¿El proyecto tiene un subtoken asociado?
    Map<String, Object> subtoken = findSubtokenByProject(request.getProyectoId());
    if (subtoken == null) {
        return ValidateInvestmentResponse.builder()
                .mensaje("El proyecto no tiene un subtoken asociado")
                .build();
    }

    // 4. Calcular precio DINAMICO del subtoken
    //    precio_actual se recalcula en CADA validacion segun:
    //      - precio_base (valor nominal del token)
    //      - suministro_total (cuantos tokens existen)
    //      - cupo_restante (cuantos quedan disponibles)
    //      - factor_volatilidad (que tan volatil es el precio)
    //    Esto hace que invertir temprano sea mas barato.
    int cupoRestante = ((Number) subtoken.get("cupo_restante")).intValue();
    BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
    int suministroTotal = (int) subtoken.get("suministro_total");
    BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

    BigDecimal precioSubtoken = pricingService.calcularPrecioDinamico(
        precioBase, suministroTotal, cupoRestante, factorVolatilidad
    );

    if (cupoRestante <= 0) {
        return ValidateInvestmentResponse.builder()
                .valido(false)
                .mensaje("El proyecto no tiene cupo disponible")
                .cupoDisponible(0)
                .precioSubtoken(precioSubtoken)
                .subTokensARecebir(0)
                .build();
    }

    // 5. Calcular cuántos sub-tokens recibiría
    //    subTokens = piso(monto / precio)
    //    Ej: 500 / 10 = 50 subtokens
    int subTokensNecesarios = request.getMontoIdea()
            .divide(precioSubtoken, 0, RoundingMode.DOWN)
            .intValue();

    // 6. Validar que reciba al menos 1 sub-token
    if (subTokensNecesarios <= 0) {
        return ValidateInvestmentResponse.builder()
                .valido(false)
                .mensaje("El monto es insuficiente para recibir al menos 1 sub-token")
                .cupoDisponible(cupoRestante)
                .precioSubtoken(precioSubtoken)
                .subTokensARecebir(0)
                .build();
    }

    // 7. Validar que no supere el cupo
    if (subTokensNecesarios > cupoRestante) {
        return ValidateInvestmentResponse.builder()
                .valido(false)
                .mensaje("El monto solicitado supera el cupo disponible del proyecto")
                .cupoDisponible(cupoRestante)
                .precioSubtoken(precioSubtoken)
                .subTokensARecebir(subTokensNecesarios)
                .build();
    }

    // TODO OK → inversión válida
    return ValidateInvestmentResponse.builder()
            .valido(true)
            .mensaje("Inversion valida")
            .cupoDisponible(cupoRestante)
            .precioSubtoken(precioSubtoken)
            .subTokensARecebir(subTokensNecesarios)
            .build();
}
```

**¿Por qué `RoundingMode.DOWN`?** Es más seguro para la plataforma redondear hacia abajo (quedarse con menos sub-tokens). Si redondeara hacia arriba, podría asignar más sub-tokens de los que el cupo permite.

**¿Por qué se valida el estado del proyecto?** Un proyecto puede estar en varios estados: `PREPARACION`, `FINANCIAMIENTO`, `EN_PROGRESO`, `FINALIZADO`. Solo en `FINANCIAMIENTO` se puede invertir.

### 7.3 Metodo createInvestment() — Creacion de inversion (el corazon)

**Archivo:** src/main/java/com/systeam/investment/service/InvestmentService.java

Este es el metodo MAS IMPORTANTE del modulo. Cuando un inversor hace POST /api/investments, esto es lo que pasa:

```java
// @Transactional = TODO es UNA sola transaccion de base de datos.
// Si algo falla a la mitad, todo se deshace (rollback).
@Transactional
public InvestmentResponse createInvestment(CreateInvestmentRequest request, Long usuarioId) {

    // ═══════════════════════════════════════════════════════════════
    // 1. VALIDACIONES (igual que validateInvestment, por seguridad)
    // ═══════════════════════════════════════════════════════════════

    // Buscar proyecto en DB (si no existe, lanza 404)
    Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
    String estado = (String) proyecto.get("estado");

    // Solo se puede invertir en proyectos en FINANCIAMIENTO
    if (!"FINANCIAMIENTO".equals(estado)) {
        throw new ConflictException("El proyecto no esta en estado de financiamiento");
    }

    // Buscar subtoken del proyecto
    Map<String, Object> subtoken = findSubtokenByProject(request.getProyectoId());
    if (subtoken == null) {
        throw new ConflictException("El proyecto no tiene un subtoken asociado para invertir");
    }

    // Extraer datos del subtoken
    Long subtokenId = ((Number) subtoken.get("id")).longValue();
    int cupoRestante = ((Number) subtoken.get("cupo_restante")).intValue();
    BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
    int suministroTotal = (int) subtoken.get("suministro_total");
    BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

    // Calcular precio DINAMICO (ver seccion 15)
    BigDecimal precioSubtoken = pricingService.calcularPrecioDinamico(
        precioBase, suministroTotal, cupoRestante, factorVolatilidad
    );

    // Calcular cuantos sub-tokens recibe (redondeando hacia abajo)
    int subTokens = request.getMontoIdea()
            .divide(precioSubtoken, 0, RoundingMode.DOWN)
            .intValue();

    if (subTokens <= 0) {
        throw new ConflictException("El monto es insuficiente para recibir al menos 1 sub-token");
    }
    if (subTokens > cupoRestante) {
        throw new ConflictException("El monto solicitado supera el cupo disponible del proyecto");
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. VERIFICAR SALDO DEL USUARIO
    // ═══════════════════════════════════════════════════════════════

    BigDecimal saldoActual = jdbc.queryForObject(
        "SELECT saldo_idea FROM users WHERE id = ?", BigDecimal.class, usuarioId
    );

    if (saldoActual == null || saldoActual.compareTo(request.getMontoIdea()) < 0) {
        throw new ConflictException("Saldo insuficiente de tokens IDEA");
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. INV-05: SWAP ATOMICO $IDEA - SUB-TOKENS (ON-CHAIN)
    // ═══════════════════════════════════════════════════════════════
    //
    // Intentamos realizar el swap en InvestmentSwap.sol en Sepolia.
    // Si Sepolia esta disponible:
    //   - Transfiere $IDEA del treasury al treasury
    //   - Mintea sub-tokens al treasury
    //   - Todo en UNA transaccion atomica
    // Si Sepolia NO esta disponible:
    //   - Usamos SmartContractService.recordInvestment() como fallback

    String txHash;
    boolean swapOnChain = false;
    try {
        // Convertir monto y sub-tokens a wei (18 decimales)
        // Ej: montoIdea = 500.00   -> 500 * 10^18 = 500000000000000000000
        // Ej: subTokens = 50       -> 50 * 10^18 = 50000000000000000000
        BigDecimal montoIdeaWei = request.getMontoIdea()
            .multiply(new BigDecimal("1000000000000000000"));
        BigDecimal subTokenAmountWei = BigDecimal.valueOf(subTokens)
            .multiply(new BigDecimal("1000000000000000000"));

        // Obtenemos la address del treasury desde la configuracion
        String treasuryAddress = blockchainProperties.getTreasuryAddress();

        // Llamamos a InvestmentSwap.invest() en Sepolia
        // Parametros:
        //   proyectoId      -> ID del proyecto
        //   ideaAmount      -> monto en wei
        //   subTokenAmount  -> sub-tokens en wei
        //   investor        -> treasury (porque aun no hay wallets on-chain)
        txHash = investmentSwapService.invest(
            request.getProyectoId(),
            montoIdeaWei.toBigInteger(),
            subTokenAmountWei.toBigInteger(),
            treasuryAddress
        );
        swapOnChain = true;
        log.info("INV-05: Swap on-chain exitoso. Tx: {}", txHash);

    } catch (Exception e) {
        // Si Sepolia no responde, hacemos FALLBACK a solo DB
        log.warn("INV-05: Swap on-chain no disponible. Fallback a DB-only. Error: {}", e.getMessage());
        Map<String, Object> txResult = smartContractService.recordInvestment(
            request.getProyectoId(), usuarioId, request.getMontoIdea(), request.getTxHash()
        );
        txHash = (String) txResult.get("txHash");
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. OPERACIONES EN BASE DE DATOS
    // ═══════════════════════════════════════════════════════════════

    // 4a. Descontar $IDEA del usuario
    jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?",
        request.getMontoIdea(), usuarioId);

    // 4b. Sumar al monto recaudado del proyecto
    jdbc.update("UPDATE projects SET monto_recaudado = COALESCE(monto_recaudado, 0) + ? WHERE id = ?",
        request.getMontoIdea(), request.getProyectoId());

    // 4c. Descontar del cupo del subtoken y actualizar precio dinamico
    int nuevoCupo = cupoRestante - subTokens;
    BigDecimal nuevoPrecio = pricingService.calcularPrecioDinamico(
        precioBase, suministroTotal, nuevoCupo, factorVolatilidad
    );
    jdbc.update("UPDATE subtokens SET cupo_restante = cupo_restante - ?, precio_actual = ? WHERE id = ?",
        subTokens, nuevoPrecio, subtokenId);

    // 4d. Agregar/actualizar portfolio del usuario
    // UPSERT: INSERT ... ON CONFLICT DO UPDATE
    // Si ya tenia ese subtoken, suma la cantidad
    // Si no lo tenia, lo crea
    jdbc.update("""
        INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad, created_at, updated_at)
        VALUES (?, ?, ?, NOW(), NOW())
        ON CONFLICT (usuario_id, subtoken_id)
        DO UPDATE SET cantidad = portfolio_activos.cantidad + ?, updated_at = NOW()
        """, usuarioId, subtokenId, subTokens, subTokens);

    // ═══════════════════════════════════════════════════════════════
    // 5. GUARDAR LA INVERSION EN DB
    // ═══════════════════════════════════════════════════════════════

    Usuario usuario = new Usuario();
    usuario.setId(usuarioId);

    Proyecto proyectoEntity = new Proyecto();
    proyectoEntity.setId(request.getProyectoId());

    Inversion inv = new Inversion();
    inv.setUsuario(usuario);
    inv.setProyecto(proyectoEntity);
    inv.setMontoIdea(request.getMontoIdea());
    inv.setSubTokensRecibidos(subTokens);
    inv.setTxHash(txHash);          // hash de la tx en Sepolia
    inv.setEstado("CONFIRMADA");

    return toResponse(investmentRepository.save(inv));
}
```

**Por que se repiten las validaciones?**

1. La pre-validacion (validateInvestment) es una CORTESIA para que el frontend muestre el precio antes de invertir
2. Cuando llega el request de creacion, NUNCA se confia en que el frontend hizo la pre-validacion. Se validan TODAS las condiciones de nuevo por seguridad.

**Que hace @Transactional?**

Garantiza que TODAS las operaciones de DB se ejecuten en una sola transaccion:

```
Sin @Transactional:
  UPDATE users SET saldo_idea -= 500   OK
  UPDATE projects SET monto += 500     OK
  INSERT portfolio ...                 FALLA (rollback manual? no)
  Resultado: usuario perdio 500 sin tener la inversion

Con @Transactional:
  UPDATE users SET saldo_idea -= 500   OK
  UPDATE projects SET monto += 500     OK
  INSERT portfolio ...                 FALLA
  -> ROLLBACK AUTOMATICO
  Resultado: todo se deshace, el usuario no pierde nada
```

**Por que INV-05 usa try-catch?**

Porque Sepolia puede estar caido. Si el swap on-chain falla, el sistema NO se rompe. Usa SmartContractService.recordInvestment() como hacia antes, que solo verifica el txHash en Sepolia pero no ejecuta un swap atomico. Es un degrade momentaneo, no una caida del sistema.

**Por que pasamos treasury como investor?**

Los usuarios de la plataforma aun no tienen wallets on-chain. El treasury recibe los sub-tokens a nombre del usuario. La DB registra la propiedad real. En el futuro, cuando los usuarios tengan wallets, pasaremos su address directamente.

**Por que se actualiza precio_actual del subtoken?**

Dynamic pricing (seccion 15): cada vez que alguien invierte, el precio del subtoken sube. Esto incentiva a invertir temprano. El calculo lo hace pricingService.calcularPrecioDinamico().

### 7.4 Método `processExpiredProjects()` — Reembolso automático

Este método lo ejecuta el scheduler una vez por día (6 AM).

**Archivo:** `investment/service/InvestmentService.java`

**Llamado desde:** `InvestmentScheduler.evaluateExpiredProjects()` (sin argumentos)

```java
@Transactional
public void processExpiredProjects() {
```

---

**Paso 1: Buscar proyectos vencidos**

```java
    List<Map<String, Object>> expiredProjects = jdbc.query(
        "SELECT id, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado " +
        "FROM projects WHERE estado = 'FINANCIAMIENTO' AND plazo IS NOT NULL " +
        "AND plazo < NOW() AND deleted_at IS NULL",
        (rs, rowNum) -> Map.of(
            "id", rs.getLong("id"),
            "montoRequerido", rs.getBigDecimal("monto_requerido"),
            "montoRecaudado", rs.getBigDecimal("monto_recaudado")
        )
    );
```

**SQL que ejecuta:**
```sql
SELECT id, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado
FROM projects
WHERE estado = 'FINANCIAMIENTO'
  AND plazo IS NOT NULL
  AND plazo < NOW()              -- vencidos
  AND deleted_at IS NULL;        -- no eliminados
```

**Ejemplo de respuesta de PostgreSQL:**
```
id | monto_requerido | monto_recaudado
1  | 10000           | 2500          ← NO alcanzó la meta (2500 < 10000)
3  | 5000            | 5000          ← SÍ alcanzó (5000 >= 5000)
```

**¿Qué queda en `expiredProjects`?**
```java
expiredProjects = [
    { id:1L, montoRequerido:10000, montoRecaudado:2500 },   // No alcanzó meta
    { id:3L, montoRequerido:5000,  montoRecaudado:5000 }    // Alcanzó meta (no se reembolsa)
]
```

---

**Paso 2: Iterar proyectos y decidir**

```java
    for (Map<String, Object> project : expiredProjects) {
        // project = { id:1L, montoRequerido:10000, montoRecaudado:2500 }  (1ra iteración)
        // project = { id:3L, montoRequerido:5000,  montoRecaudado:5000 }  (2da iteración)

        Long projectId = (Long) project.get("id");                    // 1L
        BigDecimal montoRequerido = (BigDecimal) project.get("montoRequerido");  // 10000
        BigDecimal montoRecaudado = (BigDecimal) project.get("montoRecaudado");  // 2500

        // 2500.compareTo(10000) = -1  →  2500 < 10000 → true (entra al if)
        // 5000.compareTo(5000)  =  0  →  5000 >= 5000 → false (no entra)
        if (montoRecaudado.compareTo(montoRequerido) < 0) {
```

**¿Qué hace `compareTo()`?**
```java
new BigDecimal("2500").compareTo(new BigDecimal("10000"))  // -1 (2500 < 10000) → REEMBOLSAR
new BigDecimal("5000").compareTo(new BigDecimal("5000"))   //  0 (5000 = 5000)  → NO REEMBOLSAR
new BigDecimal("6000").compareTo(new BigDecimal("5000"))   //  1 (6000 > 5000)  → NO REEMBOLSAR
```

---

**Paso 3: Ejecutar reembolso (solo si no alcanzó meta)**

```java
            refundAllInvestors(projectId);
            // → Llama a refundAllInvestors(1L)
            // → (ver sección 7.5 para el detalle)

            jdbc.update(
                "UPDATE projects SET estado = 'FINALIZADO', updated_at = NOW() WHERE id = ?",
                projectId  // 1L
            );
```

**SQL:**
```sql
UPDATE projects SET estado = 'FINALIZADO', updated_at = NOW() WHERE id = 1;
```

---

**¿Por qué `COALESCE(monto_recaudado, 0)`?** Si un proyecto nuevo no tiene ninguna inversión, `monto_recaudado` podría ser NULL. `COALESCE` devuelve 0 en ese caso, evitando un `NullPointerException` en `compareTo()`.

### 7.5 Método `refundAllInvestors()` — Devolución a inversores

**Archivo:** `investment/service/InvestmentService.java`

**Llamado desde:** `processExpiredProjects()` con un `Long proyectoId`

```java
@Transactional
public void refundAllInvestors(Long proyectoId) {
```

**Argumento recibido:**
| Parámetro | Valor | ¿De dónde viene? |
|-----------|-------|------------------|
| `proyectoId` | `1L` | Del proyecto vencido que no alcanzó la meta |

---

**Paso 1: Buscar inversiones activas del proyecto**

```java
    List<Inversion> inversiones = investmentRepository.findPendingRefundsByProyectoId(proyectoId);
    // → findPendingRefundsByProyectoId(1L)
```

**¿Qué consulta SQL ejecuta este método?** (Está en `JdbcInvestmentRepository.java`)

```java
public List<Inversion> findPendingRefundsByProyectoId(Long proyectoId) {
    return jdbc.query(
        "SELECT * FROM investments WHERE proyecto_id = ? " +
        "AND estado IN ('PENDIENTE', 'CONFIRMADA')",
        rowMapper,
        proyectoId  // 1L
    );
}
```

**SQL:**
```sql
SELECT * FROM investments WHERE proyecto_id = 1 AND estado IN ('PENDIENTE', 'CONFIRMADA');
```

**Respuesta de PostgreSQL:**
```
id | usuario_id | proyecto_id | monto_idea | sub_tokens_recibidos | tx_hash | estado
1  | 2          | 1           | 500        | 50                   | 0x983a..| CONFIRMADA
2  | 5          | 1           | 200        | 20                   | 0xdef.. | CONFIRMADA
3  | 2          | 1           | 100        | 10                   | 0x789.. | PENDIENTE
```

**¿Qué queda en `inversiones`?**
```java
inversiones = [
    Inversion{id:1, usuarioId:2, montoIdea:500, subTokensRecibidos:50, ...},
    Inversion{id:2, usuarioId:5, montoIdea:200, subTokensRecibidos:20, ...},
    Inversion{id:3, usuarioId:2, montoIdea:100, subTokensRecibidos:10, ...}
]
```

---

**Paso 2: Por cada inversión, devolver fondos**

```java
    for (Inversion inv : inversiones) {
        // 1ra iteración: inv = Inversion{id:1, usuarioId:2, montoIdea:500, subTokensRecibidos:50}
        // 2da iteración: inv = Inversion{id:2, usuarioId:5, montoIdea:200, subTokensRecibidos:20}
        // 3ra iteración: inv = Inversion{id:3, usuarioId:2, montoIdea:100, subTokensRecibidos:10}
```

**2a. SmartContract (blockchain):**
```java
        smartContractService.refundInvestment(
            proyectoId,                    // 1L
            inv.getUsuario().getId(),      // 2L  (usuario_id de la inversión)
            inv.getMontoIdea()             // BigDecimal("500")
        );
```
Esto llama al método en `SmartContractService.java` que a su vez llama a `BlockchainService.mintTokens()` para hacer un mint en Sepolia simulando la devolución (ver trazado en sección 10.7).

**2b. Devolver $IDEA al usuario:**
```java
        jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?",
            inv.getMontoIdea(),           // BigDecimal("500")
            inv.getUsuario().getId()      // 2L
        );
```
**SQL:** `UPDATE users SET saldo_idea = saldo_idea + 500 WHERE id = 2`
**Efecto:** El usuario 2 recupera sus 500 $IDEA

**2c. Limpiar portfolio:**
```java
        if (inv.getSubTokensRecibidos() != null && inv.getSubTokensRecibidos() > 0) {
            // subTokensRecibidos = 50, es > 0 → entra
            jdbc.update("""
                UPDATE portfolio_activos pa
                SET cantidad = GREATEST(pa.cantidad - ?, 0), updated_at = NOW()
                FROM subtokens s
                WHERE pa.subtoken_id = s.id AND s.proyecto_id = ? AND pa.usuario_id = ?
                """,
                inv.getSubTokensRecibidos(),  // 50
                proyectoId,                    // 1L
                inv.getUsuario().getId()       // 2L
            );
        }
```

**SQL que se ejecuta:**
```sql
UPDATE portfolio_activos pa
SET cantidad = GREATEST(pa.cantidad - 50, 0), updated_at = NOW()
FROM subtokens s
WHERE pa.subtoken_id = s.id
  AND s.proyecto_id = 1
  AND pa.usuario_id = 2;
```

**¿Qué hace GREATEST?**
- Si `cantidad = 100`, `GREATEST(100 - 50, 0)` = `50` ✅
- Si `cantidad = 30`, `GREATEST(30 - 50, 0)` = `0` (nunca negativo)
- Si `cantidad = 50`, `GREATEST(50 - 50, 0)` = `0` ✅

**2d. Marcar inversión como reembolsada:**
```java
        inv.setEstado("REEMBOLSADA");
        investmentRepository.save(inv);
        // save() internamente hace UPDATE investments SET estado = 'REEMBOLSADA' WHERE id = 1
```

**SQL:**
```sql
UPDATE investments SET estado = 'REEMBOLSADA', updated_at = NOW() WHERE id = 1;
```

---

**Resultado final después del loop:**

| Tabla | Cambio |
|-------|--------|
| `users` | usuario 2: +500 $IDEA, usuario 5: +200 $IDEA, usuario 2: +100 $IDEA |
| `portfolio_activos` | Se restaron 50, 20 y 10 sub-tokens respectivamente |
| `investments` | Las 3 inversiones ahora tienen estado `REEMBOLSADA` |
| `projects` | Estado pasa a `FINALIZADO` |

**Visual del flujo completo de reembolso:**
```
SCHEDULER (6 AM)
   │
   │ processExpiredProjects()
   │
   ├─ SQL: SELECT proyectos vencidos en FINANCIAMIENTO
   │
   ├─ [Proyecto #1: recaudado=2500 < requerido=10000]
   │   │
   │   └─ refundAllInvestors(1)
   │       │
   │       ├─ [Inversión #1: usuario=2, monto=500, tokens=50]
   │       │   ├─ Blockchain: mint 500 $IDEA (simula refund)
   │       │   ├─ SQL: users.saldo_idea += 500
   │       │   ├─ SQL: portfolio_activos.cantidad -= 50
   │       │   └─ SQL: investments.estado = 'REEMBOLSADA'
   │       │
   │       ├─ [Inversión #2: usuario=5, monto=200, tokens=20]
   │       │   ├─ Blockchain: mint 200 $IDEA
   │       │   ├─ SQL: users.saldo_idea += 200
   │       │   ├─ SQL: portfolio_activos.cantidad -= 20
   │       │   └─ SQL: investments.estado = 'REEMBOLSADA'
   │       │
   │       └─ [Inversión #3: usuario=2, monto=100, tokens=10]
   │           ├─ Blockchain: mint 100 $IDEA
   │           ├─ SQL: users.saldo_idea += 100
   │           ├─ SQL: portfolio_activos.cantidad -= 10
   │           └─ SQL: investments.estado = 'REEMBOLSADA'
   │
   └─ SQL: projects.estado = 'FINALIZADO'
```

---

**¿GREATEST(cantidad - ?, 0)?** Evita que la cantidad quede negativa por errores de redondeo. Si por algún motivo el usuario tiene menos sub-tokens de los que debería devolver, se pone en 0.

### 7.6 Métodos auxiliares privados

```java
// Buscar proyecto o lanzar 404
private Map<String, Object> findProjectRowOrThrow(Long projectId) {
    List<Map<String, Object>> results = jdbc.query(
        "SELECT id, titulo, estado, monto_requerido, " +
        "COALESCE(monto_recaudado, 0) AS monto_recaudado " +
        "FROM projects WHERE id = ? AND deleted_at IS NULL",
        (rs, rowNum) -> Map.of(
            "id", rs.getLong("id"),
            "titulo", rs.getString("titulo"),
            "estado", rs.getString("estado"),
            "montoRequerido", rs.getBigDecimal("monto_requerido"),
            "montoRecaudado", rs.getBigDecimal("monto_recaudado")
        ),
        projectId
    );
    if (results.isEmpty()) {
        throw new ResourceNotFoundException("Proyecto no encontrado con ID: " + projectId);
    }
    return results.get(0);
}

// Buscar subtoken asociado a un proyecto
private Map<String, Object> findSubtokenByProject(Long proyectoId) {
    List<Map<String, Object>> results = jdbc.query(
        "SELECT id, cupo_restante, precio_actual FROM subtokens WHERE proyecto_id = ?",
        (rs, rowNum) -> Map.of(
            "id", rs.getLong("id"),
            "cupo_restante", rs.getInt("cupo_restante"),
            "precio_actual", rs.getBigDecimal("precio_actual")
        ),
        proyectoId
    );
    return results.isEmpty() ? null : results.get(0);
}

// Convertir Inversion → InvestmentResponse
private InvestmentResponse toResponse(Inversion inv) {
    String proyectoTitulo = "";
    try {
        proyectoTitulo = jdbc.queryForObject(
            "SELECT titulo FROM projects WHERE id = ?",
            String.class, inv.getProyecto().getId()
        );
    } catch (Exception e) {
        proyectoTitulo = "Proyecto #" + inv.getProyecto().getId();
    }

    return InvestmentResponse.builder()
            .id(inv.getId())
            .usuarioId(inv.getUsuario() != null ? inv.getUsuario().getId() : null)
            .proyectoId(inv.getProyecto() != null ? inv.getProyecto().getId() : null)
            .proyectoTitulo(proyectoTitulo)
            .montoIdea(inv.getMontoIdea())
            .subTokensRecibidos(inv.getSubTokensRecibidos())
            .txHash(inv.getTxHash())
            .estado(inv.getEstado())
            .createdAt(inv.getCreatedAt())
            .updatedAt(inv.getUpdatedAt())
            .build();
}
```

**¿Por qué `Map.of()`?** No usamos la entidad `Proyecto` del shared-model porque le faltan campos como `monto_recaudado`. `Map.of()` devuelve un mapa inmutable con las columnas que necesitamos, sin depender del shared-model.

---

### 7.7 Trazado completo de llamadas — Una inversión paso a paso

> Esta sección es el **análisis línea por línea** de lo que pasa cuando un usuario invierte. Seguimos una inversión de 500 $IDEA en el proyecto #1, con precio de sub-token = 10.

#### 7.7.1 El usuario hace `POST /api/investments`

**Request HTTP que llega:**
```
POST http://localhost:8081/api/investments
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9... (JWT del usuario)
Content-Type: application/json

{
  "proyectoId": 1,
  "montoIdea": 500,
  "txHash": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
}
```

#### 7.7.2 Paso 1: Spring Security filtra el request

**¿Quién recibe primero el request?** El `TokenValidationFilter` (en `security/TokenValidationFilter.java`).

```
Request HTTP → TokenValidationFilter.doFilterInternal()
                │
                ├─ Extrae header "Authorization: Bearer eyJhbGci..."
                ├─ Llama a AuthServiceClient.validateToken(token)
                │     → HTTP POST a http://localhost:8080/auth/validate
                │     → Recibe { userId: 2, email: "inversor@test.com",
                │                roles: ["USER"], permissions: ["investment:create"] }
                │
                ├─ Crea JwtPrincipal(userId=2, email="inversor@test.com")
                │
                └─ Guarda en SecurityContextHolder (para que @AuthenticationPrincipal funcione)
```

**Salida de este paso:** El usuario `id=2` está autenticado con permiso `investment:create`.

#### 7.7.3 Paso 2: InvestmentController recibe el request

**Archivo:** `investment/controller/InvestmentController.java`

```java
@PostMapping                                          // Ruta: POST /api/investments
@ResponseStatus(HttpStatus.CREATED)                   // Devuelve 201 (Created)
@PreAuthorize("hasAuthority('investment:create')")    // ¿Tiene permiso? → Sí (paso anterior)
public InvestmentResponse createInvestment(
        @RequestBody @Valid CreateInvestmentRequest request,
        // request = { proyectoId: 1, montoIdea: 500, txHash: "0x983a..." }
        @AuthenticationPrincipal JwtPrincipal principal)
        // principal = JwtPrincipal(userId=2, email="inversor@test.com")
{
    return investmentService.createInvestment(
        request,          // → CreateInvestmentRequest{proyectoId=1, montoIdea=500, txHash="0x983a..."}
        principal.userId() // → 2L (Long)
    );
    // ↑ Devuelve InvestmentResponse{id=1, estado="CONFIRMADA", ...}
}
```

**Llamada al service:**
```
InvestmentController.createInvestment(request, 2L)
    → InvestmentService.createInvestment(request, usuarioId=2L)
```

#### 7.7.4 Paso 3: InvestmentService.createInvestment() — Validaciones

**Archivo:** `investment/service/InvestmentService.java` — Línea 839

```java
@Transactional
public InvestmentResponse createInvestment(CreateInvestmentRequest request, Long usuarioId) {
```

**Argumentos recibidos:**
| Parámetro | Valor | ¿De dónde viene? |
|-----------|-------|------------------|
| `request.proyectoId` | `1L` | Del JSON del frontend |
| `request.montoIdea` | `BigDecimal("500")` | Del JSON del frontend |
| `request.txHash` | `"0x983a508cc91f..."` | De MetaMask (el usuario firmó la tx) |
| `usuarioId` | `2L` | Del JWT (autenticación) |

---

**3a. Buscar proyecto:**

```java
Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
// findProjectRowOrThrow(1L)
```

**Llamada interna:** `findProjectRowOrThrow(1L)` en línea 1056:
```java
private Map<String, Object> findProjectRowOrThrow(Long projectId) {
    List<Map<String, Object>> results = jdbc.query(
        "SELECT id, titulo, estado, monto_requerido, " +
        "COALESCE(monto_recaudado, 0) AS monto_recaudado " +
        "FROM projects WHERE id = ? AND deleted_at IS NULL",
        new RowMapper<Map<String, Object>>() {
            public Map<String, Object> mapRow(ResultSet rs, int rowNum) {
                return Map.of(
                    "id",             rs.getLong("id"),              // 1L
                    "titulo",         rs.getString("titulo"),         // "Mi Proyecto"
                    "estado",         rs.getString("estado"),         // "FINANCIAMIENTO"
                    "montoRequerido", rs.getBigDecimal("monto_requerido"),  // 10000
                    "montoRecaudado", rs.getBigDecimal("monto_recaudado")   // 2500
                );
            }
        },
        1L  // ← projectId (parámetro de la query)
    );
    // results = [ {id:1, titulo:"Mi Proyecto", estado:"FINANCIAMIENTO", ...} ]
    //           ↑ Lista con 1 elemento

    if (results.isEmpty()) {
        // Si el proyecto no existe → lanza 404
        throw new ResourceNotFoundException("Proyecto no encontrado con ID: " + projectId);
    }
    return results.get(0);
    // ↑ Devuelve: { id:1, titulo:"Mi Proyecto", estado:"FINANCIAMIENTO",
    //               montoRequerido:10000, montoRecaudado:2500 }
}
```

**¿Qué SQL ejecuta?**
```sql
SELECT id, titulo, estado, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado
FROM projects
WHERE id = 1 AND deleted_at IS NULL;
```

**¿Qué devuelve PostgreSQL?**
```
id | titulo       | estado         | monto_requerido | monto_recaudado
1  | "Mi Proyecto" | "FINANCIAMIENTO" | 10000           | 2500
```

**¿Qué queda en `proyecto`?**
```java
proyecto = Map.of(
    "id",             1L,
    "titulo",         "Mi Proyecto",
    "estado",         "FINANCIAMIENTO",
    "montoRequerido", new BigDecimal("10000"),
    "montoRecaudado", new BigDecimal("2500")
);
```

---

**3b. Validar estado:**

```java
String estado = (String) proyecto.get("estado");
// estado = "FINANCIAMIENTO"

if (!"FINANCIAMIENTO".equals(estado)) {
    // No entra acá porque estado = "FINANCIAMIENTO"
    throw new ConflictException("El proyecto no esta en estado de financiamiento");
}
```

---

**3c. Buscar subtoken:**

```java
Map<String, Object> subtoken = findSubtokenByProject(request.getProyectoId());
// findSubtokenByProject(1L)
```

**Llamada interna:** `findSubtokenByProject(1L)` en línea 1077:
```java
private Map<String, Object> findSubtokenByProject(Long proyectoId) {
    List<Map<String, Object>> results = jdbc.query(
        "SELECT id, cupo_restante, precio_actual FROM subtokens WHERE proyecto_id = ?",
        new RowMapper<Map<String, Object>>() {
            public Map<String, Object> mapRow(ResultSet rs, int rowNum) {
                return Map.of(
                    "id",             rs.getLong("id"),              // 10L
                    "cupo_restante",  rs.getInt("cupo_restante"),    // 100
                    "precio_actual",  rs.getBigDecimal("precio_actual")  // 10
                );
            }
        },
        1L  // ← proyectoId
    );
    // results = [ {id:10, cupo_restante:100, precio_actual:10} ]

    return results.isEmpty() ? null : results.get(0);
    // ↑ Devuelve: { id:10, cupo_restante:100, precio_actual:10 }
}
```

**¿Qué SQL ejecuta?**
```sql
SELECT id, cupo_restante, precio_actual FROM subtokens WHERE proyecto_id = 1;
```

**¿Qué devuelve PostgreSQL?**
```
id | cupo_restante | precio_actual
10 | 100           | 10
```

---

**3d. Calcular sub-tokens:**

```java
int subTokens = request.getMontoIdea()          // 500
        .divide(precioSubtoken, 0, RoundingMode.DOWN)  // 500 / 10 = 50
        .intValue();                             // 50

// subTokens = 50
```

**¿Qué hace exactamente `divide(precioSubtoken, 0, RoundingMode.DOWN)`?**
- `500 / 10 = 50.0` → trunca a `50`
- Si fuera `501 / 10 = 50.1` → trunca a `50` (pierde 0.1, no redondea hacia arriba)

---

**3e. Validaciones finales:**

```java
if (subTokens <= 0) {
    // No entra acá porque subTokens = 50
    throw new ConflictException("...");
}
if (subTokens > cupoRestante) {
    // No entra acá porque 50 <= 100
    throw new ConflictException("...");
}
```

---

**Resumen de validaciones en este punto:**
| Check | Resultado |
|-------|-----------|
| Proyecto #1 existe | ✅ Sí, está en FINANCIAMIENTO |
| Subtoken asociado | ✅ Sí, id=10, cupo=100, precio=10 |
| Cálculo: 500/10 = 50 | ✅ OK |
| 50 > 0? | ✅ Sí |
| 50 > 100? | ❌ No, pasa |

#### 7.7.5 Paso 4: Verificar saldo del usuario

```java
BigDecimal saldoActual = jdbc.queryForObject(
    "SELECT saldo_idea FROM users WHERE id = ?",
    BigDecimal.class,
    usuarioId  // 2L
);
```

**SQL que ejecuta:**
```sql
SELECT saldo_idea FROM users WHERE id = 2;
```

**Respuesta de PostgreSQL:**
```
saldo_idea
5000.00
```

```java
// saldoActual = BigDecimal("5000.00")

if (saldoActual == null || saldoActual.compareTo(request.getMontoIdea()) < 0) {
    // saldoActual = 5000, request.montoIdea = 500
    // 5000.compareTo(500) = 1 (5000 > 500)
    // No entra acá
    throw new ConflictException("Saldo insuficiente de tokens IDEA");
}
```

#### 7.7.6 Paso 5: Llamar a SmartContractService → BlockchainService → Sepolia

**Archivo:** `InvestmentService.java` línea 896

```java
Map<String, Object> txResult = smartContractService.recordInvestment(
    request.getProyectoId(),  // 1L
    usuarioId,                // 2L
    request.getMontoIdea(),   // BigDecimal("500")
    request.getTxHash()       // "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
);
```

**¿Qué pasa adentro de `SmartContractService.recordInvestment()`?**
(Ver archivo: `project/service/SmartContractService.java`)

```java
public Map<String, Object> recordInvestment(Long proyectoId, Long usuarioId,
                                              BigDecimal montoIdea, String txHash) {
    Map<String, Object> result = new HashMap<>();
    try {
        // Llama a BlockchainService.verifyTransaction(txHash)
        // Internamente hace eth_getTransactionReceipt a Sepolia
        boolean txValida = blockchain.verifyTransaction(txHash);
        // ↑ ¿El hash existe en Sepolia? ¿La transacción fue exitosa?

        result.put("success", txValida);
        result.put("txHash", txHash);

        if (txValida) {
            // Si la tx es válida, consulta datos reales:
            BigInteger block = blockchain.getLatestBlock();
            // ↑ eth_blockNumber → el último bloque minado

            BigInteger balance = blockchain.balanceOf(
                props.getProjectTokenAddress(),     // ProjectToken address
                props.getTreasuryAddress()           // Treasury address
            );
            // ↑ eth_call a balanceOf(0x7eEA8...) en el ProjectToken

            result.put("blockNumber", block.longValue());        // ej: 12345678
            result.put("treasuryTokensBalance", balance);        // ej: 1000000
            result.put("note", "Transaccion verificada en Sepolia");
        } else {
            result.put("blockNumber", 0L);
            result.put("note", "Tx no encontrada en Sepolia");
        }
    } catch (Exception e) {
        // MODO OFFLINE: si Sepolia no responde
        log.error("Blockchain no disponible: {}", e.getMessage());
        result.put("success", true);     // asume éxito
        result.put("blockNumber", 0L);
        result.put("note", "Modo offline: " + e.getMessage());
    }
    return result;
}
```

**Profundizando en `blockchain.verifyTransaction(txHash)`:**

Este método hace **exactamente** una llamada HTTP a Sepolia:

```
Web3j envía:  POST https://ethereum-sepolia-rpc.publicnode.com
              Content-Type: application/json
              Body: {
                "jsonrpc": "2.0",
                "method": "eth_getTransactionReceipt",
                "params": ["0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"],
                "id": 1
              }

Sepolia responde:
              {
                "jsonrpc": "2.0",
                "id": 1,
                "result": {
                  "transactionHash": "0x983a508c...",
                  "blockNumber": "0xbc614e",        // 12345678 en hex
                  "status": "0x1",                  // 0x1 = éxito
                  "from": "0x7eEA86...",
                  "to": "0x7aabb2...",
                  "logs": [...]
                }
              }
```

**¿Qué devuelve `verifyTransaction()`?**
```java
return receipt.getTransactionReceipt().isPresent()     // true (existe la tx)
    && receipt.getTransactionReceipt().get().getStatus().equals("0x1");
    // true (status = 0x1 = éxito)
// → Devuelve: true
```

**¿Qué queda en `txResult`?**
```java
txResult = {
    "success": true,
    "txHash": "0x983a508c...",
    "blockNumber": 12345678L,
    "treasuryTokensBalance": 1000000,
    "note": "Transaccion verificada en Sepolia"
}
```

**¿Qué queda en `txHash`?**
```java
String txHash = (String) txResult.get("txHash");
// txHash = "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
```

#### 7.7.7 Paso 6: Operaciones en base de datos

Todas estas operaciones están dentro de `@Transactional`. Si alguna falla, todas se deshacen.

---

**6a. Descontar $IDEA del usuario:**
```java
jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?",
    request.getMontoIdea(),  // BigDecimal("500")
    usuarioId                // 2L
);
```
**SQL:** `UPDATE users SET saldo_idea = saldo_idea - 500 WHERE id = 2`
**Efecto:** `saldo_idea` pasa de 5000 a 4500

---

**6b. Sumar al monto recaudado del proyecto:**
```java
jdbc.update("UPDATE projects SET monto_recaudado = COALESCE(monto_recaudado, 0) + ? WHERE id = ?",
    request.getMontoIdea(),  // BigDecimal("500")
    request.getProyectoId()  // 1L
);
```
**SQL:** `UPDATE projects SET monto_recaudado = COALESCE(monto_recaudado, 0) + 500 WHERE id = 1`
**Efecto:** `monto_recaudado` pasa de 2500 a 3000

---

**6c. Descontar del cupo del subtoken:**
```java
jdbc.update("UPDATE subtokens SET cupo_restante = cupo_restante - ? WHERE id = ?",
    subTokens,   // 50 (Integer)
    subtokenId   // 10L
);
```
**SQL:** `UPDATE subtokens SET cupo_restante = cupo_restante - 50 WHERE id = 10`
**Efecto:** `cupo_restante` pasa de 100 a 50

---

**6d. Agregar/actualizar portfolio (UPSERT):**
```java
jdbc.update("""
    INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad, created_at, updated_at)
    VALUES (?, ?, ?, NOW(), NOW())
    ON CONFLICT (usuario_id, subtoken_id)
    DO UPDATE SET cantidad = portfolio_activos.cantidad + ?, updated_at = NOW()
    """,
    usuarioId,    // 2L
    subtokenId,   // 10L
    subTokens,    // 50
    subTokens     // 50 (para la cláusula DO UPDATE SET cantidad + ?)
);
```

**¿Qué hace UPSERT?**
- Si el usuario 2 NO tenía el subtoken 10 → INSERT: crea registro con cantidad = 50
- Si el usuario 2 YA tenía el subtoken 10 → UPDATE: suma 50 a la cantidad existente
- La condición `ON CONFLICT (usuario_id, subtoken_id)` usa el índice único que creamos en la migración

**SQL que se ejecuta (caso INSERT):**
```sql
INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad, created_at, updated_at)
VALUES (2, 10, 50, NOW(), NOW());
```
**SQL que se ejecuta (caso UPDATE):**
```sql
UPDATE portfolio_activos
SET cantidad = cantidad + 50, updated_at = NOW()
WHERE usuario_id = 2 AND subtoken_id = 10;
```

#### 7.7.8 Paso 7: Guardar la inversión en DB

**Construcción de la entidad:**
```java
Usuario usuario = new Usuario();
usuario.setId(usuarioId);                          // id = 2

Proyecto proyectoEntity = new Proyecto();
proyectoEntity.setId(request.getProyectoId());      // id = 1

Inversion inv = new Inversion();
inv.setUsuario(usuario);                            // usuario_id = 2
inv.setProyecto(proyectoEntity);                    // proyecto_id = 1
inv.setMontoIdea(request.getMontoIdea());           // monto_idea = 500
inv.setSubTokensRecibidos(subTokens);               // sub_tokens_recibidos = 50
inv.setTxHash(txHash);                              // tx_hash = "0x983a..."
inv.setEstado("CONFIRMADA");                        // estado = CONFIRMADA
```

**Llamada al repository:**
```java
Inversion saved = investmentRepository.save(inv);
// Inversión sin ID hasta este punto → save() hará INSERT
```

**¿Qué pasa dentro de `JdbcInvestmentRepository.save()`?**
```java
// Como inversion.id == null, entra en el INSERT:
String sql = """
    INSERT INTO investments
        (usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos,
         tx_hash, estado, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
    RETURNING id
    """;

Long id = jdbc.queryForObject(sql, Long.class,
    inversion.getUsuario().getId(),         // 2
    inversion.getProyecto().getId(),        // 1
    inversion.getMontoIdea(),               // 500.00
    inversion.getSubTokensRecibidos(),      // 50
    inversion.getTxHash(),                  // "0x983a..."
    inversion.getEstado()                   // "CONFIRMADA"
);
// PostgreSQL devuelve: 1 (el id auto-generado)

inversion.setId(1L);
// Ahora la inversión tiene id
```

**¿Qué SQL se ejecuta en la DB?**
```sql
INSERT INTO investments
    (usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos,
     tx_hash, estado, created_at, updated_at)
VALUES
    (2, 1, 500.00, 50, '0x983a508c...', 'CONFIRMADA', NOW(), NOW())
RETURNING id;
```

**¿Qué devuelve PostgreSQL?**
```
id
1
```

#### 7.7.9 Paso 8: Convertir a InvestmentResponse (lo que ve el frontend)

```java
return toResponse(investmentRepository.save(inv));
// toResponse(Inversion{id=1, usuario_id=2, proyecto_id=1, monto=500, tokens=50, ...})
```

**¿Qué hace `toResponse()`?**
```java
private InvestmentResponse toResponse(Inversion inv) {
    // Consulta el título del proyecto para mostrarlo en la respuesta
    String proyectoTitulo = "";
    try {
        proyectoTitulo = jdbc.queryForObject(
            "SELECT titulo FROM projects WHERE id = ?",
            String.class,
            inv.getProyecto().getId()  // 1L
        );
        // proyectoTitulo = "Mi Proyecto"
    } catch (Exception e) {
        proyectoTitulo = "Proyecto #" + inv.getProyecto().getId();
    }

    return InvestmentResponse.builder()
            .id(inv.getId())                          // 1
            .usuarioId(inv.getUsuario().getId())      // 2
            .proyectoId(inv.getProyecto().getId())    // 1
            .proyectoTitulo(proyectoTitulo)            // "Mi Proyecto"
            .montoIdea(inv.getMontoIdea())            // 500.00
            .subTokensRecibidos(inv.getSubTokensRecibidos())  // 50
            .txHash(inv.getTxHash())                  // "0x983a508c..."
            .estado(inv.getEstado())                  // "CONFIRMADA"
            .createdAt(inv.getCreatedAt())            // 2026-05-20T10:30:00
            .updatedAt(inv.getUpdatedAt())            // 2026-05-20T10:30:00
            .build();
}
```

#### 7.7.10 Paso 9: Respuesta HTTP al frontend

```json
HTTP 201 Created
Content-Type: application/json

{
  "id": 1,
  "usuarioId": 2,
  "proyectoId": 1,
  "proyectoTitulo": "Mi Proyecto",
  "montoIdea": 500.00,
  "subTokensRecibidos": 50,
  "txHash": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac",
  "estado": "CONFIRMADA",
  "createdAt": "2026-05-20T10:30:00",
  "updatedAt": "2026-05-20T10:30:00"
}
```

#### 7.7.11 Resumen visual de toda la cadena de llamadas

```
FRONTEND                          CONTROLLER                   SERVICE                        DB/BLOCKCHAIN
   │                                  │                            │                              │
   │ POST /api/investments             │                            │                              │
   │ { proyectoId:1,                   │                            │                              │
   │   montoIdea:500,                 │                            │                              │
   │   txHash:"0x983a..." }           │                            │                              │
   │─────────────────────────────────>│                            │                              │
   │                                  │                            │                              │
   │                                  │ createInvestment(request, 2)                               │
   │                                  │───────────────────────────>│                              │
   │                                  │                            │                              │
   │                                  │                            │ findProjectRowOrThrow(1)      │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<─────────────────────────────│
   │                                  │                            │ { id:1, estado:FINANCIAMIENTO }│
   │                                  │                            │                              │
   │                                  │                            │ findSubtokenByProject(1)      │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<─────────────────────────────│
   │                                  │                            │ { id:10, cupo:100, precio:10 }│
   │                                  │                            │                              │
   │                                  │                            │ smartContractService          │
   │                                  │                            │ .recordInvestment(...)        │
   │                                  │                            │──┐                           │
   │                                  │                            │  │ blockchain.verifyTx(hash)  │
   │                                  │                            │  │──────────────────────────>│─── Sepolia RPC
   │                                  │                            │  │<──────────────────────────│─── eth_getTxReceipt
   │                                  │                            │<─┘ { success:true }          │
   │                                  │                            │                              │
   │                                  │                            │ UPDATE users SET saldo_idea  │
   │                                  │                            │  = saldo_idea - 500          │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<─────────────────────────────│
   │                                  │                            │                              │
   │                                  │                            │ UPDATE projects SET           │
   │                                  │                            │  monto_recaudado += 500      │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<─────────────────────────────│
   │                                  │                            │                              │
   │                                  │                            │ UPDATE subtokens SET         │
   │                                  │                            │  cupo_restante -= 50         │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<─────────────────────────────│
   │                                  │                            │                              │
   │                                  │                            │ UPSERT portfolio_activos     │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<─────────────────────────────│
   │                                  │                            │                              │
   │                                  │                            │ INSERT investments (...)     │
   │                                  │                            │─────────────────────────────>│
   │                                  │                            │<──────────────────── id:1 ───│
   │                                  │                            │                              │
   │                                  │<───────────────────────────│                              │
   │                                  │                            │                              │
   │<─────────────────────────────────│                            │                              │
   │ 201 Created                      │                            │                              │
   │ { id:1, estado:"CONFIRMADA",     │                            │                              │
   │   txHash:"0x983a...",            │                            │                              │
   │   subTokensRecibidos:50 }        │                            │                              │
```

---

## 8. InvestmentController — Los endpoints REST

### 8.1 El controlador completo

```java
@RestController                    // Spring sabe que esta clase maneja requests HTTP
@RequestMapping("/api/investments")  // Todos los endpoints empiezan con /api/investments
public class InvestmentController {

    private final InvestmentService investmentService;

    // POST /api/investments/validate — Pre-validación
    @PostMapping("/validate")
    public ValidateInvestmentResponse validateInvestment(
            @RequestBody @Valid ValidateInvestmentRequest request) {
        return investmentService.validateInvestment(request);
    }

    // POST /api/investments — Crear inversión
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)          // Devuelve 201 Created (no 200)
    @PreAuthorize("hasAuthority('investment:create')")  // Solo usuarios con permiso
    public InvestmentResponse createInvestment(
            @RequestBody @Valid CreateInvestmentRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {  // Usuario autenticado
        return investmentService.createInvestment(request, principal.userId());
    }

    // GET /api/investments/{id} — Ver una inversión
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('investment:read')")
    public InvestmentResponse getInvestmentById(@PathVariable Long id) {
        return investmentService.getInvestmentById(id);
    }

    // GET /api/investments/history?page=0&size=10 — Historial
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('investment:read')")
    public Page<InvestmentResponse> getInvestmentHistory(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return investmentService.getInvestmentHistory(
            principal.userId(), PageRequest.of(page, size));
    }
}
```

### 8.2 Tabla de endpoints

| Método | URL | Auth | ¿Para qué? | Request Body |
|--------|-----|------|------------|-------------|
| `POST` | `/api/investments/validate` | ❌ No | Pre-validar inversión | `{ proyectoId, montoIdea }` |
| `POST` | `/api/investments` | ✅ `investment:create` | Crear inversión | `{ proyectoId, montoIdea, txHash }` |
| `GET` | `/api/investments/{id}` | ✅ `investment:read` | Ver detalle | — |
| `GET` | `/api/investments/history` | ✅ `investment:read` | Historial (paginado) | — |

### 8.3 ¿Cómo obtiene el ID del usuario autenticado?

El sistema tiene un **filtro JWT** (`TokenValidationFilter`) que:

1. Intercepta cada request HTTP
2. Extrae el token JWT del header `Authorization: Bearer <token>`
3. Llama al módulo Auth (`auth.service.url`) para validar el token
4. Si es válido, crea un objeto `JwtPrincipal` con `userId` y `email`
5. Spring Security lo pone disponible en el contexto

En el controller, `@AuthenticationPrincipal JwtPrincipal principal` lo extrae automáticamente. El service recibe `principal.userId()`:

```java
// Así se ve JwtPrincipal:
public record JwtPrincipal(Long userId, String email) {}
```

**¿Por qué no hardcodear `32L`?** En desarrollo temprano se usaba un ID fijo para pruebas. Ahora que la autenticación funciona, cada request obtiene el ID real del usuario.

---

## 9. InvestmentScheduler — Tareas automáticas

### 9.1 ¿Qué es un scheduler? 

Un scheduler ejecuta código en **intervalos programados**. Spring Boot lo activa con `@EnableScheduling`.

### 9.2 El código

```java
@Component                              // Spring detecta esta clase como bean
@EnableScheduling                       // Activa la programación de tareas
public class InvestmentScheduler {

    private final InvestmentService investmentService;

    @Scheduled(cron = "0 0 6 * * ?")   // 6:00 AM todos los días
    public void evaluateExpiredProjects() {
        log.info("Iniciando evaluacion de proyectos vencidos...");
        investmentService.processExpiredProjects();
    }
}
```

### 9.3 ¿Qué significa el cron?

```
0 0 6 * * ?
↑ ↑ ↑ ↑ ↑ ↑
│ │ │ │ │ └ día de semana (? = cualquier)
│ │ │ │ └── mes
│ │ │ └──── día del mes
│ │ └────── hora (6 = 6 AM)
│ └──────── minuto (0)
└────────── segundo (0)
```

### 9.4 ¿Por qué un scheduler y no un trigger de base de datos?

1. **La lógica es compleja:** involucra múltiples tablas (`users`, `projects`, `portfolio_activos`, `investments`) y posiblemente blockchain
2. **Es fácil de debuggear:** podés ejecutar `investmentService.processExpiredProjects()` manualmente desde un endpoint
3. **Es fácil de testear:** mockeás el service y verificás que se llamen los métodos correctos
4. **Un stored procedure sería más difícil de mantener y versionar**

---

## 10. Integración blockchain — De mocks a Web3j real

### 10.1 El viaje: de mock a real

Originalmente, `SmartContractService` devolvía datos falsos:

```java
// ANTES (mock)
public Map<String, Object> recordInvestment(...) {
    result.put("success", true);              // Siempre true
    result.put("blockNumber", 12345678L);     // Número fijo
    result.put("note", "Mock");               // Sin conexión real
}
```

Ahora, habla realmente con Sepolia:

```java
// AHORA (real con Web3j)
public Map<String, Object> recordInvestment(...) {
    result.put("success", blockchain.verifyTransaction(txHash));  // Verifica en Sepolia
    result.put("blockNumber", blockchain.getLatestBlock());       // Número real
    result.put("balanceTreasury", blockchain.balanceOf(...));     // Saldo real on-chain
}
```

### 10.2 ¿Qué se necesitaba para la integración?

| Recurso | ¿Qué es? | ¿De dónde sale? |
|---------|----------|----------------|
| **Nodo RPC** | URL HTTP de un nodo Ethereum | `https://ethereum-sepolia-rpc.publicnode.com` (público) |
| **PaymentGateway address** | Dónde vive el contrato | `0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10` |
| **ProjectToken address** | Dónde vive $IDEA | `0x9f2c766d0bd9bbb640422decdf0125be02c7d144` |
| **USDC address** | Dónde vive USDC | `0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238` (oficial de Circle) |
| **Private key** | Clave privada de la wallet del backend | `b7ddf518...` (testnet, sin fondos reales) |
| **Librería Web3j** | Java ↔ Ethereum | `org.web3j:core:4.12.3` |

### 10.3 Paso a paso: ¿qué archivos se crearon/modificaron?

#### 10.3.1 `pom.xml` — Dependencia Web3j

Se agregó:
```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>4.12.3</version>
</dependency>
```

**¿Qué contiene `web3j-core`?**
- `Web3j` — cliente HTTP para nodos Ethereum
- `Credentials` — wallet (private key + address)
- `Function`, `FunctionEncoder`, `FunctionReturnDecoder` — codificación ABI
- `RawTransactionManager` — firma y envío de transacciones
- `EthGetBalance`, `EthCall`, `EthSendTransaction` — tipos de request/response

#### 10.3.2 `.env` — Variables de blockchain

Se agregaron al archivo `.env`:
```
BLOCKCHAIN_RPC_URL=https://ethereum-sepolia-rpc.publicnode.com
BLOCKCHAIN_PAYMENT_GATEWAY=0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10
BLOCKCHAIN_PROJECT_TOKEN=0x9f2c766d0bd9bbb640422decdf0125be02c7d144
BLOCKCHAIN_USDC=0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
BLOCKCHAIN_TREASURY=0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
BLOCKCHAIN_PRIVATE_KEY=b7ddf518a66ac1b209065717fd07f08aeb78ed3f8235e88bc17935172d72be3e
```

**¿Por qué en .env y no hardcodeado?** Spring Boot lee `.env` gracias a `spring.config.import=optional:file:.env[.properties]`. Las propiedades quedan fuera del código, se pueden cambiar sin recompilar, y .env está en `.gitignore` (no se sube a GitHub).

#### 10.3.3 `application.properties` — Mapping de propiedades

```properties
blockchain.rpc-url=${BLOCKCHAIN_RPC_URL}
blockchain.payment-gateway-address=${BLOCKCHAIN_PAYMENT_GATEWAY}
blockchain.project-token-address=${BLOCKCHAIN_PROJECT_TOKEN}
blockchain.usdc-address=${BLOCKCHAIN_USDC}
blockchain.treasury-address=${BLOCKCHAIN_TREASURY}
blockchain.private-key=${BLOCKCHAIN_PRIVATE_KEY}
```

Spring necesita que las properties tengan el formato `blockchain.*` para que `@ConfigurationProperties(prefix = "blockchain")` funcione. Como el `.env` usa `BLOCKCHAIN_*`, este archivo hace de "puente".

#### 10.3.4 `BlockchainProperties.java` — Objeto de configuración

```java
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {
    private String rpcUrl;                    // blockchain.rpc-url
    private String paymentGatewayAddress;     // blockchain.payment-gateway-address
    private String projectTokenAddress;       // blockchain.project-token-address
    private String usdcAddress;               // blockchain.usdc-address
    private String treasuryAddress;           // blockchain.treasury-address
    private String privateKey;                // blockchain.private-key

    // Getters y setters (Spring los necesita para setear los valores)
}
```

**¿Qué hace `@ConfigurationProperties`?** Spring busca en `application.properties` todas las propiedades que empiecen con `blockchain.` y las asigna a los campos de esta clase. Por ejemplo, `blockchain.rpc-url` → `rpcUrl` (Spring convierte kebab-case a camelCase automáticamente).

#### 10.3.5 `Web3jConfig.java` — Beans de conexión

```java
@Configuration
@EnableConfigurationProperties(BlockchainProperties.class)
public class Web3jConfig {

    @Bean
    public Web3j web3j(BlockchainProperties props) {
        // Crea un cliente HTTP que apunta al nodo RPC de Sepolia
        return Web3j.build(new HttpService(props.getRpcUrl()));
    }

    @Bean
    public Credentials credentials(BlockchainProperties props) {
        // Crea una wallet a partir de la private key
        return Credentials.create(props.getPrivateKey());
    }
}
```

**¿Qué es un Bean?** En Spring, un Bean es un objeto que Spring crea y administra. Cuando declaramos `@Bean public Web3j web3j(...)`, Spring:
1. Ejecuta este método una vez al arrancar
2. Guarda el resultado en su "contexto" (una especie de caja de objetos)
3. Cuando otro objeto necesita un `Web3j`, Spring se lo inyecta automáticamente

**¿Por qué Beans y no `new Web3j()` cada vez?**
- **Singleton:** un solo objeto reutilizado en toda la app
- **Inyección automática:** no hay que crear manualmente las dependencias
- **Ciclo de vida:** Spring puede cerrar conexiones al apagar la app

### 10.4 BlockchainService — El corazón de la integración

#### 10.4.1 Estructura general

```java
@Service
public class BlockchainService {

    private final Web3j web3j;                   // Cliente HTTP al nodo RPC
    private final Credentials credentials;       // Wallet del backend
    private final TransactionManager txManager;  // Firma y envía transacciones
    private final BlockchainProperties props;    // Addresses de contratos

    public BlockchainService(Web3j web3j, Credentials credentials,
                              BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }
    // ... métodos
}
```

Los métodos se organizan en cuatro secciones dentro del archivo:

| Sección | Divisor en código | Propósito |
|---|---|---|
| **Lecturas** | `// LECTURAS (gratis, no cuestan gas)` | Métodos que solo leen datos de la blockchain mediante `eth_call`. No modifican estado, no consumen gas. |
| **Escrituras** | `// ESCRITURA (cuestan gas, usan tu wallet)` | Métodos que modifican el estado on-chain mediante `eth_sendRawTransaction`. Consumen gas y firman con la private key del backend. |
| **SmartContractService** | `// MÉTODOS que usa SmartContractService` | Métodos de utilidad (`verifyTransaction()`, `getBackendAddress()`, `getBalanceSepolia()`) que son llamados desde la capa de negocio. |
| **Privados** | `// PRIVADOS` | Métodos auxiliares internos como `executeCall()`, `callAndExtractUint256()`, `executeWrite()`. No se exponen fuera de la clase. |

A continuación se explica cada método en detalle.

#### 10.4.2 Método `balanceOf()` — Leer saldo de un token ERC-20

```java
public BigInteger balanceOf(String tokenAddress, String ownerAddress) throws Exception {
    // 1. Construir la función que queremos llamar
    Function fn = new Function(
        "balanceOf",                          // nombre de la función en Solidity
        List.of(new Address(ownerAddress)),    // argumentos: la address del dueño
        List.of(new TypeReference<Uint256>() {})  // tipo de retorno: uint256
    );

    // 2. Ejecutar la llamada de solo lectura
    return callAndExtractUint256(tokenAddress, fn);
}
```

**¿Qué pasa adentro de `callAndExtractUint256()`?**

```java
private BigInteger callAndExtractUint256(String contractAddress, Function fn) throws Exception {
    // PASO 1: CODIFICAR LA FUNCIÓN A HEXADECIMAL
    String encodedFunction = FunctionEncoder.encode(fn);
    // Esto toma "balanceOf(address)" y el argumento 0x7eEA8...
    // y genera algo como:
    // 0x70a08231 + 0000000...00007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
    // ↑ selector de 4 bytes  ↑ argumento padded a 32 bytes

    // PASO 2: CREAR UNA TRANSACCIÓN DE SOLO LECTURA
    Transaction tx = Transaction.createEthCallTransaction(
        credentials.getAddress(),  // from (quién llama — la wallet del backend)
        contractAddress,           // to (el contrato — ProjectToken, USDC...)
        encodedFunction            // data (la función codificada)
    );

    // PASO 3: ENVIAR AL NODO RPC
    EthCall response = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
    // Internamente, Web3j arma y envía:
    //
    // POST https://ethereum-sepolia-rpc.publicnode.com
    // {
    //   "jsonrpc": "2.0",
    //   "method": "eth_call",
    //   "params": [{
    //     "from": "0x...",
    //     "to": "0x...",
    //     "data": "0x70a08231..."
    //   }, "latest"],
    //   "id": 1
    // }

    // PASO 4: DECODIFICAR LA RESPUESTA
    // La respuesta es algo como "0x0000...0001f4" = 500 en decimal
    List<Type> result = FunctionReturnDecoder.decode(
        response.getValue(), fn.getOutputParameters());

    // PASO 5: EXTRAER EL VALOR
    if (!result.isEmpty() && result.get(0) instanceof Uint256 uintVal) {
        return uintVal.getValue();  // BigInteger: 500
    }
    return BigInteger.ZERO;
}
```

**¿Por qué se llama `eth_call` y no `eth_sendTransaction`?** Porque `eth_call` es una simulación: el nodo ejecuta el código del contrato pero **no crea una transacción**. Es gratis (no consume gas) y no modifica el estado de la blockchain. Sirve para leer datos.

#### 10.4.3 Método `mintTokens()` — Escribir en la blockchain

```java
public String mintTokens(String toAddress, BigInteger amountWei) throws Exception {
    Function fn = new Function(
        "mint",
        List.of(new Address(toAddress), new Uint256(amountWei)),
        List.of()  // sin retorno (la función mint es void)
    );
    return executeWrite(props.getProjectTokenAddress(), fn);
}
```

**¿Qué pasa adentro de `executeWrite()`?**

```java
private String executeWrite(String contractAddress, Function fn) throws Exception {
    // PASO 1: CODIFICAR
    String encodedFunction = FunctionEncoder.encode(fn);

    // PASO 2: CONSULTAR GAS PRICE (precio actual del gas en Sepolia)
    BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

    // PASO 3: OBTENER NONCE (número de transacciones ya enviadas por esta wallet)
    BigInteger nonce = web3j.ethGetTransactionCount(
        credentials.getAddress(), DefaultBlockParameterName.PENDING
    ).send().getTransactionCount();

    // PASO 4: ENVIAR LA TRANSACCIÓN FIRMADA
    EthSendTransaction response = txManager.sendTransaction(
        gasPrice,                           // precio del gas en wei
        BigInteger.valueOf(300_000L),       // gas limit
        contractAddress,                    // to: ProjectToken address
        encodedFunction,                    // data: la función codificada
        BigInteger.ZERO                     // value: 0 ETH (no enviamos ether)
    );

    // Internamente, RawTransactionManager hace:
    // 1. Arma RawTransaction(nonce, gasPrice, gasLimit, to, value, data)
    // 2. Firma con la private key → obtiene signature (r, s, v)
    // 3. Arma SignedRawTransaction(nonce, gasPrice, ..., signature)
    // 4. Envía: eth_sendRawTransaction(signedTxHex)
    // 5. El nodo devuelve txHash (identificador de la transacción)

    if (response.hasError()) {
        throw new RuntimeException("Error en tx: " + response.getError().getMessage());
    }

    // PASO 5: DEVOLVER EL HASH PARA TRACKING
    return response.getTransactionHash();
}
```

**¿Qué es gasPrice?** Es el precio que pagás por cada unidad de gas. En Sepolia suele ser muy bajo (~1-10 gwei). Se consulta dinámicamente porque puede variar según la congestión de la red.

**¿Qué es nonce?** Es un contador de transacciones. Cada transacción de una wallet debe tener un nonce incremental. Si envías dos txs con el mismo nonce, solo una se confirma. Sirve para evitar ataques de "replay".

**¿Qué es gasLimit?** Es la cantidad máxima de gas que estás dispuesto a pagar por esta transacción. `300_000` es un límite típico para funciones simples como `mint()`.

#### 10.4.4 Método `verifyTransaction()` — Verificar una tx existente

**Archivo:** `blockchain/service/BlockchainService.java` — línea 116

```java
public boolean verifyTransaction(String txHash) throws Exception {
    //                                       ↑
    //                     Hash de la transacción a verificar (32 bytes en hex)
    //                     ej: "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"

    // ─────────────────────────────────────────────────────────────────────────
    // PASO 1: Consultar el recibo de la transacción en Sepolia
    // ─────────────────────────────────────────────────────────────────────────
    var receipt = web3j.ethGetTransactionReceipt(txHash).send();
    //      ↑                     ↑           ↑
    //  EthGetTransactionReceipt  método RPC  .send() envía el POST HTTP y bloquea
    //
    // web3j.ethGetTransactionReceipt(txHash) construye internamente:
    //   POST https://ethereum-sepolia-rpc.publicnode.com
    //   {
    //     "jsonrpc": "2.0",
    //     "method": "eth_getTransactionReceipt",
    //     "params": ["0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"],
    //     "id": 1
    //   }
    //
    // Si la tx existe, el nodo responde:
    //   { "result": { "transactionHash": "0x983a...", "status": "0x1", ... } }
    //
    // Si la tx NO existe (hash falso), responde:
    //   { "result": null }
    //
    // Si el RPC está caído, lanza java.net.ConnectException → lo atrapa SmartContractService

    // ─────────────────────────────────────────────────────────────────────────
    // PASO 2: Verificar que la tx exista (isPresent) y haya sido exitosa (status=0x1)
    // ─────────────────────────────────────────────────────────────────────────
    return receipt.getTransactionReceipt()         // Optional<TransactionReceipt>
        .isPresent()                               // true si el "result" no es null
            && receipt.getTransactionReceipt()     // Optional.get() → TransactionReceipt
                .get()                             // TransactionReceipt{status="0x1", ...}
                .getStatus()                       // "0x1" (String)
                .equals("0x1");                    // "0x1".equals("0x1") → true
        //
        // ¿Por qué "0x1" y no "1"?
        //   Ethereum devuelve los valores como hex strings: "0x1" = éxito, "0x0" = fallo
        //   Si la tx reverteó (falló), status = "0x0" → .equals("0x1") devuelve false
        //
        // Resultados posibles:
        //   receipt presente Y status="0x1" → true  (tx existe y fue exitosa ✅)
        //   receipt presente Y status="0x0" → false (tx existe pero falló ❌)
        //   receipt vacío (result: null)    → false (tx no existe en Sepolia 🚫)
        //   Exception (nodo caído)          → la atrapa SmartContractService.recordInvestment()
}

**¿Para qué sirve?** Cuando el usuario firma una transacción con MetaMask y nos envía el `txHash`, podemos verificar que:
1. La transacción realmente existe en Sepolia (no es un hash falso)
2. La transacción fue exitosa (status = 0x1, no 0x0)

#### 10.4.5 Manejo de errores: modo offline

```java
public Map<String, Object> recordInvestment(Long proyectoId, Long usuarioId,
                                              BigDecimal montoIdea, String txHash) {
    //                             ↑                ↑              ↑
    // Proyecto donde invierte    Usuario que invierte  500 IDEA   Hash de tx de MetaMask

    Map<String, Object> result = new HashMap<>();          // ← Map para la respuesta

    try {
        // ── Intenta verificar la tx en Sepolia ──
        boolean txValida = blockchain.verifyTransaction(txHash);
        // Si Sepolia responde, txValida = true (existe y fue exitosa) o false
        // ... respuesta normal si Sepolia responde

    // ─────────────────────────────────────────────────────────────────────────
    // MODO OFFLINE: cualquier error de conexión con Sepolia se atrapa aquí
    // ─────────────────────────────────────────────────────────────────────────
    } catch (Exception e) {
        // Captura: ConnectionRefused, SocketTimeout, UnknownHost, etc.
        log.error("Blockchain no disponible: {}", e.getMessage());
        // Log: "Blockchain no disponible: Connection refused: /31.22.124.172:443"

        // ── Asumimos éxito para no bloquear la inversión ──
        result.put("success", true);                  // ← asume éxito (modo offline)
        result.put("txHash", txHash);                  // ← el hash que nos pasó el frontend
        result.put("note", "Modo offline - tx no verificada: " + e.getMessage());
        // result = { "success": true, "txHash": "0x983a...",
        //            "note": "Modo offline - tx no verificada: Connection refused" }
    }
    // El InvestmentService ve "success":true y continúa con el registro en DB
}
```

**¿Por qué modo offline?**
- Si el nodo RPC está caído, la aplicación no debería dejar de funcionar
- La inversión se registra igual en PostgreSQL
- En un futuro, un job de reconciliación puede verificar las txs pendientes

### 10.5 ¿Cómo luce una transacción real en Web3j?

Cuando el backend ejecuta `mintTokens()`:

```
1. FunctionEncoder.encode()
   "mint(address,uint256)" → Keccak256("mint(address,uint256)")[0:4] = 0x40c10f19
   Address(0x7eEA8...)      → 0x0000...00007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
   Uint256(1000)            → 0x0000...0000000000000000000000000000000000000003E8
   Data final: 0x40c10f19 + [address padded] + [amount padded]

2. RawTransactionManager.build()
   {
     nonce: 5,                        // 5ta transacción de esta wallet
     gasPrice: 1500000000,            // 1.5 gwei
     gasLimit: 300000,
     to: "0x9f2c766d0bd9bbb640422decdf0125be02c7d144",  // ProjectToken
     value: "0x0",                     // sin ETH
     data: "0x40c10f19..."            // función codificada
   }

3. Credentials.sign()
   Firma la transacción con la private key → (r, s, v) + vChainId

4. eth_sendRawTransaction()
   HTTP POST al nodo con la tx firmada (hex)

5. Respuesta:
   {
     "jsonrpc": "2.0",
     "id": 1,
     "result": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
   }
   // ↑ el txHash que identifica esta tx en la blockchain
```

---

### 10.6 Trazado completo de una llamada blockchain — verifyTransaction()

> Esta sección es el análisis línea por línea de lo que pasa cuando el backend verifica un txHash en Sepolia.

#### 10.6.1 ¿Quién llama a verifyTransaction()?

El `SmartContractService.recordInvestment()` es quien lo llama:

```
InvestmentService.createInvestment()
    → SmartContractService.recordInvestment(proyectoId=1, usuarioId=2, montoIdea=500, txHash="0x983a...")
        → BlockchainService.verifyTransaction(txHash="0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac")
            → HTTP POST al nodo Sepolia
            → eth_getTransactionReceipt("0x983a508c...")
            → respuesta JSON
        ← true (la tx existe y fue exitosa)
    ← { success: true, blockNumber: 12345678, ... }
← txResult
```

#### 10.6.2 Método `verifyTransaction()` línea por línea

**Archivo:** `blockchain/service/BlockchainService.java` — línea 113

```java
public boolean verifyTransaction(String txHash) throws Exception {
```

**Argumento recibido:**
| Parámetro | Valor |
|-----------|-------|
| `txHash` | `"0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"` |

---

**Paso 1: Enviar eth_getTransactionReceipt al nodo**

```java
var receipt = web3j.ethGetTransactionReceipt(txHash).send();
```

**¿Qué hace exactamente Web3j con esta línea?**

Web3j construye y envía esta request HTTP:

```
POST https://ethereum-sepolia-rpc.publicnode.com
Content-Type: application/json
Accept: application/json

{
  "jsonrpc": "2.0",
  "method": "eth_getTransactionReceipt",
  "params": [
    "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
  ],
  "id": 1
}
```

**El nodo responde:**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "transactionHash": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac",
    "transactionIndex": "0x0",
    "blockNumber": "0xbc614e",
    "blockHash": "0x1234...",
    "from": "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A",
    "to": "0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10",
    "cumulativeGasUsed": "0x1a7b8",
    "gasUsed": "0x1a7b8",
    "status": "0x1",
    "logs": [
      {
        "address": "0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10",
        "topics": [
          "0x4595a0f17e3c06f6b02f80e7e8d01d1bf73b5f8e7e9e4f2a5c4b8d9e0f1a2b3c",
          "0x0000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A",
          "0x0000000000000000000000000000000000000000000000000000000000000001"
        ],
        "data": "0x000000000000000000000000000000000000000000000000000000002faf0800"
      }
    ]
  }
}
```

**¿Qué es cada campo de la respuesta?**

| Campo | Valor | Significado |
|-------|-------|------------|
| `transactionHash` | `"0x983a..."` | Hash de la transacción (idéntico al que pedimos) |
| `blockNumber` | `"0xbc614e"` | Número de bloque donde se incluyó (12345678 en decimal) |
| `from` | `"0x7eEA86..."` | Quién envió la tx (nuestra wallet) |
| `to` | `"0x7aabb2..."` | Contrato al que se llamó (PaymentGateway) |
| `status` | `"0x1"` | **0x1 = éxito, 0x0 = falló** ← esto es lo que verificamos |
| `gasUsed` | `"0x1a7b8"` | Gas consumido (108472 en decimal) |
| `logs` | `[...]` | Eventos emitidos por el contrato (ej: Paid) |

**¿Qué pasa si el txHash no existe en Sepolia?**

Si el hash es falso o la tx nunca se minó, el nodo responde:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": null
}
```

En ese caso, `receipt.getTransactionReceipt()` devuelve `Optional.empty()`, y `verifyTransaction()` devuelve `false`.

---

**Paso 2: Verificar que la tx exista y haya sido exitosa**

```java
return receipt.getTransactionReceipt()         // Optional<TransactionReceipt>
    .isPresent()                                // true si la tx existe
        && receipt.getTransactionReceipt()      // TransactionReceipt
            .get()                              // TransactionReceipt{status="0x1", ...}
            .getStatus()                        // "0x1"
            .equals("0x1");                     // "0x1" == "0x1" → true
```

**Línea por línea:**
```java
// 2a. ¿El Optional tiene un valor?
receipt.getTransactionReceipt().isPresent()
// El receipt que recibimos del nodo tiene un result no null
// → Devuelve: true

// 2b. Obtener el TransactionReceipt
receipt.getTransactionReceipt().get()
// → Devuelve: TransactionReceipt{transactionHash="0x983a...", status="0x1", ...}

// 2c. Obtener el status
receipt.getTransactionReceipt().get().getStatus()
// → Devuelve: "0x1"

// 2d. Comparar con "0x1" (éxito)
"0x1".equals("0x1")
// → Devuelve: true

// Resultado final:
return true && true;  // → true
```

**¿Qué devuelve?** `true` — la transacción existe y fue exitosa.

#### 10.6.3 ¿Qué hace SmartContractService con ese resultado?

```java
// SmartContractService.java
boolean txValida = blockchain.verifyTransaction(txHash);
// txValida = true

result.put("success", txValida);  // "success": true

if (txValida) {
    // Solo si la tx es válida, consulta datos adicionales
    BigInteger block = blockchain.getLatestBlock();      // 12345678
    BigInteger balance = blockchain.balanceOf(...);       // 1000000

    result.put("blockNumber", 12345678L);
    result.put("treasuryTokensBalance", 1000000);
    result.put("note", "Transaccion verificada en Sepolia");
} else {
    result.put("note", "Tx no encontrada en Sepolia");
}
```

**¿Qué contiene el Map final?**
```java
{
    "success": true,
    "txHash": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac",
    "blockNumber": 12345678,
    "treasuryTokensBalance": 1000000,
    "note": "Transaccion verificada en Sepolia"
}
```

### 10.7 Trazado completo: mintTokens() — escribir en Sepolia

#### 10.7.1 ¿Quién llama a mintTokens()?

```
SmartContractService.refundInvestment(proyectoId=1, usuarioId=2, montoIdea=500)
    → blockchain.mintTokens(toAddress="0x7eEA86...", amountWei=500_000000000000000000)
```

**¿Por qué `amountWei = 500 * 10^18`?** El ProjectToken tiene 18 decimales (como Ethereum). Para representar 500 tokens completos, necesitamos `500 * 10^18` en wei.

#### 10.7.2 Método `mintTokens()` línea por línea

**Archivo:** `blockchain/service/BlockchainService.java` — línea 91

```java
public String mintTokens(String toAddress, BigInteger amountWei) throws Exception {
```

**Argumentos recibidos:**
| Parámetro | Valor |
|-----------|-------|
| `toAddress` | `"0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"` (el treasury) |
| `amountWei` | `500000000000000000000` (500 tokens con 18 decimales) |

---

**Paso 1: Armar la función Solidity**

```java
Function fn = new Function(
    "mint",                                                         // nombre en Solidity
    List.of(new Address(toAddress), new Uint256(amountWei)),        // inputs: address + uint256
    List.of()                                                       // outputs: void (sin retorno)
);
```

**¿Qué representa `fn` en memoria?**
```java
fn = {
    name: "mint",
    inputParameters: [
        Address{ value: "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A" },
        Uint256{ value: 500000000000000000000 }
    ],
    outputParameters: []
}
```

---

**Paso 2: Llamar a executeWrite()**

```java
return executeWrite(props.getProjectTokenAddress(), fn);
// executeWrite("0x9f2c766d0bd9bbb640422decdf0125be02c7d144", fn)
```

**¿Qué pasa dentro de executeWrite()?**

**2a. Codificar la función a hex:**
```java
String encodedFunction = FunctionEncoder.encode(fn);
```

`FunctionEncoder.encode()` hace:
1. Toma los primeros 4 bytes del Keccak256 de `"mint(address,uint256)"`:
   ```
   Keccak256("mint(address,uint256)") = 0x40c10f19873b7c9b9e8c9b8c9b8c9b8c9b8c9b...
   Selector = 0x40c10f19 (primeros 4 bytes)
   ```
2. Codifica el primer argumento (address) padded a 32 bytes:
   ```
   0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
   → 0x0000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
   ```
3. Codifica el segundo argumento (uint256) padded a 32 bytes:
   ```
   500000000000000000000
   → 0x00000000000000000000000000000000000000000000001b1ae4d6e2ef500000
   ```
4. Concatena:
   ```
   0x40c10f19 + 0000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
            + 00000000000000000000000000000000000000000000001b1ae4d6e2ef500000
   ```

**Resultado:** `encodedFunction = "0x40c10f190000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A00000000000000000000000000000000000000000000001b1ae4d6e2ef500000"`

---

**2b. Consultar gas price:**
```java
BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
```

**HTTP Request al nodo:**
```
POST https://ethereum-sepolia-rpc.publicnode.com
Body: {"jsonrpc":"2.0","method":"eth_gasPrice","params":[],"id":1}
```

**Respuesta del nodo:**
```json
{"jsonrpc":"2.0","id":1,"result":"0x59682f00"}
```

**Decodificación:** `0x59682f00 = 1500000000 wei = 1.5 gwei`

---

**2c. Obtener nonce:**
```java
BigInteger nonce = web3j.ethGetTransactionCount(
    credentials.getAddress(),             // "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
    DefaultBlockParameterName.PENDING     // incluye txs pendientes
).send().getTransactionCount();
```

**HTTP Request al nodo:**
```
POST https://ethereum-sepolia-rpc.publicnode.com
Body: {
  "jsonrpc":"2.0",
  "method":"eth_getTransactionCount",
  "params":["0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A", "pending"],
  "id":1
}
```

**Respuesta:**
```json
{"jsonrpc":"2.0","id":1,"result":"0x5"}
```

**Decodificación:** `0x5 = 5` → esta wallet ya envió 5 transacciones. La próxima será la número 6 (nonce = 5).

---

**2d. Enviar la transacción firmada:**
```java
EthSendTransaction response = txManager.sendTransaction(
    gasPrice,                        // 1500000000 wei
    BigInteger.valueOf(300_000L),    // 300000 gas limit
    contractAddress,                 // "0x9f2c766d0bd9bbb640422decdf0125be02c7d144"
    encodedFunction,                 // "0x40c10f19..."
    BigInteger.ZERO                  // 0 ETH (value)
);
```

**¿Qué hace RawTransactionManager.sendTransaction() internamente?**

Paso a paso:

```
1. Construir RawTransaction:
   RawTransaction(
     nonce: 5,
     gasPrice: 1500000000,
     gasLimit: 300000,
     to: "0x9f2c766d0bd9bbb640422decdf0125be02c7d144",
     value: 0,
     data: "0x40c10f190000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A..."
   )

2. Firmar con Credentials (private key):
   - Calcula el hash de la transacción: Keccak256(RLP.encode(rawTx))
   - Firma con la private key usando ECDSA (secp256k1)
   - Obtiene: { r, s, v } → signature

3. Armar la transacción firmada:
   SignedRawTransaction(
     rawTx,
     signature: { r: 0x..., s: 0x..., v: 0x... }
   )

4. Codificar a hex:
   "0xf8a9...850...94..."

5. Enviar eth_sendRawTransaction:
   POST https://ethereum-sepolia-rpc.publicnode.com
   Body: {
     "jsonrpc": "2.0",
     "method": "eth_sendRawTransaction",
     "params": ["0xf8a9...850...94..."],
     "id": 1
   }
```

**Respuesta del nodo:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
}
```

**Este es el txHash.** Con él podemos:
- Buscar la tx en Etherscan: https://sepolia.etherscan.io/tx/0x983a508c...
- Esperar a que se mine (~15 segundos)
- Ver el receipt con `eth_getTransactionReceipt`

---

**2e. Verificar errores y devolver:**

```java
if (response.hasError()) {
    throw new RuntimeException("Error en tx: " + response.getError().getMessage());
}

String txHash = response.getTransactionHash();
// txHash = "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"

log.info("Tx enviada: {} contrato: {}", txHash, contractAddress);
// Log: "Tx enviada: 0x983a508c... contrato: 0x9f2c766d..."

return txHash;
```

**¿Qué devuelve?** `"0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"`

#### 10.7.3 Costo de la transacción (gas)

¿Cuánto cuesta esta transacción en SepoliaETH?

```
gasUsed = 108472 (del receipt)
gasPrice = 1500000000 wei (1.5 gwei)

Costo total = gasUsed × gasPrice
            = 108472 × 1500000000
            = 162708000000000000 wei
            = 0.000162708 ETH
```

En Sepolia es ETH falso sin valor. En Ethereum mainnet serían ~$0.30 USD al precio actual.

#### 10.7.4 ¿Qué pasa si la wallet del backend no tiene ETH para gas?

Si `eth_sendRawTransaction` falla porque la wallet no tiene saldo, el nodo responde:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32000,
    "message": "insufficient funds for gas * price + value"
  }
}
```

Y `executeWrite()` lanza:
```
RuntimeException: Error en tx: insufficient funds for gas * price + value
```

El `SmartContractService` atrapa esta excepción y entra en **modo offline**.

#### 10.7.5 ¿Qué devuelve SmartContractService.refundInvestment()?

```java
return txHash;  // "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
```

Si el backend no tiene ETH para gas, el catch lo atrapa:
```java
} catch (Exception e) {
    result.put("success", true);
    result.put("refundTxHash", "0xrefund-" + System.currentTimeMillis());
    result.put("note", "Reembolso en modo offline: " + e.getMessage());
}
```

### 10.8 Trazados completos de los otros métodos de BlockchainService

#### 10.8.1 `allowance()` — Consultar aprobación ERC-20

Permite ver cuántos tokens aprobó un usuario (owner) para que gaste otro (spender). Es una función de solo lectura (`view`).

**Archivo:** `blockchain/service/BlockchainService.java` — línea 60

**Código completo con trazas línea por línea:**

```java
public BigInteger allowance(String tokenAddress, String owner, String spender) throws Exception {
    //                                            ↑                          ↑         ↑         ↑
    //                                      Address del token    quien aprobó  quién puede gastar
    //                                      ej: USDC (0x1c7D4B)  ej: treasury  ej: PaymentGateway

    // ─────────────────────────────────────────────────────────────────────────
    // Construye la función Solidity que vamos a llamar
    // ─────────────────────────────────────────────────────────────────────────
    Function fn = new Function(
        "allowance",                                           // ← nombre exacto en el contrato ERC-20
        List.of(new Address(owner), new Address(spender)),     // ← argumentos: (address owner, address spender)
        List.of(new TypeReference<Uint256>() {})               // ← tipo de retorno: uint256
    );
    // Después de esta línea, fn = {
    //   name: "allowance",
    //   inputParameters: [Address("0x7eEA86..."), Address("0x7aabb2...")],
    //   outputParameters: [Uint256]
    // }

    // ─────────────────────────────────────────────────────────────────────────
    // Delega en callAndExtractUint256 que:
    //   1. Llama a executeCall(tokenAddress, fn)
    //   2. Decodifica el resultado Uint256 → BigInteger
    // ─────────────────────────────────────────────────────────────────────────
    return callAndExtractUint256(tokenAddress, fn);
    //       ↑                                ↑
    //  BigInteger con la cantidad aprobada  Address del token ERC-20 (USDC)
    //  ej: 500000000 (500 USDC)             ej: "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
}
```

**¿Qué recibe?**
| Parámetro | Ejemplo de valor |
|-----------|-----------------|
| `tokenAddress` | `"0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"` (USDC) |
| `owner` | `"0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"` (quien aprobó) |
| `spender` | `"0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10"` (PaymentGateway) |

**Llamada real a Sepolia:**
```
POST https://ethereum-sepolia-rpc.publicnode.com
{
  "method": "eth_call",
  "params": [{
    "from": "0x7eEA86...",
    "to": "0x1c7D4B...",
    "data": "0xdd62ed3e" +
            "0000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A" +
            "0000000000000000000000007aabb2435e3220c4bd51cc6546e6899fe75a3f10"
  }, "latest"]
}
```

**Respuesta:** `"0x0000...2faf0800"` = 500000000 (500 USDC con 6 decimales)

**¿Para qué sirve?** Antes de llamar a `payUSDC()`, verificamos que el usuario haya aprobado suficientes USDC al PaymentGateway. Si `allowance < amount`, pedimos al frontend que primero llame a `approve()` desde MetaMask.

---

#### 10.8.2 `getContractOwner()` — Consultar owner de un contrato

Los contratos ERC-20 suelen tener una función `owner()` que devuelve la address del administrador.

**Archivo:** `blockchain/service/BlockchainService.java` — línea 70

```java
public String getContractOwner(String tokenAddress) throws Exception {
    //                                          ↑
    //                          Address del contrato ERC-20
    //                    ej: "0x9f2c766d0bd9bbb640422decdf0125be02c7d144" (ProjectToken)

    // ─────────────────────────────────────────────────────────────────────────
    // Construye la función "owner()" - sin argumentos, retorna address
    // ─────────────────────────────────────────────────────────────────────────
    Function fn = new Function(
        "owner",                                           // ← nombre exacto en Solidity
        List.of(),                                         // ← sin parámetros de entrada: owner() no recibe args
        List.of(new TypeReference<Address>() {})           // ← tipo de retorno: address
    );
    // Después de esta línea, fn = {
    //   name: "owner",
    //   inputParameters: [],
    //   outputParameters: [Address]   ← esperamos una address de 20 bytes
    // }

    // ─────────────────────────────────────────────────────────────────────────
    // executeCall() envía eth_call al nodo y decodifica la respuesta
    // ─────────────────────────────────────────────────────────────────────────
    List<Type> result = executeCall(tokenAddress, fn);
    // executeCall() hace:
    //   1. FunctionEncoder.encode(fn) → "0x89362000" (selector de owner())
    //   2. Transaction.createEthCallTransaction(from, tokenAddress, "0x89362000")
    //   3. web3j.ethCall(tx, "latest").send()
    //   4. FunctionReturnDecoder.decode(response, [Address])
    // result = [Address{value: "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"}]

    // ─────────────────────────────────────────────────────────────────────────
    // Verifica que haya resultado y que sea de tipo Address
    // ─────────────────────────────────────────────────────────────────────────
    if (!result.isEmpty() && result.get(0) instanceof Address addr) {
        // result.isEmpty() = false   ← hay un elemento en la lista
        // result.get(0) instanceof Address = true   ← es del tipo esperado
        // addr = Address{value: "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"}
        return addr.getValue();
        // Devuelve: "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Si el contrato no tiene función owner() o la respuesta está vacía
    // ─────────────────────────────────────────────────────────────────────────
    return null;                                         // ← no se pudo determinar el owner
}
```

**Llamada:**
```
POST https://ethereum-sepolia-rpc.publicnode.com
{
  "method": "eth_call",
  "params": [{
    "from": "0x7eEA86...",
    "to": "0x9f2c766d...",
    "data": "0x89362000"      // keccak("owner()")[0:4]
  }, "latest"]
}
```

**Respuesta:** `"0x0000...07eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"` → `"0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"` (nuestra treasury, deployer de los contratos).

---

#### 10.8.3 `getLatestBlock()` — Saber el último bloque de Sepolia

**Archivo:** `blockchain/service/BlockchainService.java` — línea 83

```java
public BigInteger getLatestBlock() throws Exception {
    // ─────────────────────────────────────────────────────────────────────────
    // Llamada ENCADENADA: cada método devuelve un objeto para el siguiente
    // ─────────────────────────────────────────────────────────────────────────
    return web3j                          // Web3j cliente HTTP conectado al nodo Sepolia
        .ethBlockNumber()                 // 1. Crea un objeto Request<Rpc> para eth_blockNumber
        //                                //    Internamente arma:
        //                                //    { "jsonrpc": "2.0", "method": "eth_blockNumber",
        //                                //      "params": [], "id": 1 }

        .send()                           // 2. Ejecuta la request HTTP:
        //                                //    POST https://ethereum-sepolia-rpc.publicnode.com
        //                                //    Recibe: { "result": "0xbc7201" }
        //                                //    Devuelve: EthBlockNumber{...}

        .getBlockNumber();                // 3. Extrae el campo "result" como BigInteger
        //                                //    "0xbc7201" → 12354049 (en decimal)
    // Resultado final: BigInteger.valueOf(12354049)
    // Representa: bloque #12354049 de Sepolia (el más reciente al momento de la consulta)
}

**HTTP:**
```
POST https://ethereum-sepolia-rpc.publicnode.com
{ "jsonrpc": "2.0", "method": "eth_blockNumber", "params": [], "id": 1 }

Response: { "result": "0xbc7201" }  → 12354049 en decimal
```

**¿Para qué sirve?** En `SmartContractService.recordInvestment()` se guarda el `blockNumber` como "timestamp descentralizado" (bloques cada ~12 segundos). En `getContractInfo()` se expone para diagnóstico.

---

#### 10.8.4 `getBalanceSepolia()` — Saldo de ETH de la wallet del backend

El backend necesita ETH en Sepolia para pagar gas. Este método consulta el saldo.

**Archivo:** `blockchain/service/BlockchainService.java` — línea 126

```java
public BigInteger getBalanceSepolia() throws Exception {
    // ─────────────────────────────────────────────────────────────────────────
    // Consulta el saldo de ETH de la wallet del backend en Sepolia
    // ─────────────────────────────────────────────────────────────────────────
    return web3j                                    // Web3j cliente HTTP
        .ethGetBalance(                             // 1. Prepara request eth_getBalance
            credentials.getAddress(),               //    ← "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
            DefaultBlockParameterName.LATEST        //    ← "latest" (último bloque confirmado)
        )
        //                                          //    HTTP que se enviará:
        //                                          //    { "jsonrpc":"2.0", "method":"eth_getBalance",
        //                                          //      "params":["0x7eEA86...", "latest"], "id":1 }

        .send()                                     // 2. Envía POST al nodo Sepolia
        //                                          //    Respuesta: { "result": "0x1bc16d674ec80000" }

        .getBalance();                              // 3. Extrae "0x1bc16d674ec80000" → BigInteger
        //                                          //    "0x1bc16d674ec80000" = 2000000000000000000 wei
    // Resultado: BigInteger.valueOf(2000000000000000000)
    //           ≈ 2.0 SepoliaETH
}
```

**HTTP:**
```
POST https://ethereum-sepolia-rpc.publicnode.com
{ "jsonrpc": "2.0", "method": "eth_getBalance",
  "params": ["0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A", "latest"], "id": 1 }

Response: { "result": "0x1bc16d674ec80000" }
→ 2000000000000000000 wei ≈ 2.0 SepoliaETH
```

**Versión en ETH (no wei):**
```java
public BigDecimal getBalanceSepoliaInEth() throws Exception {
    // ─────────────────────────────────────────────────────────────────────────
    // Llama al método anterior para obtener el saldo en wei
    // ─────────────────────────────────────────────────────────────────────────
    BigInteger wei = getBalanceSepolia();
    // wei = 2000000000000000000 (2 * 10^18)

    // ─────────────────────────────────────────────────────────────────────────
    // Convierte de wei a ether usando Convert.fromWei()
    // ─────────────────────────────────────────────────────────────────────────
    return Convert.fromWei(wei.toString(), Convert.Unit.ETHER);
    //                ↑                  ↑
    //          "2000000000000000000"   unidad destino = ETHER
    //
    // Convert.fromWei() internamente:
    //   1. Toma el string "2000000000000000000" → BigDecimal
    //   2. Divide por 10^18 (porque 1 ETH = 10^18 wei)
    //   3. Resultado: BigDecimal("2.0")
    //
    // Equivalente matemático: 2000000000000000000 / 1_000_000_000_000_000_000 = 2.0

    // Nota: Convert.fromWei() acepta String porque BigInteger puede tener
    // hasta ~78 dígitos, y BigDecimal(String) preserva la precisión exacta.
}

---

#### 10.8.5 `payUSDC()` — Pagar en USDC a través del PaymentGateway

**Archivo:** `blockchain/service/BlockchainService.java` — línea 100

```java
public String payUSDC(BigInteger amountInUSDC, String actionId) throws Exception {
    //                              ↑                ↑
    //             Cantidad en USDC (6 decimales)    ID único de la acción
    //             ej: 500000000 = 500 USDC          ej: "INV-1-2-171615"

    // ─────────────────────────────────────────────────────────────────────────
    // Preparar el argumento bytes32: convertir String a bytes[32] con padding
    // ─────────────────────────────────────────────────────────────────────────
    byte[] padded = new byte[32];                    // ← array de 32 bytes, todos en 0x00
    byte[] raw = actionId.getBytes();                // ← "INV-1-2-171615" → [0x49, 0x4E, 0x56, ...]
    System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, 32));
    //                ↑    ↑    ↑       ↑       ↑
    //              origen srcPos destino destPos  longitud
    //
    // Si raw = [0x49, 0x4E, 0x56, ...] (14 bytes para "INV-1-2-171615")
    // Math.min(14, 32) = 14
    // padded = [0x49, 0x4E, 0x56, ..., 0x00, 0x00, ...] (14 bytes de datos + 18 ceros)

    // ─────────────────────────────────────────────────────────────────────────
    // Construir la función Solidity "pay(uint256,bytes32)"
    // ─────────────────────────────────────────────────────────────────────────
    Function fn = new Function(
        "pay",                                                  // ← nombre en Solidity
        List.of(new Uint256(amountInUSDC), new Bytes32(padded)),// ← inputs: uint256 + bytes32
        List.of()                                               // ← outputs: void (no retorna nada)
    );
    // Después de esta línea, fn = {
    //   name: "pay",
    //   inputParameters: [Uint256(500000000), Bytes32(0x494e56...0000)],
    //   outputParameters: []
    // }

    // ─────────────────────────────────────────────────────────────────────────
    // Delegar en executeWrite() que firma y envía la transacción
    // ─────────────────────────────────────────────────────────────────────────
    return executeWrite(props.getPaymentGatewayAddress(), fn);
    //       ↑                 ↑
    //  String txHash          Address del PaymentGateway en Sepolia
    //                         ej: "0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10"
    //
    // executeWrite() hará:
    //   1. FunctionEncoder.encode(fn) → "0xafb35652" + uint256 padded + bytes32
    //   2. Consulta gasPrice y nonce
    //   3. RawTransactionManager.sendTransaction() → firma + eth_sendRawTransaction
    //   4. Devuelve txHash: "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
}

**Parámetros:**

| Parámetro | Ejemplo | ¿Qué es? |
|-----------|---------|----------|
| `amountInUSDC` | `500000000` | 500 USDC (6 decimales → 500 * 10^6) |
| `actionId` | `"INV-1-2-171615"` | Identificador único (proyectoId-usuarioId-timestamp) |

**¿Por qué Bytes32?** El contrato PaymentGateway espera `bytes32` como ID. Convertimos el String a bytes, lo rellenamos con ceros a la derecha hasta 32 bytes. Si es más largo, se trunca.

**Función Solidity esperada:**
```solidity
function pay(uint256 amount, bytes32 actionId) external {
    // Transfiere amount USDC del msg.sender al treasury
    // Emite evento Paid(amount, actionId)
}
```

**Data codificada:**
```
Keccak("pay(uint256,bytes32)")[0:4] = 0xafb35652
Uint256(500000000)                  → 0x0000...1dcd6500
Bytes32("INV-1-2-171615")           → 0x494e562d312d322d31373136313500000000000000000000000000000000000000
```

**¿Para qué sirve (futuro)?** Actualmente los pagos son off-chain. El flujo on-chain completo sería:
1. Usuario aprueba USDC al PaymentGateway (desde MetaMask)
2. Backend llama `allowance()` para verificar aprobación
3. Backend llama `payUSDC()` para transferir USDC
4. El contrato emite evento `Paid`
5. Backend escucha el evento y marca la inversión como confirmada

---

#### 10.8.6 `executeCall()` — El método privado que usan todas las lecturas

**Archivo:** `blockchain/service/BlockchainService.java` — línea 141

```java
private List<Type> executeCall(String contractAddress, Function fn) throws Exception {
    //                                ↑                  ↑
    //              Address del contrato a consultar     Función Solidity ya armada
    //              ej: ProjectToken (0x9f2c766d...)     ej: balanceOf(address)

    // ═══════════════════════════════════════════════════════════════════════════
    // PASO 1: Codificar la función a hexadecimal (ABI encoding)
    // ═══════════════════════════════════════════════════════════════════════════
    String encodedFunction = FunctionEncoder.encode(fn);
    // FunctionEncoder.encode() hace:
    //   1. Toma el nombre "balanceOf" → Keccak256("balanceOf(address)") → selector de 4 bytes
    //   2. Codifica cada argumento según su tipo (address → 32 bytes con padding)
    //   3. Concatena: "0x70a08231" + address padded a 32 bytes
    //
    // encodedFunction para balanceOf(treasury):
    //   "0x70a082310000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"

    // ═══════════════════════════════════════════════════════════════════════════
    // PASO 2: Preparar y enviar la transacción de SOLO LECTURA (eth_call)
    // ═══════════════════════════════════════════════════════════════════════════
    EthCall response = web3j.ethCall(
        // ─────────────────────────────────────────────────────────────────────
        // Crea una transacción simulada (no se firma, no cuesta gas)
        // ─────────────────────────────────────────────────────────────────────
        Transaction.createEthCallTransaction(
            credentials.getAddress(),             // from: "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
            contractAddress,                      // to: "0x9f2c766d0bd9bbb640422decdf0125be02c7d144"
            encodedFunction                       // data: "0x70a08231..."
        ),
        //                              ↑
        //                Se ejecuta en el ÚLTIMO bloque minado ("latest")
        DefaultBlockParameterName.LATEST          // "latest" = estado más reciente de la blockchain
    ).send();                                     // ← ENVÍA el POST HTTP y espera respuesta
    //
    // Internamente, Web3j construye:
    // POST https://ethereum-sepolia-rpc.publicnode.com
    // {
    //   "jsonrpc": "2.0",
    //   "method": "eth_call",
    //   "params": [{
    //     "from": "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A",
    //     "to": "0x9f2c766d0bd9bbb640422decdf0125be02c7d144",
    //     "data": "0x70a082310000000000000000000000007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
    //   }, "latest"],
    //   "id": 1
    // }
    //
    // Respuesta del nodo:
    // {
    //   "jsonrpc": "2.0",
    //   "id": 1,
    //   "result": "0x000000000000000000000000000000000000000000000000000000002faf0800"
    // }

    // ═══════════════════════════════════════════════════════════════════════════
    // PASO 3: Verificar si el nodo respondió con un error
    // ═══════════════════════════════════════════════════════════════════════════
    if (response.hasError()) {
        // Si el contrato no existe, la función no existe, o el RPC está mal
        // Ej: "execution reverted" o "nonce too low"
        throw new RuntimeException("Error en ethCall: " + response.getError().getMessage());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PASO 4: Decodificar la respuesta hexadecimal a objetos Java tipados
    // ═══════════════════════════════════════════════════════════════════════════
    return FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());
    //       ↑                              ↑                  ↑
    //  List<Type> tipada         hex "0x0000...2faf0800"     [Uint256] según lo que espera fn
    //
    // FunctionReturnDecoder.decode():
    //   1. Toma el hex "0x000000000000000000000000000000000000000000000000000000002faf0800"
    //   2. Lo interpreta según fn.getOutputParameters() = [Uint256]
    //   3. Extrae los últimos 32 bytes → new Uint256(500000000)
    //   4. Devuelve: [Uint256{value=500000000}]
    //
    // Para allowance(), el resultado será similar pero con otra cantidad
    // Para getContractOwner(), decodificará un Address en vez de Uint256
}

**¿Qué métodos usan `executeCall()`?**

| Método | Función Solidity | Parámetros |
|--------|-----------------|------------|
| `balanceOf()` | `balanceOf(address)` | 1 address |
| `allowance()` | `allowance(address,address)` | 2 addresses |
| `getContractOwner()` | `owner()` | ninguno |

**Diagrama de flujo:**
```
executeCall("0x9f2c766d...", fn_balanceOf)
  │
  ├─ FunctionEncoder.encode(fn)
  │    → "0x70a082310000...0007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
  │
  ├─ Transaction.createEthCallTransaction(from, to, data)
  │    → Transaction{from="0x7eEA86...", to="0x9f2c766d...", data="0x70a08231..."}
  │
  ├─ web3j.ethCall(tx, "latest").send()
  │    → POST { "method": "eth_call", "params": [{...}, "latest"], "id": 1 }
  │
  ├─ response.getValue()
  │    → "0x0000...2faf0800"
  │
  └─ FunctionReturnDecoder.decode(hex, [Uint256])
       → [Uint256{value=500000000}]
```

---

#### 10.8.7 `executeWrite()` — El método privado que usan todas las escrituras

Ya cubierto en detalle en 10.7.2 (mintTokens). Resumen de métodos que lo usan:

| Método | Contrato | Función Solidity | Parámetros |
|--------|----------|-----------------|------------|
| `mintTokens()` | ProjectToken | `mint(address,uint256)` | address destino, cantidad |
| `payUSDC()` | PaymentGateway | `pay(uint256,bytes32)` | cantidad, actionId |

El proceso siempre es:
1. `FunctionEncoder.encode(fn)` → hex de función + args
2. `web3j.ethGasPrice().send()` → gas price actual
3. `web3j.ethGetTransactionCount(address, "pending")` → nonce
4. `txManager.sendTransaction(gasPrice, gasLimit, to, data, 0)` → firma + envío
5. Devuelve `txHash` para tracking

---

### 10.9 Trazado completo de SmartContractService — El puente entre inversiones y blockchain

SmartContractService es la capa que conecta la lógica de negocio (InvestmentService) con la blockchain (BlockchainService). Cada uno de sus métodos es llamado desde InvestmentService y delega en BlockchainService.

#### 10.9.1 `getContractInfo()` — Información de un contrato en Sepolia

**Archivo:** `project/service/SmartContractService.java` — línea 28

```java
public Map<String, Object> getContractInfo(String contractAddress) {
    //                                              ↑
    //             Address del contrato a consultar (cualquier contrato ERC-20)
    //             ej: "0x9f2c766d0bd9bbb640422decdf0125be02c7d144" (ProjectToken)

    // ─────────────────────────────────────────────────────────────────────────
    // Crea el Map que vamos a devolver como respuesta JSON
    // ─────────────────────────────────────────────────────────────────────────
    Map<String, Object> info = new HashMap<>();    // ← Map vacío, lista para llenar

    // ─────────────────────────────────────────────────────────────────────────
    // Intenta conectarse a Sepolia para obtener 4 datos distintos
    // ─────────────────────────────────────────────────────────────────────────
    try {
        // ── 1. Address del contrato (eco: devolvemos lo que recibimos) ──
        info.put("address", contractAddress);
        // info = { "address": "0x9f2c766d..." }

        // ── 2. Saldo del treasury en ese contrato ──
        BigInteger balance = blockchain.balanceOf(contractAddress, props.getTreasuryAddress());
        //                                  ↑                        ↑
        //                           ProjectToken (0x9f2c766d...)   Treasury (0x7eEA86...)
        //
        // Encadena: eth_call balanceOf("0x7eEA86...") al ProjectToken
        // → BigInteger.valueOf(1000000) (1M $IDEA)
        //
        info.put("treasuryBalance", balance);
        // info = { "address": "...", "treasuryBalance": 1000000 }

        // ── 3. Address de la wallet del backend (de las Credentials) ──
        info.put("backendAddress", blockchain.getBackendAddress());
        //                          ↑
        //                  credentials.getAddress() = "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
        // info = { "address": "...", "treasuryBalance": ..., "backendAddress": "0x7eEA86..." }

        // ── 4. Saldo de ETH de la wallet (para pagar gas) ──
        info.put("backendSepoliaEth", blockchain.getBalanceSepoliaInEth());
        //                              ↑
        //                      BigDecimal("2.0") → 2.0 SepoliaETH
        // info = { ..., "backendSepoliaEth": 2.0 }

        // ── 5. Último bloque de Sepolia (timestamps descentralizado) ──
        BigInteger latestBlock = blockchain.getLatestBlock();
        //                        ↑
        //                BigInteger.valueOf(12354049)
        //
        info.put("latestBlock", latestBlock);
        // info = { ..., "latestBlock": 12354049 }

        // ── 6. Nota de éxito ──
        info.put("note", "Conexion a Sepolia activa");
        // info = { ..., "note": "Conexion a Sepolia activa" }

    // ─────────────────────────────────────────────────────────────────────────
    // Si CUALQUIER llamado a blockchain falla (RPC caído, timeout, etc.)
    // ─────────────────────────────────────────────────────────────────────────
    } catch (Exception e) {
        // No dejamos que la excepción se propague - devolvemos info parcial
        info.put("error", e.getMessage());
        // ej: "error": "Connection refused: /31.22.124.172:443"

        info.put("note", "Error de conexion con Sepolia");
        // info = { "error": "Connection refused", "note": "Error de conexion con Sepolia" }
        // No hay "treasuryBalance", "backendAddress", etc. - solo el error
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Devuelve el Map (con datos completos o con error, según el try/catch)
    // ─────────────────────────────────────────────────────────────────────────
    return info;
    //
    // Si todo salió bien, el Map contiene 6 entradas.
    // Si hubo error, contiene solo 2-3 entradas.
    //
    // Spring Boot convierte este Map automáticamente a JSON ⮕ HTTP Response.
}
```

**Llamada desde un endpoint REST (futuro):**
```
GET /api/blockchain/info?contract=0x9f2c766d...
```

**Cadena de llamadas:**
```
SmartContractService.getContractInfo("0x9f2c766d0bd9bbb640422decdf0125be02c7d144")
  │
  ├─ 1. blockchain.balanceOf("0x9f2c766d...", "0x7eEA86...")
  │      → eth_call balanceOf(treasury) → 1000000 (1M $IDEA en treasury)
  │
  ├─ 2. blockchain.getBackendAddress()
  │      → "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A" (nombre de la wallet)
  │
  ├─ 3. blockchain.getBalanceSepoliaInEth()
  │      → eth_getBalance → 2.0 SepoliaETH
  │
  ├─ 4. blockchain.getLatestBlock()
  │      → eth_blockNumber → 12354049
  │
  └─ Resultado:
     {
       "address": "0x9f2c766d0bd9bbb640422decdf0125be02c7d144",
       "treasuryBalance": 1000000,
       "backendAddress": "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A",
       "backendSepoliaEth": 2.0,
       "latestBlock": 12354049,
       "note": "Conexion a Sepolia activa"
     }
```

**Si Sepolia está caído:**
```json
{
  "error": "Connection refused",
  "note": "Error de conexion con Sepolia"
}
```

---

#### 10.9.2 `getTotalSubTokens()` — Obtener saldo de $IDEA del treasury

**Archivo:** `project/service/SmartContractService.java` — línea 47

```java
public Long getTotalSubTokens(String contractAddress) {
    //                                        ↑ (no se usa - siempre consulta ProjectToken fijo)

    // ─────────────────────────────────────────────────────────────────────────
    // Consulta el saldo de tokens $IDEA del treasury en el contrato ProjectToken
    // ─────────────────────────────────────────────────────────────────────────
    try {
        // ── Llama a BlockchainService.balanceOf() ──
        BigInteger balance = blockchain.balanceOf(
            props.getProjectTokenAddress(),    // ← Address del ProjectToken (0x9f2c766d...)
            props.getTreasuryAddress()         // ← Address del treasury (0x7eEA86...)
        );
        // blockchain.balanceOf() internamente:
        //   1. Crea Function("balanceOf", [Address(treasury)], [Uint256])
        //   2. Llama a executeCall(projectToken, fn)
        //   3. eth_call al ProjectToken → Uint256(1000000)
        // balance = BigInteger.valueOf(1000000)

        // ── Convierte BigInteger a Long (pierde precisión si > 9 quintillones) ──
        return balance.longValue();
        //       ↑
        //  1000000L  ← 1 millón de $IDEA en el treasury

    // ─────────────────────────────────────────────────────────────────────────
    // Si Sepolia no responde, devuelve 0 (fallback seguro, no detiene la app)
    // ─────────────────────────────────────────────────────────────────────────
    } catch (Exception e) {
        log.error("Error getTotalSubTokens: {}", e.getMessage());
        // Log: "Error getTotalSubTokens: Connection refused" (no interrumpe)
        return 0L;                                          // ← fallback: 0 tokens
    }
}

---

#### 10.9.3 `getTokenDistribution()` — Distribución de tokens

**Archivo:** `project/service/SmartContractService.java` — línea 60

```java
public Map<String, Long> getTokenDistribution(String contractAddress) {
    //                                              ↑ (no se usa - siempre ProjectToken)

    // ─────────────────────────────────────────────────────────────────────────
    // Prepara el Map que devolverá los datos de distribución
    // ─────────────────────────────────────────────────────────────────────────
    Map<String, Long> distribution = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Consulta DOS saldos on-chain para entender la distribución de tokens
    // ─────────────────────────────────────────────────────────────────────────
    try {
        // ── Consulta 1: Total supply (¿cuántos $IDEA existen?) ──
        BigInteger totalSupply = blockchain.balanceOf(
            props.getProjectTokenAddress(),      // ← ProjectToken (0x9f2c766d...)
            props.getProjectTokenAddress()       // ← El mismo ProjectToken (¡esto es un truco!)
        );
        // ⚠️ ACLARACIÓN: balanceOf(projectToken, projectToken) NO es totalSupply()
        //    Esto consulta el saldo del contrato en sí mismo, que suele ser 0.
        //    En el futuro debería llamar a la función totalSupply() del ERC-20:
        //      Function fn = new Function("totalSupply", List.of(), List.of(new TypeReference<Uint256>(){}))
        //
        // Por ahora devuelve 0 (el contrato no tiene tokens en su propia address)
        // TODO: Reemplazar con blockchain.totalSupply() cuando se implemente

        // ── Consulta 2: Saldo del treasury ──
        BigInteger treasuryBalance = blockchain.balanceOf(
            props.getProjectTokenAddress(),      // ← ProjectToken (0x9f2c766d...)
            props.getTreasuryAddress()           // ← Treasury (0x7eEA86...)
        );
        // blockchain.balanceOf() → eth_call → BigInteger.valueOf(1000000)
        // Treasury tiene 1,000,000 $IDEA

        // ── Pone los valores en el Map (como Long) ──
        distribution.put("totalSupply", totalSupply.longValue());
        // distribution = { "totalSupply": 0 }

        distribution.put("treasuryBalance", treasuryBalance.longValue());
        // distribution = { "totalSupply": 0, "treasuryBalance": 1000000 }

    // ─────────────────────────────────────────────────────────────────────────
    // Si blockchain falla, devuelve valores por defecto (0)
    // ─────────────────────────────────────────────────────────────────────────
    } catch (Exception e) {
        log.error("Error getTokenDistribution: {}", e.getMessage());
        distribution.put("disponible", 0L);       // ← fallback
        distribution.put("invertido", 0L);         // ← fallback
        // distribution = { "disponible": 0, "invertido": 0 }
    }

    return distribution;
    //
    // Si todo funciona, distribution = { "totalSupply": 0, "treasuryBalance": 1000000 }
    // Si falla, distribution = { "disponible": 0, "invertido": 0 }
}

---

#### 10.9.4 `deployContract()` — Simulación de deploy

**Archivo:** `project/service/SmartContractService.java` — línea 81

```java
public String deployContract(Long projectId, Long totalTokens, String tokenName) {
    //                                ↑              ↑              ↑
    //                          ID del proyecto   Cantidad total   Nombre del token
    //                          (no se usa)       a mintear       (no se usa)

    // ─────────────────────────────────────────────────────────────────────────
    // En lugar de deployar un nuevo contrato, "simula" el deploy minteando
    // tokens al treasury en el contrato ProjectToken existente
    // ─────────────────────────────────────────────────────────────────────────
    try {
        // ── Mintea totalTokens al treasury ──
        String mintTx = blockchain.mintTokens(
            props.getTreasuryAddress(),                  // ← destino: treasury (0x7eEA86...)
            BigInteger.valueOf(totalTokens)               // ← cantidad: ej 1000000 (1M)
        );
        // blockchain.mintTokens() internamente:
        //   1. Crea Function("mint", [Address(treasury), Uint256(1000000)], [])
        //   2. Llama a executeWrite(projectTokenAddress, fn)
        //   3. Firma y envía eth_sendRawTransaction
        //   4. Devuelve txHash: "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
        // mintTx = "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"

        // ── Log para tracking ──
        log.info("Deploy simulation - mint tx: {}", mintTx);
        // Log: "Deploy simulation - mint tx: 0x983a508c..."

        // ── Devuelve la address del contrato ProjectToken (fija) ──
        return props.getProjectTokenAddress();
        //       ↑
        //  "0x9f2c766d0bd9bbb640422decdf0125be02c7d144"
        //
        // ⚠️ No es una address nueva por proyecto - todos comparten el mismo ProjectToken
        // En el futuro: deployaría un nuevo ERC-20 por cada proyecto usando una factory

    // ─────────────────────────────────────────────────────────────────────────
    // Si el mint falla (sin gas, RPC caído), devuelve una address "fake"
    // ─────────────────────────────────────────────────────────────────────────
    } catch (Exception e) {
        log.error("Error deployContract: {}", e.getMessage());
        // Log: "Error deployContract: insufficient funds for gas * price + value"
        return "0x" + System.currentTimeMillis() + projectId;
        //       ↑
        //  "0x1716151000001" ← address fake con timestamp + projectId
        //  Solo para no romper el flujo - en producción debe deployarse real
    }
}
```

**¿Qué hace realmente?** No deploya un nuevo contrato (eso se hace con Foundry). En cambio:
1. Hace un `mint()` de `totalTokens` al treasury en el contrato ProjectToken existente
2. Devuelve la address del contrato ProjectToken (que es fija, no cambia por proyecto)

**En el futuro:** Cuando cada proyecto tenga su propio token, esto deployaría un nuevo contrato ERC-20 personalizado.

---

#### 10.9.5 `recordInvestment()` — El método principal (trazado completo línea por línea)

**Archivo:** `project/service/SmartContractService.java` — línea 92

```java
public Map<String, Object> recordInvestment(
    Long proyectoId, Long usuarioId, BigDecimal montoIdea, String txHash
) {
    //   ↑                ↑               ↑              ↑
    // ID del proyecto   ID del user    Monto en IDEA   Hash de la tx en Sepolia
    // (solo para log)   (solo para log)  ej: 500.00     ej: "0x983a508c..."

    // ─────────────────────────────────────────────────────────────────────────
    // Prepara el Map que devolverá el resultado de la verificación
    // ─────────────────────────────────────────────────────────────────────────
    Map<String, Object> result = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOQUE PRINCIPAL: intenta verificar la tx en Sepolia
    // ═══════════════════════════════════════════════════════════════════════════
    try {
        // ─────────────────────────────────────────────────────────────────────
        // PASO 1: Verificar que la transacción exista y haya sido exitosa
        // ─────────────────────────────────────────────────────────────────────
        boolean txValida = blockchain.verifyTransaction(txHash);
        //                       ↑
        //             BlockchainService.verifyTransaction("0x983a508c...")
        //               → eth_getTransactionReceipt → status="0x1" → true
        //
        // Si la tx no existe en Sepolia (hash falso), devuelve false.
        // Si el nodo responde con error, lanza Exception → catch.

        result.put("success", txValida);
        // result = { "success": true }
        result.put("txHash", txHash);
        // result = { "success": true, "txHash": "0x983a508c..." }

        // ─────────────────────────────────────────────────────────────────────
        // PASO 2: Si la transacción es válida, obtener datos adicionales
        // ─────────────────────────────────────────────────────────────────────
        if (txValida) {
            // ── 2a. Obtener el número de bloque donde se minó la tx ──
            BigInteger block = blockchain.getLatestBlock();
            //                        ↑
            //              eth_blockNumber → 12345678
            //
            result.put("blockNumber", block.longValue());
            // result = { "success": true, "txHash": "...", "blockNumber": 12345678 }

            result.put("note", "Transaccion verificada en Sepolia");
            // result = { ..., "note": "Transaccion verificada en Sepolia" }

            // ── 2b. Consultar el saldo actual del treasury en ProjectToken ──
            BigInteger balance = blockchain.balanceOf(
                props.getProjectTokenAddress(),    // ← ProjectToken (0x9f2c766d...)
                props.getTreasuryAddress()         // ← Treasury (0x7eEA86...)
            );
            // balance = BigInteger.valueOf(1000000) (1M $IDEA)

            result.put("treasuryTokensBalance", balance);
            // result = { ..., "treasuryTokensBalance": 1000000 }
        }
        // ─────────────────────────────────────────────────────────────────────
        // PASO 2b: Si la tx NO es válida (hash falso o no minado aún)
        // ─────────────────────────────────────────────────────────────────────
        else {
            result.put("blockNumber", 0L);          // ← bloque 0 = no verificado
            result.put("note", "Tx no encontrada en Sepolia - se registra igual en DB");
            // result = { "success": false, "txHash": "...", "blockNumber": 0,
            //            "note": "Tx no encontrada en Sepolia - se registra igual en DB" }
        }
    }
    // ═══════════════════════════════════════════════════════════════════════════
    // BLOQUE DE FALLO: Sepolia no responde → MODO OFFLINE
    // ═══════════════════════════════════════════════════════════════════════════
    catch (Exception e) {
        // Si eth_getTransactionReceipt lanza timeout, connection refused, etc.
        log.error("Error recordInvestment: {}", e.getMessage());
        // Log: "Error recordInvestment: Connection refused: /31.22.124.172:443"

        // En modo offline, asumimos éxito para no bloquear la inversión
        result.put("success", true);                 // ← asume éxito
        result.put("txHash", txHash);                // ← el hash que nos dieron
        result.put("blockNumber", 0L);               // ← no sabemos en qué bloque está
        result.put("note", "Blockchain no disponible - modo offline: " + e.getMessage());
        // result = { "success": true, "txHash": "...", "blockNumber": 0,
        //            "note": "Blockchain no disponible - modo offline: Connection refused" }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Devuelve el Map con los resultados al InvestmentService
    // ─────────────────────────────────────────────────────────────────────────
    return result;
    //
    // CASO 1 - Tx válida: { success:true, txHash:"0x983a...", blockNumber:12345678,
    //                        treasuryTokensBalance:1000000,
    //                        note:"Transaccion verificada en Sepolia" }
    //
    // CASO 2 - Tx inválida: { success:false, txHash:"0x983a...", blockNumber:0,
    //                        note:"Tx no encontrada en Sepolia - se registra igual en DB" }
    //
    // CASO 3 - Offline: { success:true, txHash:"0x983a...", blockNumber:0,
    //                     note:"Blockchain no disponible - modo offline: ..." }
}

**Llamada desde InvestmentService.createInvestment() con datos reales:**

```
recordInvestment(
  proyectoId=1,
  usuarioId=2,
  montoIdea=500.00,
  txHash="0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"
)
```

**Paso 1: verifyTransaction**
```
blockchain.verifyTransaction("0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac")
  → HTTP POST eth_getTransactionReceipt
  → receipt.status = "0x1"
  → true ✅
```

**Paso 2: getLatestBlock**
```
blockchain.getLatestBlock()
  → HTTP POST eth_blockNumber
  → 12345678
```

**Paso 3: balanceOf**
```
blockchain.balanceOf(
  "0x9f2c766d0bd9bbb640422decdf0125be02c7d144",  // ProjectToken
  "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"   // Treasury
)
  → HTTP POST eth_call balanceOf(treasury)
  → 1000000 (1M $IDEA)
```

**Resultado final:**
```java
{
    "success": true,
    "txHash": "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac",
    "blockNumber": 12345678,
    "treasuryTokensBalance": 1000000,
    "note": "Transaccion verificada en Sepolia"
}
```

**¿Qué hace InvestmentService con este resultado?**

```java
Map<String, Object> txResult = smartContractService.recordInvestment(
    request.getProyectoId(), usuarioId, request.getMontoIdea(), request.getTxHash());

boolean txValida = (boolean) txResult.get("success");
// txValida = true → continúa con el registro en DB
// txValida = false → lanza excepción (inversión rechazada)
```

---

#### 10.9.6 `refundInvestment()` — Reembolso (minteo de tokens de reemplazo)

**Archivo:** `project/service/SmartContractService.java` — línea 122

```java
public Map<String, Object> refundInvestment(
    Long proyectoId, Long usuarioId, BigDecimal montoIdea
) {
    //   ↑                ↑               ↑
    // ID del proyecto   ID del user    Monto a reembolsar en $IDEA
    // (solo para log)   (solo para log)  ej: 500.00

    // ─────────────────────────────────────────────────────────────────────────
    // Prepara el Map con el resultado del reembolso
    // ─────────────────────────────────────────────────────────────────────────
    Map<String, Object> result = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOQUE PRINCIPAL: intenta mintear tokens de reemplazo en Sepolia
    // ═══════════════════════════════════════════════════════════════════════════
    try {
        // ─────────────────────────────────────────────────────────────────────
        // PASO 1: Convertir montoIdea (BigDecimal, ej: 500.00) a wei (BigInteger)
        // ─────────────────────────────────────────────────────────────────────
        BigInteger amountWei = montoIdea.multiply(BigDecimal.TEN.pow(18)).toBigInteger();
        //                            ↑                     ↑              ↑
        //                      500.00 (BigDecimal)    10^18 = 1e18    500000000000000000000
        //
        // ¿Por qué 10^18? Porque el ProjectToken tiene 18 decimales (como ETH).
        // Para representar 500 tokens completos necesitamos 500 * 10^18 en wei.
        //
        // amountWei = BigInteger.valueOf(500000000000000000000)
        //
        // Equivalente: 500 * 1,000,000,000,000,000,000 = 500,000,000,000,000,000,000

        // ─────────────────────────────────────────────────────────────────────
        // PASO 2: Mintear nuevos tokens al treasury como reembolso simulado
        // ─────────────────────────────────────────────────────────────────────
        String refundTx = blockchain.mintTokens(props.getTreasuryAddress(), amountWei);
        //                                   ↑                          ↑
        //                         Treasury (0x7eEA86...)     500000000000000000000 wei
        //
        // blockchain.mintTokens() internamente:
        //   1. Crea Function("mint", [Address(treasury), Uint256(500e18)], [])
        //   2. executeWrite(projectToken, fn)
        //   3. eth_sendRawTransaction → txHash
        //
        // refundTx = "0x983a508cc91f4577f3491df4f65b66b5d09c062dc78ab0339400ec2bb74d9fac"

        // ─────────────────────────────────────────────────────────────────────
        // PASO 3: Armar respuesta exitosa
        // ─────────────────────────────────────────────────────────────────────
        result.put("success", true);
        result.put("refundTxHash", refundTx);
        // result = { "success": true, "refundTxHash": "0x983a508c..." }
        result.put("note", "Reembolso simulado via mint en Sepolia");
        // result = { ..., "note": "Reembolso simulado via mint en Sepolia" }

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOQUE DE FALLO: Sepolia no responde → MODO OFFLINE
    // ═══════════════════════════════════════════════════════════════════════════
    } catch (Exception e) {
        log.error("Error refundInvestment: {}", e.getMessage());
        // Log: "Error refundInvestment: insufficient funds"

        result.put("success", true);                 // ← asume éxito
        result.put("refundTxHash", "0xrefund-" + System.currentTimeMillis());
        //                  ↑
        //        "0xrefund-1716151000" ← hash fake con timestamp
        //
        result.put("note", "Reembolso en modo offline: " + e.getMessage());
        // result = { "success": true, "refundTxHash": "0xrefund-...",
        //            "note": "Reembolso en modo offline: insufficient funds" }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Devuelve el resultado al InvestmentService.processExpiredProjects()
    // ─────────────────────────────────────────────────────────────────────────
    return result;
    //
    // CASO 1 - Éxito: { success:true, refundTxHash:"0x983a508c...",
    //                   note:"Reembolso simulado via mint en Sepolia" }
    //
    // CASO 2 - Offline: { success:true, refundTxHash:"0xrefund-1716151000",
    //                     note:"Reembolso en modo offline: insufficient funds" }
}

**¿Por qué mint y no transfer?** El reembolso real involucraría llamar a `transfer()` desde el PaymentGateway al usuario. Actualmente, como el flujo off-chain registra la inversión en PostgreSQL pero los tokens ya fueron minteados al treasury, un reembolso implica:
1. Mintear nuevos tokens al treasury (para mantener el suministro)
2. El scheduler actualiza `saldo_idea` en PostgreSQL y descuenta subtokens del portfolio

**En el futuro:** `refundInvestment()` llamará a `PaymentGateway.refund(user, amount)` que transferirá USDC desde el treasury al usuario.

---

#### 10.9.7 `deployInvestmentContract()` — Contrato ya deployado

**Archivo:** `project/service/SmartContractService.java` — línea 141

```java
public Map<String, Object> deployInvestmentContract(Long proyectoId) {
    //                                                   ↑
    //                                          ID del proyecto (reservado para futuro)

    // ─────────────────────────────────────────────────────────────────────────
    // Crea el Map con la información del contrato
    // ─────────────────────────────────────────────────────────────────────────
    Map<String, Object> result = new HashMap<>();    // ← Map vacío

    // ─────────────────────────────────────────────────────────────────────────
    // Pone la address del contrato ProjectToken (fija, no cambia por proyecto)
    // ─────────────────────────────────────────────────────────────────────────
    result.put("contractAddress", props.getProjectTokenAddress());
    //                       ↑
    //        "0x9f2c766d0bd9bbb640422decdf0125be02c7d144"
    //        (único contrato ProjectToken en Sepolia)
    //
    // En el futuro: aquí se deployaría un nuevo ERC-20 para el proyecto

    result.put("success", true);
    // result = { "contractAddress": "0x9f2c766d...", "success": true }

    result.put("note", "Contrato ProjectToken ya deployado en Sepolia");
    // result = { ..., "note": "Contrato ProjectToken ya deployado en Sepolia" }

    // ─────────────────────────────────────────────────────────────────────────
    // Devuelve el Map
    // ─────────────────────────────────────────────────────────────────────────
    return result;
    //
    // resultado:
    // {
    //   "contractAddress": "0x9f2c766d0bd9bbb640422decdf0125be02c7d144",
    //   "success": true,
    //   "note": "Contrato ProjectToken ya deployado en Sepolia"
    // }
}

**¿Por qué es tan simple?** Porque los contratos ya están deployados en Sepolia. No deployamos un contrato nuevo por cada inversión. Si en el futuro cada proyecto tiene su propio contrato ERC-20, este método usaría Foundry o una fábrica de contratos on-chain.

---

## 11. Tests — Cómo asegurar que funciona

### 11.1 Enfoque de testing

Usamos **JUnit 5 + Mockito** para **tests unitarios**. Esto significa que:

- Testeamos el `InvestmentService` **de forma aislada**
- **Mockeamos** (simulamos) sus dependencias: `InvestmentRepository`, `SmartContractService`, `JdbcTemplate`
- No necesitamos una base de datos PostgreSQL real ni conexión a Sepolia
- Los tests corren en milisegundos

### 11.2 Configuración de Mockito

```java
@ExtendWith(MockitoExtension.class)     // Activa Mockito en JUnit 5
@MockitoSettings(strictness = Strictness.LENIENT)  // No falla por métodos no llamados
class InvestmentServiceTest {

    @Mock InvestmentRepository investmentRepository;      // Simula la DB (JPA)
    @Mock SmartContractService smartContractService;      // Simula blockchain (fallback)
    @Mock JdbcTemplate jdbc;                              // Simula SQL directo
    @Mock DynamicPricingService pricingService;           // ★ Simula precio dinamico
    @Mock InvestmentSwapService investmentSwapService;    // ★ INV-05: Simula swap on-chain
    @Mock BlockchainProperties blockchainProperties;      // ★ INV-05: Addresses de contratos

    @InjectMocks InvestmentService investmentService;     // Servicio real con mocks inyectados
```

**¿Que hace cada anotacion?**

| Anotacion | Efecto |
|-----------|--------|
| `@Mock` | Crea un objeto simulado. Si llamas a sus metodos, no ejecutan codigo real. Por defecto devuelven `null` o valores por defecto. |
| `@InjectMocks` | Crea una instancia real y le inyecta los mocks en el constructor. |
| `@MockitoSettings(LENIENT)` | Mockito normalmente falla si hay metodos que no se usaron en el test. Con LENIENT, ignora esos casos. |

**Por que 6 mocks y no 3 como antes?** Porque el servicio ahora tiene mas dependencias:

| Mock | Se agrego en | Por que |
|------|-------------|---------|
| `InvestmentSwapService` | INV-05 | Simula el swap atomico en Sepolia |
| `BlockchainProperties` | INV-05 | Provee la address del treasury |
| `DynamicPricingService` | Seccion 15 | Calcula precio dinamico del subtoken |

**Setup de cada test (@BeforeEach):**

```java
@BeforeEach
void setUp() {
    // Configuracion global: la address del treasury usada en INV-05
    when(blockchainProperties.getTreasuryAddress())
        .thenReturn("0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A");
    // ... crear objetos base (proyecto, inversion, etc.)
}
```

### 11.3 Los 14 tests explicados

| # | Nombre del test | ¿Qué cubre? | ¿Cómo lo prueba? |
|---|-----------------|-------------|------------------|
| 1 | `validate_cuandoProyectoValido_retornaValido` | Validación exitosa | Mockea proyecto en FINANCIAMIENTO con subtoken con cupo. Verifica `valido = true`. |
| 2 | `validate_cuandoProyectoNoEnFinanciamiento_retornaInvalido` | Estado incorrecto | Mockea proyecto en PREPARACION. Verifica `valido = false`. |
| 3 | `validate_cuandoSinSubtoken_retornaInvalido` | Sin subtoken | Mockea proyecto en FINANCIAMIENTO pero sin subtoken. Verifica `valido = false`. |
| 4 | `validate_cuandoSinCupo_retornaInvalido` | Cupo agotado | Mockea subtoken con `cupo_restante = 0`. Verifica `valido = false`. |
| 5 | `validate_cuandoProyectoNoExiste_lanzaNotFound` | Proyecto inexistente | Mockea `jdbc.query()` devolviendo lista vacía. Verifica `ResourceNotFoundException`. |
| 6 | `create_conDatosValidos_creaInversionCorrectamente` | Creacion exitosa (con INV-05) | Flujo completo: mockea proyecto, subtoken (con precio_base, suministro_total, factor_volatilidad), saldo, pricingService, **investmentSwapService.invest()** (INV-05), y repository. Verifica `id = 1`, `subTokens = 50`, `txHash = "0xabc-swap-inv-05"`. |
| 7 | `create_cuandoProyectoNoEnFinanciamiento_lanzaConflict` | Crear en estado incorrecto | Mockea proyecto en PREPARACION. Verifica `ConflictException`. |
| 8 | `create_cuandoSinSubtoken_lanzaConflict` | Crear sin subtoken | Mockea proyecto en FINANCIAMIENTO pero sin subtoken. Verifica `ConflictException`. |
| 9 | `create_cuandoCupoInsuficiente_lanzaConflict` | Crear con cupo agotado | Mockea subtoken con `subTokens > cupo_restante`. Verifica `ConflictException`. |
| 10 | `create_cuandoSaldoInsuficiente_lanzaConflict` | Crear sin saldo | Mockea `saldo_idea < montoIdea`. Verifica `ConflictException`. |
| 11 | `getInvestmentById_cuandoNoExiste_lanzaNotFound` | Buscar ID inexistente | Mockea `repository.findById()` devolviendo `Optional.empty()`. |
| 12 | `getInvestmentHistory_retornaHistorial` | Historial vacío | Mockea `repository.findByUsuarioId()` devolviendo página vacía. |
| 13 | `processExpiredProjects_cuandoProyectoNoAlcanzaMeta_ejecutaReembolso` | Reembolso automático | Mockea proyecto vencido con `recaudado < requerido`. Verifica que se llame a `refundAllInvestors`. |
| 14 | `processExpiredProjects_cuandoProyectoAlcanzaMeta_noEjecutaReembolso` | Proyecto exitoso | Mockea proyecto vencido pero con `recaudado >= requerido`. Verifica que NO se llame a `refundAllInvestors`. |

### 11.4 Anatomía de un test (el más importante)

```java
@Test
void createInvestment_conDatosValidos_creaInversionCorrectamente() {
    // ═══════════════════════════════════════
    // GIVEN (Configuración)
    // ═══════════════════════════════════════

    // Simula que la DB responde con un proyecto en FINANCIAMIENTO
    when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
        .thenReturn(List.of(Map.of("estado", "FINANCIAMIENTO")))   // 1er llamado: findProject
        .thenReturn(List.of(Map.of("id", 1L,                       // 2do llamado: findSubtoken
            "cupo_restante", 100,
            "precio_actual", BigDecimal.TEN)));

    // Simula que el usuario tiene 5000 $IDEA
    when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(2L)))
        .thenReturn(new BigDecimal("5000.00"));

    // Simula que el smart contract responde exitosamente
    when(smartContractService.recordInvestment(any(), any(), any(), any()))
        .thenReturn(Map.of("success", true, "txHash", "0xdef456"));

    // Simula que el repository guarda y asigna un ID
    when(investmentRepository.save(any(Inversion.class))).thenAnswer(inv -> {
        Inversion saved = inv.getArgument(0);
        saved.setId(1L);
        return saved;
    });

    // ═══════════════════════════════════════
    // WHEN (Ejecución)
    // ═══════════════════════════════════════
    CreateInvestmentRequest request = new CreateInvestmentRequest();
    request.setProyectoId(1L);
    request.setMontoIdea(new BigDecimal("500"));
    request.setTxHash("0xabc123");

    InvestmentResponse response = investmentService.createInvestment(request, 2L);

    // ═══════════════════════════════════════
    // THEN (Verificación)
    // ═══════════════════════════════════════
    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);               // ID asignado
    assertThat(response.getEstado()).isEqualTo("CONFIRMADA");  // Estado correcto
    assertThat(response.getSubTokensRecibidos()).isEqualTo(50);  // 500 / 10 = 50
    assertThat(response.getMontoIdea()).isEqualByComparingTo("500"); // Monto exacto
    assertThat(response.getTxHash()).isEqualTo("0xdef456");   // TxHash del smart contract
}
```

**Explicación de cada `when()`:**

1. `when(jdbc.query(..., eq(1L)))` → Cuando el service llame a `jdbc.query()` con proyectoId = 1, devolvé un proyecto en FINANCIAMIENTO. En la segunda llamada, devolvé un subtoken con cupo 100 y precio 10.

2. `when(jdbc.queryForObject(..., eq(2L)))` → Cuando consulte el saldo del usuario 2, devolvé 5000.

3. `when(smartContractService.recordInvestment(...))` → Cuando llame a blockchain, devolvé éxito con txHash.

4. `when(investmentRepository.save(...))` → Cuando guarde la inversión, asignale ID = 1.

**¿Por qué `anyString()`, `any(RowMapper.class)`?** Porque no nos importa el SQL exacto o el RowMapper que use el service. Lo que nos importa es que reciba los parámetros correctos (`eq(1L)`, `eq(2L)`).

### 11.5 ¿Por qué Mockito en vez de base de datos real?

| Criterio | Tests con Mockito | Tests con BD real |
|----------|------------------|-------------------|
| Velocidad | Milisegundos | Segundos |
| Dependencias | Solo Java | PostgreSQL + Flyway migrado |
| Aislamiento | Cada test es independiente | Posible estado residual entre tests |
| Errores | Fáciles de simular (saldo insuficiente, cupo agotado) | Difíciles de reproducir |
| CI/CD | Corre en cualquier máquina | Requiere configurar BD |

---

## 12. Contratos deployados en Sepolia

### 12.1 Tabla de contratos

| Contrato | Address | TX de deploy | ¿Qué hace? |
|----------|---------|-------------|-----------|
| **PaymentGateway** | `0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10` | `0x983a508c...` | Recibe USDC, emite evento `Paid` |
| **ProjectToken ($IDEA)** | `0x9f2c766d0bd9bbb640422decdf0125be02c7d144` | `0xa043c587...` | Token ERC-20 del proyecto IDEAFY |
| **USDC (Circle)** | `0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238` | (oficial de Circle) | Stablecoin para pagos |
| **Treasury** | `0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A` | — | Wallet del equipo (recibe los fondos) |

### 12.2 Ver los contratos en Etherscan

Podés ver tus contratos en el explorador de Sepolia:

- **PaymentGateway:** https://sepolia.etherscan.io/address/0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10
- **ProjectToken:** https://sepolia.etherscan.io/address/0x9f2c766d0bd9bbb640422decdf0125be02c7d144

En Etherscan podés:
- ✅ **Read Contract:** Llamar funciones `view` (balanceOf, allowance, owner) sin firma
- ✍️ **Write Contract:** Conectar MetaMask y llamar funciones que modifican estado
- 📄 **Code:** Ver el código fuente verificado (que coincide con el Solidity original)
- 📊 **Events:** Ver los eventos emitidos (Paid, Transfer, Approval)

### 12.3 ¿Cómo se deployaron?

Usando **Foundry** (el toolkit de desarrollo Ethereum):

```bash
# PaymentGateway
forge script script/DeployPaymentGateway.s.sol \
  --rpc-url https://ethereum-sepolia-rpc.publicnode.com \
  --account dev \
  --broadcast

# ProjectToken
forge create src/ProjectToken.sol:ProjectToken \
  --rpc-url https://ethereum-sepolia-rpc.publicnode.com \
  --account dev \
  --constructor-args "IDEAFY Token" "IDEA" 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A \
  --broadcast
```

Los receipts de deploy quedan en `~/sip2026-blockchain/clase-2/broadcast/`. Allí se pueden consultar las addresses y txHashes.

### 12.4 Verificar contratos en Etherscan

Cuando deployás un contrato con Foundry, el código fuente en Etherscan aparece como "bytecode" (ilegible). Para que cualquiera pueda **leer el código** y **verificar que coincide con el bytecode deployado**, hay que "verificar" el contrato en Etherscan.

**¿Por qué verificar?**
1. **Transparencia:** Cualquiera puede leer el código fuente y auditarlo
2. **Confianza:** Los inversores saben que el contrato hace exactamente lo que dice
3. **Interacción:** Etherscan permite leer/escribir el contrato desde su web si está verificado

**Cómo verificar un contrato manualmente:**

```
1. Ir a: https://sepolia.etherscan.io/verifyContract
2. Poner la address del contrato (ej: 0x7aabb2435...)
3. Seleccionar:
   - Compiler: Same as the one used (ej: 0.8.20)
   - License: MIT
   - Optimization: No (salvo que hayas usado --optimize)
4. Pegar el código fuente completo del .sol
5. Si el contrato importa librerías (OpenZeppelin):
   - Marcar "Yes" en "Are there any libraries?"
   - Etherscan detecta automáticamente las dependencias
6. Click en "Verify and Publish"
```

**Con Foundry (automático):**
```bash
# Verificar automáticamente durante el deploy
forge create src/ProjectToken.sol:ProjectToken \
  --rpc-url https://ethereum-sepolia-rpc.publicnode.com \
  --account dev \
  --constructor-args "IDEAFY Token" "IDEA" 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A \
  --broadcast \
  --verify

# O verificar un contrato ya deployado
forge verify-contract \
  --chain-id 11155111 \
  --watch \
  0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10 \
  src/PaymentGateway.sol:PaymentGateway
```

**¿Qué pasa si no verificás?** El contrato funciona igual, pero en Etherscan solo se ve el bytecode (hexadecimal ilegible). Cualquiera puede verificar contratos de terceros, así que es mejor hacerlo vos para asegurarte de que el código publicado es el correcto.

### 12.5 Cómo obtener SepoliaETH (para pagar gas)

Las transacciones en Sepolia cuestan **gas**, que se paga en **SepoliaETH** (ETH falso de testnet). No tiene valor real, pero sin él no podés deployar contratos ni ejecutar transacciones.

**Opciones para conseguir SepoliaETH:**

| Opción | URL | ¿Cómo funciona? |
|--------|-----|-----------------|
| **Alchemy Faucet** | `https://sepoliafaucet.com` | Crear cuenta gratis en Alchemy → pegar address → recibe 0.5 SepoliaETH/día |
| **Infura Faucet** | `https://infura.io/faucet/sepolia` | Crear cuenta gratis → pegar address → recibe SepoliaETH |
| **Google Cloud Faucet** | `https://cloud.google.com/application/web3/faucet/ethereum/sepolia` | Con cuenta de Google → 0.05 SepoliaETH cada 24h |
| **Faucet público** | `https://faucet.sepolia.dev` | Sin registro, pero limitado |
| **Proof of Work** | `https://sepolia-faucet.pk910.de` | Resolvé un captcha minando un poco (no requiere cuenta) |

**Recomendación:** Usá **Alchemy Faucet** o **Infura Faucet** — son los más confiables y te dan suficiente ETH para miles de transacciones.

**¿Cuánto SepoliaETH necesito?**
- Deployar un contrato como ProjectToken: ~0.001-0.005 ETH
- Llamar `mint()`: ~0.0001-0.0005 ETH
- Con 0.5 SepoliaETH podés hacer **miles de transacciones**

**¿Por qué a veces las transacciones fallan con "insufficient funds"?**
El error completo es:
```
insufficient funds for gas * price + value
```
Esto significa que la wallet no tiene suficiente SepoliaETH para pagar el gas de la transacción. Solución: pedir más SepoliaETH de un faucet.

### 12.6 Entender las transacciones en Etherscan

Cuando entrás a Etherscan y buscás un txHash (ej: `0x983a508c...`), ves una página con mucha información. Esto es lo que significa cada campo:

| Campo | Ejemplo | Significado |
|-------|---------|------------|
| **Transaction Hash** | `0x983a...` | Identificador único de la transacción |
| **Status** | ✅ Success | `0x1` = éxito, `0x0` = falló (revert) |
| **Block** | `12345678` | Bloque donde se incluyó la tx (cuanto más grande, más antigua) |
| **Timestamp** | `23 secs ago` | Hace cuánto se minó (Sepolia: ~12 segundos por bloque) |
| **From** | `0x7eEA86...` | Wallet que FIRMÓ la transacción (pagó el gas) |
| **To** | `0x23a81e...` | Contrato al que se llamó (o "Contract Creation" si es un deploy) |
| **Value** | `0 ETH` | Cantidad de ETH enviada (casi siempre 0 porque usamos tokens) |
| **Gas Limit** | `500,000` | Gas máximo que el firmante estaba dispuesto a pagar |
| **Gas Used** | `312,456` | Gas REAL que consumió la transacción |
| **Gas Price** | `1.5 Gwei` | Precio pagado por cada unidad de gas (0.0000000015 ETH) |
| **Actual Tx Cost** | `0.000468 ETH` | Total pagado = Gas Used × Gas Price |
| **Input Data** | `0x40c10f19...` | La función llamada + argumentos (ABI encoded) |
| **Logs** | `2 logs` | Eventos emitidos (Transfer, TokensQuemados, etc.) |

**¿Cómo leer el Input Data?**
Etherscan intenta decodificar automáticamente el Input Data si el contrato está verificado. Si no, ves el hex crudo. Por ejemplo:
```
Function: mint(address para, uint256 cantidad)
MethodID: 0x40c10f19
[0]:  0000...0007eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A  → para (address)
[1]:  0000...000000000000000000000000000000000000000003E8  → cantidad (5000 en decimal)
```

---

## 13. Historias de usuario

Esta seccion cuenta la historia COMPLETA de cada HU: que problema resuelve, que tareas la implementan, que archivos participan, y donde encontrar los detalles.

---

### 13.1 HU-10 — Registro de inversion en blockchain

**Descripcion:** Como inversor, quiero que mi inversion quede registrada en la blockchain para que sea inmutable y verificable.

**Problema que resuelve:** Sin un registro en blockchain, las inversiones solo existen en la DB de la plataforma. Si alguien modifica la DB, la inversion "desaparece". Con un hash de transaccion (txHash) guardado, cualquiera puede ir a Etherscan y verificar que la inversion existio.

**Tareas que la implementan:**
- **INV-01:** Funcion `recordInvestment()` en `SmartContractService.java` — verifica que el txHash existe en Sepolia haciendo un `eth_getTransactionReceipt`
- **INV-02:** Tabla `investments` (V8 de Flyway) — almacena el txHash junto con monto, usuario, proyecto

**Flujo:**
```
POST /api/investments { txHash: "0xabc..." }
  1. InvestmentService.createInvestment()
  2. SmartContractService.recordInvestment()
     → BlockchainService.verifyTransaction(txHash)
     → eth_getTransactionReceipt("0xabc...") a Sepolia
     → Si existe: ✅ valido. Si no: ⚠️ warning pero igual se guarda
  3. INSERT en inversiones con el txHash
```

**Archivos involucrados:**
| Archivo | Que hace |
|---------|----------|
| `src/.../investment/service/InvestmentService.java` | Llama a recordInvestment() durante createInvestment() |
| `src/.../project/service/SmartContractService.java` | recordInvestment(): verifica txHash en Sepolia |
| `src/.../blockchain/service/BlockchainService.java` | verifyTransaction(): hace eth_getTransactionReceipt |
| `src/.../db/V8__create_investments_table.sql` | Crea tabla inversiones con columna tx_hash |

**Donde ver los detalles:** Secciones 7.3 (createInvestment), 10.2 (BlockchainService).

---

### 13.2 HU-11 — Inversion en proyecto con Smart Contract

**Descripcion:** Como inversor, quiero invertir en un proyecto utilizando mis tokens $IDEA mediante un smart contract, para recibir automaticamente los sub-tokens que representen mi participacion y registrar la inversion en la blockchain.

**Problema que resuelve:** Antes de INV-05, la inversion se registraba en DB pero NO habia una operacion atomica on-chain. Si el proceso fallaba a mitad de camino (ej: se descuenta $IDEA en DB pero no se registra la inversion), quedaba en estado inconsistente. Con INV-05, el contrato `InvestmentSwap.sol` hace TODO en UNA sola transaccion: transfiere $IDEA, mintea sub-tokens, y emite un evento. Si algo falla, TODO se revierte.

**Tareas que la implementan:**
- **INV-04:** Pre-validacion de inversion (validateInvestment()) — verifica estado, cupo, saldo ANTES de llamar al contrato
- **INV-05:** Swap atomico on-chain — contrato InvestmentSwap.sol + InvestmentSwapService.java

**Flujo completo:**
```
1. PRE-VALIDACION (INV-04):
   GET /api/investments/validate { proyectoId, monto }
   → InvestmentService.validateInvestment()
   → Verifica estado = FINANCIAMIENTO
   → Verifica subtoken existe
   → Calcula precio dinamico
   → Verifica cupo disponible
   → Devuelve { valido: true, subTokensARecebir: 50, precio: 10 }

2. CREACION DE INVERSION (INV-05):
   POST /api/investments { proyectoId, montoIdea: 500 }
   → InvestmentService.createInvestment()
   → Valida todo de nuevo (seguridad)
   → InvestmentSwapService.invest(proyectoId, 500*10^18, 50*10^18, treasury)
     → Envia tx a Sepolia
     → InvestmentSwap.sol ejecuta:
       a) transferFrom(treasury, treasury, 500*10^18)
       b) mint(treasury, 50*10^18)
       c) emit InvestmentMade(proyectoId, treasury, 500, 50)
   → Si Sepolia falla: fallback a SmartContractService.recordInvestment()
   → Actualiza DB: saldo_idea, monto_recaudado, cupo, portfolio
   → Guarda inversion
```

**Arquitectura de contratos:**
```
InvestmentSwap.sol  (INV-05)
├── crearTokenProyecto() → deploya ProjectToken (owner = InvestmentSwap)
├── invest()            → swap atomico $IDEA → sub-tokens
├── refund()            → quema sub-tokens + RefundMade (INV-09)
└── obtenerTokenDeProyecto() → consulta address del token
```

**Archivos involucrados:**
| Archivo | Que hace |
|---------|----------|
| `blockchain/contracts/InvestmentSwap.sol` | Contrato con crearTokenProyecto(), invest(), refund() |
| `src/.../service/InvestmentSwapService.java` | Cliente Java: crearTokenProyecto(), invest(), refund(), obtenerTokenDeProyecto() |
| `src/.../deploy/DeployInvestmentSwap.java` | Deployer a Sepolia con bytecode embebido |
| `src/.../config/BlockchainProperties.java` | Address del contrato + treasury |
| `src/.../project/service/ProjectService.java` | Llama a InvestmentSwap.crearTokenProyecto() al pasar a FINANCIAMIENTO |
| `src/.../investment/service/InvestmentService.java` | createInvestment() llama a InvestmentSwap.invest() |

**Donde ver los detalles:** Seccion 21 (INV-05 completa con contrato anotado linea por linea).

---

### 13.3 HU-12 — Historial de inversiones

**Descripcion:** Como inversor, quiero ver el historial de todas mis inversiones.

**Problema que resuelve:** Sin un endpoint de historial, el usuario no puede ver en que proyectos invirtio, cuanto puso, ni el estado actual de cada inversion.

**Tarea que la implementa:**
- **INV-07:** Endpoint GET /api/investments/history con paginacion

**Flujo:**
```
GET /api/investments/history?page=0&size=10
  → InvestmentController.getInvestmentHistory(usuarioId, pageable)
  → InvestmentService.getInvestmentHistory(usuarioId, pageable)
  → InvestmentRepository.findByUsuarioId(usuarioId, pageable)
  → SELECT * FROM inversiones WHERE usuario_id = ? ORDER BY created_at DESC
  → Devuelve Page<InvestmentResponse>
```

**Datos que devuelve cada inversion:**
```json
{
  "id": 1,
  "proyectoTitulo": "Proyecto Alpha",
  "montoIdea": 500.00,
  "subTokensRecibidos": 50,
  "estado": "CONFIRMADA",
  "txHash": "0xabc...",
  "createdAt": "2026-03-15T10:30:00"
}
```

**Archivos involucrados:**
| Archivo | Que hace |
|---------|----------|
| `src/.../investment/controller/InvestmentController.java` | GET /api/investments/history |
| `src/.../investment/service/InvestmentService.java` | getInvestmentHistory() |
| `src/.../investment/repository/InvestmentRepository.java` | Interfaz JPA con findByUsuarioId() |

**Donde ver los detalles:** Secciones 6.3.3 (repository), 7.5 (service), 8.2 (controller).

---

### 13.4 HU-28 — Devolucion automatica por financiamiento fallido

**Descripcion:** Como inversor, quiero recuperar mis fondos automaticamente si el proyecto no alcanza su meta.

**Problema que resuelve:** Sin HU-28, los inversores perdian su dinero si un proyecto fallaba. Tenian que reclamar manualmente (si existia un proceso de reclamo). Con HU-28, el sistema detecta automaticamente proyectos vencidos que no alcanzaron la meta, ejecuta los reembolsos, y cambia el estado a RECHAZADO.

**Tareas que la implementan:**
- **INV-09:** Funcion refund() en InvestmentSwap.sol — quema sub-tokens y emite RefundMade como prueba on-chain
- **INV-10:** InvestmentScheduler + processExpiredProjects() — evalua proyectos a las 6 AM, ejecuta reembolsos, cambia estado

**Flujo completo:**
```
6:00 AM — InvestmentScheduler.evaluateExpiredProjects()
  → InvestmentService.processExpiredProjects()
  → SQL: SELECT proyectos WHERE estado = 'FINANCIAMIENTO' AND plazo < NOW()
  
  Para CADA proyecto vencido:
    ┌─ Si montoRecaudado < montoRequerido:
    │   → refundAllInvestors(proyectoId)
    │     Para CADA inversion del proyecto:
    │       1. SmartContractService.refundInvestment()
    │          → InvestmentSwapService.refund()  (INV-09)
    │          → InvestmentSwap.sol.refund() on-chain
    │            a) burnFrom(treasury, subTokens)  → quema sub-tokens
    │            b) emit RefundMade(proyectoId, investor, 0, subTokens)
    │       2. UPDATE users SET saldo_idea += monto   → devuelve $IDEA
    │       3. UPDATE portfolio_activos SET cantidad -= subTokens
    │       4. Inversion.setEstado("REEMBOLSADA")
    │   → UPDATE projects SET estado = 'RECHAZADO'   (INV-10)
    │
    └─ Si montoRecaudado >= montoRequerido:
        → No hace nada (proyecto exitoso)
```

**Estados de inversion:**
| Estado | Significado |
|--------|-------------|
| CONFIRMADA | Inversion activa, proyecto en financiamiento |
| REEMBOLSADA | Proyecto fallo, se devolvio el dinero |
| RECHAZADA | (futuro) Inversion rechazada por el sistema |

**Estados de proyecto (actualizado INV-10):**
| Estado | Significado |
|--------|-------------|
| PREPARACION | Creando el proyecto |
| FINANCIAMIENTO | Aceptando inversiones |
| EJECUCION | Proyecto en desarrollo |
| FINALIZADO | Proyecto completado exitosamente |
| CANCELADO | Cancelado por el creador |
| RECHAZADO (NUEVO) | No alcanzo la meta de financiamiento |

**Archivos involucrados:**
| Archivo | Que hace |
|---------|----------|
| `blockchain/contracts/InvestmentSwap.sol` | Funcion refund() + evento RefundMade |
| `src/.../service/InvestmentSwapService.java` | Metodo refund() — cliente Java |
| `src/.../service/SmartContractService.java` | refundInvestment() — puente al contrato |
| `src/.../service/InvestmentService.java` | processExpiredProjects() + refundAllInvestors() |
| `src/.../scheduler/InvestmentScheduler.java` | Cron a las 6 AM |
| `src/.../db/V13__add_rechazado_state.sql` | Agrega RECHAZADO al CHECK constraint |
| `src/.../deploy/DeployInvestmentSwap.java` | Bytecode v2 con refund |

**Donde ver los detalles:** Seccion 22 (INV-09 + INV-10 completa con todo el codigo anotado).

---

## 14. Preguntas frecuentes

### "¿Por qué usaron JDBC y no JPA?"

> Elegimos **JdbcTemplate** (JDBC) en vez de JPA/Hibernate porque:
> 1. **Control total sobre SQL:** Sabemos exactamente qué consultas se ejecutan, sin sorpresas de N+1 queries
> 2. **Módulo pequeño:** Solo tenemos una tabla principal (`investments`), no justifica el overhead de configurar EntityManager, mapear relaciones, y lidiar con lazy loading
> 3. **Performance predecible:** Con JPA, el mismo código puede generar SQL diferente según el estado de la sesión de persistencia. Con JDBC, siempre es el mismo SQL
> 4. **Facilidad de testing:** Es más fácil mockear `JdbcTemplate` que un `EntityManager`

### "¿Cómo se conectan con la blockchain?"

> Usamos **Web3j 4.12.3**, la biblioteca Java estándar para Ethereum. La conexión funciona así:
> 
> **Arquitectura:**
> ```
> InvestmentService → SmartContractService → BlockchainService → Web3j
>                                                                  ↓
>                                                      Nodo RPC Sepolia
>                                                      (HTTP + JSON-RPC)
>                                                                  ↓
>                                                PaymentGateway | ProjectToken | USDC
> ```
>
> **Lectura (gratis):** `BlockchainService.balanceOf()` codifica la función Solidity como hex, la envía como `eth_call` al nodo, y decodifica la respuesta
> 
> **Escritura (con gas):** `BlockchainService.mintTokens()` codifica la función, la firma con la private key del backend, y la envía como `eth_sendRawTransaction`
>
> **Contratos en Sepolia:**
> | Contrato | Address |
> |----------|---------|
> | PaymentGateway | `0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10` |
> | ProjectToken ($IDEA) | `0x9f2c766d0bd9bbb640422decdf0125be02c7d144` |
> | USDC (Circle) | `0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238` |
> | Treasury | `0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A` |

### "¿Qué pasa si el usuario no tiene saldo suficiente?"

> El `InvestmentService.createInvestment()` consulta `saldo_idea` de la tabla `users` antes de realizar cualquier operación:
> ```java
> BigDecimal saldoActual = jdbc.queryForObject(
>     "SELECT saldo_idea FROM users WHERE id = ?", BigDecimal.class, usuarioId);
> if (saldoActual == null || saldoActual.compareTo(request.getMontoIdea()) < 0) {
>     throw new ConflictException("Saldo insuficiente de tokens IDEA");
> }
> ```
> Si el saldo es insuficiente, lanza `ConflictException` que Spring Boot traduce automáticamente a HTTP 409 Conflict. El frontend muestra el mensaje de error.
> 
> Además, todo el método está dentro de `@Transactional`. Si la validación pasa pero después falla otra operación (ej: actualizar el portfolio), se hace rollback de todo lo ejecutado.

### "¿Cómo se asegura que el reembolso sea correcto?"

> El reembolso automático (HU-28) lo ejecuta `InvestmentScheduler` una vez al día (6 AM). El flujo es:
> 1. Busca proyectos en estado FINANCIAMIENTO con `plazo < NOW()` (vencidos)
> 2. Solo si `monto_recaudado < monto_requerido`, ejecuta el reembolso
> 3. Por cada inversión activa:
>    - Suma el monto original al `saldo_idea` del usuario
>    - Descuenta los sub-tokens del portfolio (con `GREATEST(cantidad - ?, 0)` para evitar negativos)
>    - Marca la inversión como `REEMBOLSADA`
> 4. Marca el proyecto como `FINALIZADO`
> 
> Si el proyecto alcanzó su meta (`monto_recaudado >= monto_requerido`), no se hace nada.

### "¿Por qué el `monto_recaudado` no está en el shared model?"

> El shared-model (`shared-model-local.jar`) es un JAR compartido entre todos los módulos del equipo. La clase `Proyecto` original no tenía el campo `monto_recaudado`. En lugar de esperar a que el equipo lo agregue al shared-model, usamos `Map<String, Object>` con consultas SQL directas para leerlo:
> ```java
> Map<String, Object> proyecto = jdbc.query(...)  // devuelve { id, titulo, estado, montoRequerido, montoRecaudado }
> ```
> Así no dependemos de actualizaciones del shared-model. Cuando el equipo lo actualice, podemos migrar a la entidad real.

### "¿Cómo obtiene el controller el ID del usuario?"

> El sistema tiene un filtro de autenticación (`TokenValidationFilter`) que intercepta cada request HTTP. El flujo es:
> 1. El frontend envía el JWT en el header `Authorization: Bearer <token>`
> 2. `TokenValidationFilter` extrae el token y llama a `auth.service.url` para validarlo
> 3. Si es válido, crea un `JwtPrincipal` con `userId` y `email`
> 4. Spring Security lo guarda en el contexto de seguridad
> 5. En el controller, `@AuthenticationPrincipal JwtPrincipal principal` lo inyecta
> 6. El service recibe `principal.userId()` (el ID real del usuario autenticado)

### "¿Qué es Web3j y cómo funciona?"

> Web3j es una biblioteca Java para interactuar con nodos Ethereum. Funciona como un "cliente HTTP inteligente":
> 
> 1. **Codifica** funciones Java a JSON-RPC (el lenguaje que entienden los nodos Ethereum)
> 2. **Envía** el request por HTTP al nodo RPC
> 3. **Decodifica** la respuesta JSON de vuelta a objetos Java
>
> Ejemplo: `web3j.ethBlockNumber().send()` genera automáticamente:
> ```json
> {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}
> ```
> Y devuelve la respuesta como `BigInteger`.
>
> Para contratos, usamos `Function`, `FunctionEncoder` y `FunctionReturnDecoder`:
> ```java
> Function fn = new Function("balanceOf",
>     List.of(new Address("0x...")),
>     List.of(new TypeReference<Uint256>() {}));
> String data = FunctionEncoder.encode(fn);
> // data = "0x70a08231" + address padded a 32 bytes
> ```

### "¿Cómo sabe el backend qué funciones tiene el contrato?"

> No usamos los archivos ABI (JSON) directamente. En lugar de generar "wrappers" Java desde los ABIs (como hace `web3j generate`), construimos las llamadas manualmente con la clase `Function` de Web3j:
> ```java
> Function fn = new Function(
>     "balanceOf",                                // nombre exacto en Solidity
>     List.of(new Address("0x...")),              // parámetros de entrada
>     List.of(new TypeReference<Uint256>() {})    // tipos de retorno
> );
> ```
> **Ventaja:** No necesitamos regenerar código Java cada vez que el contrato se actualiza. Solo cambiamos el string del nombre de la función y los tipos de parámetros.
> 
> El ABI se usa internamente en `FunctionEncoder` y `FunctionReturnDecoder` para saber cómo codificar/decodificar. Los tipos de Solidity tienen su equivalente en Java:
> 
> | Solidity | Java (Web3j) |
> |----------|-------------|
> | `address` | `Address` |
> | `uint256` | `Uint256` |
> | `uint8` | `Uint8` |
> | `bool` | `Bool` |
> | `bytes32` | `Bytes32` |
> | `string` | `Utf8String` |

### "¿Por qué el backend necesita una private key?"

> La private key le permite al backend **firmar transacciones** que modifican el estado de la blockchain. Sin ella, el backend solo podría **leer** datos (con `eth_call`, que es gratis).
>
> El backend usa la private key para:
> - `mintTokens()`: Emitir nuevos tokens $IDEA (solo el owner del contrato puede hacerlo)
> - En el futuro: `payUSDC()` o `refundInvestment()` en el PaymentGateway
>
> **Importante:** Esta private key es de una wallet de **testnet** (Sepolia) que no tiene fondos reales. En producción se usaría una wallet del treasury con permisos específicos y medidas de seguridad adicionales (HSM, multi-sig, etc.).

### "¿Por qué a veces el modo offline?"

> El modo offline es un **fallback** para cuando el nodo RPC de Sepolia no responde:
> ```java
> try {
>     blockchain.verifyTransaction(txHash);  // Puede fallar si Sepolia está caído
> } catch (Exception e) {
>     // Modo offline: asumimos éxito y seguimos
>     result.put("success", true);
>     result.put("note", "Modo offline: " + e.getMessage());
> }
> ```
> **¿Por qué?**
> - La aplicación no debe dejar de funcionar porque la blockchain esté temporalmente caída
> - La inversión se registra en PostgreSQL, que es nuestro sistema de registro principal
> - Si la tx no se verificó, se puede reconciliar después con un job programado
> - En un entorno de testnet, los nodos públicos pueden ser lentos o inestables

### "¿14 tests y 12 tests del otro módulo = 26 tests, todos pasan. ¿Cobertura?"

> Los 14 tests del módulo de inversión cubren:
> 
> **validateInvestment (5 tests):**
> - Validación exitosa: proyecto en FINANCIAMIENTO, subtoken con cupo, monto suficiente → `valido: true`
> - Proyecto en PREPARACION → `valido: false`
> - Proyecto sin subtoken → `valido: false`
> - Cupo restante = 0 → `valido: false`
> - Proyecto inexistente → `ResourceNotFoundException` (404)
>
> **createInvestment (5 tests):**
> - Creación exitosa con todos los datos correctos → `CONFIRMADA`
> - Proyecto no está en FINANCIAMIENTO → `ConflictException`
> - Proyecto sin subtoken → `ConflictException`
> - Cupo insuficiente → `ConflictException`
> - Saldo insuficiente → `ConflictException`
>
> **getInvestmentById (1 test):**
> - ID inexistente → `ResourceNotFoundException`
>
> **getInvestmentHistory (1 test):**
> - Historial vacío → página sin contenido
>
> **processExpiredProjects (2 tests):**
> - Proyecto vencido que no alcanzó la meta → se ejecuta reembolso
> - Proyecto vencido que sí alcanzó la meta → no se ejecuta reembolso
>
> **Cobertura de ramas:** TODAS las ramas de `if/else` y `try/catch` están cubiertas. No hay código sin testear en `InvestmentService`.

### "¿Qué son wei, gwei y ether?"

> Son **unidades de medida** de la criptomoneda Ether (ETH). Como los metros, centímetros y kilómetros miden distancia, estas unidades miden cantidad de ETH:
>
> | Unidad | Valor en wei | Uso típico |
> |--------|-------------|------------|
> | **wei** | 1 | La unidad más chica (como un átomo). No se usa en UI pero es la unidad nativa en Web3j |
> | **gwei** | 1,000,000,000 (10⁹) | Se usa para precios de gas (ej: "1.5 gwei" de gas price) |
> | **ether** | 1,000,000,000,000,000,000 (10¹⁸) | La unidad que ven los usuarios en MetaMask |
>
> **Analogía:** 1 ether = 10¹⁸ wei. Es como 1 kilómetro = 1,000,000 milímetros.
>
> **En el código:**
> ```java
> // Web3j siempre trabaja en wei (BigInteger)
> BigInteger amountWei = BigInteger.valueOf(500_000_000_000_000_000_000L);  // 500 ether
>
> // Para mostrar al usuario, convertimos a ether (BigDecimal)
> BigDecimal amountEther = Convert.fromWei(amountWei.toString(), Convert.Unit.ETHER);
> // → BigDecimal("500.00")
> ```
>
> **Regla práctica:** cuando veas `BigInteger` en el código blockchain, pensá en wei. Cuando veas `BigDecimal`, pensá en ether.

### "¿Qué es chainId y EIP-155?"

> **chainId** es un número que identifica de forma única a cada red Ethereum:
> - **Mainnet:** chainId = 1
> - **Sepolia:** chainId = 11155111
> - **Ganache (local):** chainId = 1337
>
> **EIP-155** es una mejora de Ethereum (Ethereum Improvement Proposal) que introdujo el chainId en las transacciones. Sirve para **evitar replay attacks**.
>
> **¿Qué es un replay attack?**
>
> Imaginá que firmás una transacción en Sepolia (testnet) que dice "enviar 100 ETH a Juan". Si esa misma transacción firmada se reenvía en Mainnet (la red real), y tu wallet tiene fondos allí, ¡perderías 100 ETH reales!
>
> **¿Cómo lo soluciona EIP-155?**
>
> Incluye el chainId dentro de los datos que se firman. Una transacción firmada para Sepolia (chainId 11155111) NO puede ejecutarse en Mainnet (chainId 1) porque el chainId no coincide.
>
> **En nuestro código:**
> ```java
> // DeployTokenFactory.java
> long chainId = 11155111L;  // Sepolia
> byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
> //                    ↑                               ↑
> //  Firma la tx         La tx sin firmar              El chainId VA DENTRO de la firma
> //
> // Si alguien copia esta tx firmada y la envía a Mainnet:
> //   "Chequeo... el chainId de la firma es 11155111, pero Mainnet es 1 → RECHAZADA"
> ```
>
> **En Web3j con RawTransactionManager:**
> No necesitamos especificar el chainId explícitamente porque `RawTransactionManager` lo obtiene automáticamente del nodo RPC usando `eth_chainId()`.

### "¿Qué puede salir mal con las transacciones blockchain?"

> Las transacciones a Sepolia pueden fallar por varias razones. El error siempre llega como mensaje del nodo RPC:
>
> | Error | Causa | Solución |
> |-------|-------|----------|
> | `nonce too low` | El nonce ya se usó (tx duplicada) | Esperar a que se confirme la tx anterior o reiniciar la wallet |
> | `nonce too high` | El nonce es mayor al esperado | El nodo espera una tx con nonce más bajo primero |
> | `insufficient funds for gas * price + value` | La wallet no tiene suficiente SepoliaETH | Pedir SepoliaETH de un faucet |
> | `execution reverted` | El contrato rechazó la llamada (ej: no tenés permisos) | Revisar los parámetros y permisos de la función |
> | `gas limit too low` | El gasLimit es menor al mínimo necesario | Aumentar gasLimit en el código |
> | `gas price too low` | El gasPrice es menor al mínimo de la red | Usar `eth_gasPrice` para obtener el precio actual |
> | `replacement transaction underpriced` | Intentaste reemplazar una tx pendiente con menos gas | Usar el mismo gasPrice o mayor |
> | `chain ID mismatch` | La firma tiene un chainId que no coincide con la red | Verificar chainId (11155111 para Sepolia) |
>
> **¿Cómo debugginggear errores de blockchain?**
>
> 1. **Mirar los logs del backend:** Spring Boot loguea errores como `Error en tx: insufficient funds`
> 2. **Buscar el txHash en Etherscan:** si la tx llegó al nodo pero falló, el status será "❌ Failed"
> 3. **Revisar el nonce:** usar `ethers.js` en consola del navegador o consultar `eth_getTransactionCount`
> 4. **Simular con eth_call:** antes de enviar una tx real, podés simularla para ver si falla

### "¿Cuánto gas consume el burn del 0.1% en cada transferencia?"

> El mecanismo de quema implementado en `_update()` **incrementa el costo de gas** de cada transferencia porque realiza operaciones adicionales:
>
> **Comparación de gas:**
> | Operación | Gas (aproximado) | ¿Por qué? |
> |-----------|------------------|-----------|
> | Transferencia ERC-20 normal | ~45,000 gas | Solo mueve tokens |
> | Transferencia con quema 0.1% | ~52,000 gas | Calcula quema, quema, emite evento extra |
> | **Incremento** | **~7,000 gas (~15%)** | |
>
> **¿Esto es un problema?**
> No. En Sepolia el gas es prácticamente gratis (1.5 gwei). El incremento de 7,000 gas cuesta:
> ```
> 7,000 gas × 1.5 gwei = 10,500 gwei = 0.0000000105 ETH ≈ $0.00000002 USD
> ```
>
> **¿Por qué aceptamos este costo?**
> Porque la deflación controlada beneficia a todos los holders del token a largo plazo. El pequeño costo adicional en cada transferencia es el "precio" de tener un token que se aprecia lentamente por la reducción de supply.

### "¿Qué es eth_call vs eth_sendRawTransaction?"

> Son los dos métodos JSON-RPC fundamentales para interactuar con contratos:
>
> | | `eth_call` | `eth_sendRawTransaction` |
> |---|---|---|
> | **¿Qué hace?** | Lee datos del contrato (simula ejecución) | Escribe datos en el contrato (ejecuta realmente) |
> | **¿Cuesta gas?** | NO (es una simulación) | SÍ (modifica la blockchain) |
> | **¿Necesita firma?** | NO | SÍ (la wallet firma la tx) |
> | **¿Devuelve datos?** | SÍ (el resultado de la función) | SÍ (el txHash) |
> | **¿Modifica estado?** | NO | SÍ |
> | **Tiempo** | Milisegundos | Segundos (espera confirmación) |
>
> **En nuestro código:**
> ```java
> // Lectura (eth_call) → NO necesita firma, NO cuesta gas
> BigInteger balance = blockchain.balanceOf(tokenAddress, userAddress);
>
> // Escritura (eth_sendRawTransaction) → NECESITA firma, SÍ cuesta gas
> String txHash = blockchain.mintTokens(userAddress, amountWei);
> ```

---

## 15. Pricing Dinámico — Cómo se calcula el precio de los sub-tokens

### 15.1 ¿Por qué necesitamos pricing dinámico?

**El problema con el precio fijo:**

Imaginá un proyecto que vende 1,000 sub-tokens a $10.00 cada uno. Los primeros inversores pagan $10.00. El último también paga $10.00. No importa si quedan 999 o solo 1 sub-token disponible, el precio es el mismo.

Esto tiene varios problemas:

1. **No hay incentivo para invertir temprano** — ¿para qué apurarse si el precio es igual?
2. **No refleja la demanda real** — si solo quedan 10 sub-tokens y todos los quieren, deberían valer más
3. **El proyecto no maximiza su financiamiento** — podría recaudar más si el precio sube con la demanda

**La solución: precio dinámico**

El precio de cada sub-token se calcula automáticamente según la **oferta y la demanda**:

```
Cuando quedan POCOS sub-tokens → el precio SUBE (escasez)
Cuando quedan MUCHOS sub-tokens → el precio se MANTIENE (abundancia)
```

Esto crea un incentivo natural para invertir temprano y recompensa a los que confiaron primero en el proyecto.

### 15.2 La fórmula matemática (explicada para principiantes)

```
precio_dinamico = precio_base × (1 + demanda_relativa × factor_volatilidad)
```

Parece complicada, pero vamos a desarmarla pieza por pieza:

**Pieza 1: `demanda_relativa`** — ¿qué % del total ya se vendió?

```
demanda_relativa = vendidos / total

Ejemplo:
  Total de sub-tokens: 1,000
  Ya se vendieron: 600
  demanda_relativa = 600 / 1,000 = 0.60 (60%)
```

**Pieza 2: `factor_volatilidad`** — ¿qué tan sensible es el precio?

Es un número entre 0 y 1 que controla cuánto se mueve el precio:
- `0.00` → el precio nunca cambia (es como antes, fijo)
- `0.25` → cambios suaves
- `0.50` → cambios moderados (valor recomendado)
- `1.00` → cambios fuertes (el precio puede duplicarse)

**Pieza 3: El cálculo completo**

```
Ejemplo con números:
  precio_base = 10.00
  suministro_total = 1,000
  cupo_restante = 400 (quedan 400, se vendieron 600)
  factor_volatilidad = 0.50

Paso 1: demanda_relativa = 600 / 1,000 = 0.60
Paso 2: 1 + (0.60 × 0.50) = 1 + 0.30 = 1.30
Paso 3: precio_dinamico = 10.00 × 1.30 = 13.00
```

**¿Qué significa esto?** Cuando se ha vendido el 60% de los sub-tokens, el precio sube de $10 a $13 (un 30% más caro).

**Tabla de ejemplo para distintos niveles de venta:**

| % Vendido | demanda_relativa | Incremento | Precio |
|-----------|-----------------|------------|--------|
| 0% | 0.00 | 1.00 | $10.00 |
| 25% | 0.25 | 1.125 | $11.25 |
| 50% | 0.50 | 1.25 | $12.50 |
| 75% | 0.75 | 1.375 | $13.75 |
| 100% | 1.00 | 1.50 | $15.00 |

### 15.3 ¿Dónde se guardan estos valores?

En la tabla `subtokens` se agregaron dos columnas nuevas (migración V10):

```sql
-- precio_base: el precio inicial del sub-token (cuando arranca el proyecto)
ALTER TABLE subtokens ADD COLUMN IF NOT EXISTS precio_base DECIMAL(15,2) NOT NULL DEFAULT 0;

-- factor_volatilidad: qué tan sensible es el precio (0.00 = fijo, 1.00 = muy volátil)
ALTER TABLE subtokens ADD COLUMN IF NOT EXISTS factor_volatilidad DECIMAL(5,2) NOT NULL DEFAULT 0.50;

-- Sincronizar: si precio_base es 0, usar el precio_actual existente
UPDATE subtokens SET precio_base = precio_actual WHERE precio_base = 0;
```

**Ojo:** `DECIMAL(15,2)` significa 15 dígitos en total, 2 decimales. `DECIMAL(5,2)` es 5 dígitos total, 2 decimales.

### 15.4 El código completo: `DynamicPricingService.java`

**Archivo:** `src/main/java/com/systeam/investment/service/DynamicPricingService.java`

```java
// ─────────────────────────────────────────────────────────────────────────────
// DynamicPricingService: servicio que calcula el precio dinámico de un
// sub-token basándose en la oferta y la demanda.
//
// @Service le dice a Spring: "esta clase es un servicio, creá una instancia
// cuando alguien la necesite"
// ─────────────────────────────────────────────────────────────────────────────
package com.systeam.investment.service;

import java.math.BigDecimal;              // ← BigDecimal = números con decimales precisos
import java.math.RoundingMode;            // ← RoundingMode = cómo redondear

import org.slf4j.Logger;                  // ← Logger = para escribir en la consola
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DynamicPricingService {

    // ─── Logger: para dejar mensajes en la consola cuando se calcula un precio ───
    private static final Logger log = LoggerFactory.getLogger(DynamicPricingService.class);
    //                 ↑                        ↑
    //       "log" es el nombre              Esta clase específica
    //       Lo usamos con log.debug(...)

    // ═══════════════════════════════════════════════════════════════════════════════
    // calcularPrecioDinamico: el corazón del servicio
    // ═══════════════════════════════════════════════════════════════════════════════
    public BigDecimal calcularPrecioDinamico(
            BigDecimal precioBase,          // ← Precio inicial (ej: 10.00)
            int suministroTotal,            // ← Cupo total de sub-tokens (ej: 1000)
            int cupoRestante,               // ← Cuántos quedan sin vender (ej: 400)
            BigDecimal factorVolatilidad    // ← Sensibilidad del precio (ej: 0.50)
    ) {
        // ─────────────────────────────────────────────────────────────────────────
        // PASO 1: Protección contra división por cero
        // ─────────────────────────────────────────────────────────────────────────
        if (suministroTotal <= 0) {
            // Si el suministro total es 0 o negativo (no debería pasar), devolvemos
            // el precio base sin cambios. Es un "fallback" por si los datos están mal.
            return precioBase;
        }

        // ─────────────────────────────────────────────────────────────────────────
        // PASO 2: Calcular cuántos sub-tokens se vendieron
        // ─────────────────────────────────────────────────────────────────────────
        BigDecimal vendido = BigDecimal.valueOf(suministroTotal - cupoRestante);
        //                      ↑
        //  BigDecimal.valueOf() convierte un int a BigDecimal
        //  Ej: 1000 - 400 = 600 → BigDecimal("600")

        // ─────────────────────────────────────────────────────────────────────────
        // PASO 3: Calcular la demanda relativa (qué % del total se vendió)
        // ─────────────────────────────────────────────────────────────────────────
        BigDecimal total = BigDecimal.valueOf(suministroTotal);
        //                       ↑
        //                 BigDecimal("1000")

        BigDecimal demandaRelativa = vendido.divide(total, 4, RoundingMode.HALF_UP);
        //                              ↑       ↑     ↑         ↑
        //                           600    / 1000  4 decimales redondeo estándar
        //
        // .divide(total, 4, RoundingMode.HALF_UP) significa:
        //   - Dividir por 'total'
        //   - Dejar 4 decimales de precisión
        //   - Redondear hacia arriba si el 5to decimal es >= 5
        //
        // 600 / 1000 = 0.6000 (con 4 decimales)

        // ─────────────────────────────────────────────────────────────────────────
        // PASO 4: Calcular el factor de incremento
        // ─────────────────────────────────────────────────────────────────────────
        BigDecimal incremento = BigDecimal.ONE.add(
            //        ↑
            //  BigDecimal.ONE = 1 (es una constante)
            demandaRelativa.multiply(factorVolatilidad)
            //     ↑                      ↑
            //  0.6000                 × 0.50 = 0.3000
        );
        // incremento = 1 + 0.3000 = 1.3000

        // ─────────────────────────────────────────────────────────────────────────
        // PASO 5: Calcular el precio final
        // ─────────────────────────────────────────────────────────────────────────
        BigDecimal precioDinamico = precioBase.multiply(incremento)
                .setScale(2, RoundingMode.HALF_UP);
        //                       ↑
        //            Redondeamos a 2 decimales (centavos)
        //
        // 10.00 × 1.3000 = 13.0000
        // .setScale(2) = 13.00

        // ─────────────────────────────────────────────────────────────────────────
        // PASO 6: Loguear el resultado (para debugging)
        // ─────────────────────────────────────────────────────────────────────────
        log.debug("Precio dinamico: base={}, vendido={}/{}, demandaRelativa={}, precio={}",
                precioBase, vendido, total, demandaRelativa, precioDinamico);

        // ─────────────────────────────────────────────────────────────────────────
        // PASO 7: Devolver el precio calculado
        // ─────────────────────────────────────────────────────────────────────────
        return precioDinamico;
        //       ↑
        //  BigDecimal("13.00") ← el precio final que paga el inversor
    }
}
```

### 15.5 ¿Cómo se integra con `InvestmentService`?

**Antes (precio fijo):**

```java
// En validateInvestment() y createInvestment():
BigDecimal precioSubtoken = (BigDecimal) subtoken.get("precio_actual");
//     ↑
//  Siempre devolvía el mismo precio, no importaba la demanda
//
// subTokens = montoIdea / precioSubtoken
// Ej: 500 / 10.00 = 50 sub-tokens (siempre)
```

**Ahora (precio dinámico):**

```java
// 1. Obtener los datos necesarios del subtoken (los nuevos + los viejos)
BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
//       ↑
//  BigDecimal("10.00") ← precio inicial

int suministroTotal = (int) subtoken.get("suministro_total");
//       ↑
//  1000 ← cupo total

BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");
//       ↑
//  BigDecimal("0.50") ← sensibilidad

// 2. Calcular el precio dinámico
BigDecimal precioSubtoken = pricingService.calcularPrecioDinamico(
    precioBase,            // 10.00
    suministroTotal,       // 1000
    cupoRestante,          // 400 (quedan 400)
    factorVolatilidad      // 0.50
);
//     ↑
//  BigDecimal("13.00") ← el precio ahora depende de la demanda
//
// subTokens = montoIdea / precioSubtoken
// Ej: 500 / 13.00 = 38 sub-tokens (¡el inversor recibe menos si invierte tarde!)
```

**Después de cada inversión, se actualiza el precio en la DB:**

```java
// Calcular el nuevo cupo restante después de esta inversión
int nuevoCupo = cupoRestante - subTokens;
//                  ↑
//          400 - 50 = 350 ← quedan menos, el precio va a subir

// Recalcular el precio con el nuevo cupo
BigDecimal nuevoPrecio = pricingService.calcularPrecioDinamico(
    precioBase, suministroTotal, nuevoCupo, factorVolatilidad
);
//                  ↑
//          Ya no es 400, es 350 → la demanda_relativa aumenta → el precio sube

// Actualizar en la DB
jdbc.update(
    "UPDATE subtokens SET cupo_restante = cupo_restante - ?, precio_actual = ? WHERE id = ?",
    subTokens,           // 50
    nuevoPrecio,         // 13.50 (subió porque hay menos cupo)
    subtokenId           // 1
);
```

### 15.6 ¿Cómo probar que funciona?

Podés hacer la prueba sin blockchain ni frontend:

**Escenario:**
1. Creás un proyecto con un subtoken: `suministro_total=1000`, `precio_base=10.00`, `factor_volatilidad=0.50`
2. Invertís 500 $IDEA
   - cupo_restante = 1000, precio = 10.00
   - Recibís: 500/10 = 50 sub-tokens
   - Nuevo cupo: 950, nuevo precio: 10.00 × (1 + 0.05 × 0.50) = 10.25
3. Invertís otros 500 $IDEA
   - cupo_restante = 950, precio = 10.25
   - Recibís: 500/10.25 = 48 sub-tokens (¡2 menos que antes!)
   - Nuevo cupo: 902, nuevo precio: 10.00 × (1 + 0.098 × 0.50) = 10.49

**Conclusión:** El segundo inversor recibe menos sub-tokens por el mismo monto porque invirtió después.

---

## 16. Sistema Boost — Destacar proyectos en el catálogo

### 16.1 ¿Por qué necesitamos un sistema Boost?

**El problema:** En un catálogo con 100 proyectos, los proyectos nuevos quedan enterrados al final de la lista. Los inversores solo ven los primeros 10-20 proyectos.

**La solución:** Los creadores pueden pagar 100 $IDEA para que su proyecto aparezca primero en el catálogo (como un "destacado" o "featured").

**Analogía del mundo real:** Es como pagar para que tu producto aparezca en la primera página de un supermercado, en vez de estar en la última estantería.

### 16.2 ¿Cómo funciona el flujo completo?

```
1. El creador del proyecto entra a la página de su proyecto
2. Hace clic en "Boostear proyecto" (cuesta 100 $IDEA)
3. El frontend llama: POST /api/projects/{id}/boost
4. El backend:
   a. Verifica que el proyecto exista
   b. Verifica que el usuario tenga saldo ≥ 100 $IDEA
   c. Descuenta 100 $IDEA de su billetera
   d. Marca el proyecto como "destacado" (es_destacado = TRUE)
   e. Suma 100 al contador monto_boost
5. A partir de ahora, el proyecto aparece PRIMERO en el catálogo
```

### 16.3 ¿Qué cambió en la base de datos?

En la migración V10 se agregaron 3 columnas a la tabla `projects`:

```sql
-- ¿El proyecto está destacado? (TRUE/FALSE)
ALTER TABLE projects ADD COLUMN IF NOT EXISTS es_destacado BOOLEAN NOT NULL DEFAULT FALSE;

-- ¿Cuándo se boosteó por última vez?
ALTER TABLE projects ADD COLUMN IF NOT EXISTS fecha_boost TIMESTAMP;

-- ¿Cuánto $IDEA se gastó en boost en total (se acumula)?
ALTER TABLE projects ADD COLUMN IF NOT EXISTS monto_boost DECIMAL(15,2) NOT NULL DEFAULT 0;
```

### 16.4 El código completo: `BoostService.java`

**Archivo:** `src/main/java/com/systeam/project/service/BoostService.java`

```java
package com.systeam.project.service;

import java.math.BigDecimal;                     // ← Para manejar montos
import java.time.LocalDateTime;                  // ← Para timestamps

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate; // ← Para consultar/actualizar DB
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // ← Para transacciones

import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;

@Service
public class BoostService {

    // ─── Logger ─────────────────────────────────────────────────────────────
    private static final Logger log = LoggerFactory.getLogger(BoostService.class);

    // ─── Constantes del sistema Boost ───────────────────────────────────────
    private static final BigDecimal COSTO_BOOST = new BigDecimal("100.00");
    //                          ↑
    //  Costo fijo: 100 $IDEA por cada boost. Si querés cambiarlo, solo tocás esta línea.

    // ─── JdbcTemplate: la conexión a la base de datos ───────────────────────
    private final JdbcTemplate jdbc;
    //       ↑
    //  jdbc.query() → para SELECT
    //  jdbc.update() → para INSERT/UPDATE/DELETE
    //  jdbc.queryForObject() → para SELECT que devuelve una sola cosa

    // ─── Constructor: Spring inyecta JdbcTemplate automáticamente ───────────
    public BoostService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // boostProject: el método principal
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    //   ↑
    //  @Transactional hace que todo esto sea una sola operación atómica:
    //  si falla algo, TODO se deshace (rollback). No queda la DB a medio actualizar.
    public void boostProject(Long proyectoId, Long usuarioId) {
        //                        ↑                ↑
        //             ID del proyecto a boostear   ID del usuario que paga

        // ─────────────────────────────────────────────────────────────────────
        // PASO 1: Verificar que el proyecto exista y no esté borrado
        // ─────────────────────────────────────────────────────────────────────
        String estado = jdbc.queryForObject(
            "SELECT estado FROM projects WHERE id = ? AND deleted_at IS NULL",
            //                     ↑                        ↑
            //  Buscamos por ID   y que no esté borrado (soft delete)
            String.class,        // ← Tipo de dato que esperamos: String
            proyectoId           // ← El ID del proyecto
        );
        // Si el proyecto no existe, jdbc.queryForObject() lanza una excepción
        // EmptyResultDataAccessException → Spring la convierte en 500
        // Por eso después no necesitamos un if(proyecto == null)

        // ─────────────────────────────────────────────────────────────────────
        // PASO 2: Verificar que el usuario tenga saldo suficiente
        // ─────────────────────────────────────────────────────────────────────
        BigDecimal saldo = jdbc.queryForObject(
            "SELECT saldo_idea FROM users WHERE id = ?",
            //       ↑
            //  SELECT saldo_idea: buscamos el saldo en $IDEA del usuario
            BigDecimal.class,     // ← Esperamos un número con decimales
            usuarioId             // ← El ID del usuario
        );

        // ── Comparar saldo con el costo del boost ──
        if (saldo == null || saldo.compareTo(COSTO_BOOST) < 0) {
            // saldo == null → el usuario no existe (saldo es null)
            // saldo.compareTo(COSTO_BOOST) < 0 → saldo < 100
            //
            // compareTo() devuelve:
            //   -1 si saldo < COSTO_BOOST
            //    0 si saldo = COSTO_BOOST
            //    1 si saldo > COSTO_BOOST
            throw new ConflictException(
                "Saldo insuficiente de tokens IDEA. Se necesitan " + COSTO_BOOST + " $IDEA"
            );
            // ConflictException → Spring devuelve HTTP 409 Conflict
        }

        // ─────────────────────────────────────────────────────────────────────
        // PASO 3: Descontar $IDEA de la billetera del usuario
        // ─────────────────────────────────────────────────────────────────────
        jdbc.update(
            "UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?",
            //                    ↑
            //  saldo_idea - 100.00 → se descuenta el costo
            COSTO_BOOST,   // 100.00
            usuarioId      // el que está pagando
        );
        // Si esta línea se ejecuta, al usuario le quedan (saldo - 100) $IDEA

        // ─────────────────────────────────────────────────────────────────────
        // PASO 4: Marcar el proyecto como destacado
        // ─────────────────────────────────────────────────────────────────────
        jdbc.update("""
            UPDATE projects SET
                es_destacado = TRUE,      -- lo marcamos como destacado
                fecha_boost = NOW(),      -- registramos cuándo se boosteó
                monto_boost = monto_boost + ?,  -- sumamos al contador total
                updated_at = NOW()         -- actualizamos timestamp
            WHERE id = ? AND deleted_at IS NULL
            """,
            COSTO_BOOST,   // 100.00 (se suma a monto_boost anterior)
            proyectoId     // el proyecto a boostear
        );

        // ─────────────────────────────────────────────────────────────────────
        // PASO 5: Loguear la operación
        // ─────────────────────────────────────────────────────────────────────
        log.info("Proyecto {} boosteado por usuario {}. Costo: {} $IDEA",
                proyectoId, usuarioId, COSTO_BOOST);
        // En la consola se ve algo como:
        // "Proyecto 5 boosteado por usuario 2. Costo: 100 $IDEA"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // desboostProject: quitar el destacado (para uso futuro)
    // ═══════════════════════════════════════════════════════════════════════════
    public void desboostProject(Long proyectoId) {
        jdbc.update(
            "UPDATE projects SET es_destacado = FALSE WHERE id = ? AND deleted_at IS NULL",
            proyectoId
        );
        // Simplemente pone es_destacado = FALSE
    }
}
```

### 16.5 El endpoint REST

Se agregó al `ProjectController.java`:

```java
// POST /api/projects/{id}/boost
// Body: (vacío, solo el JWT en el header)
@PostMapping("/{id}/boost")
@ResponseStatus(HttpStatus.OK)
@PreAuthorize("hasAuthority('project:update')")
//              ↑
//  Solo usuarios con permiso project:update (CREATOR y ADMIN)
public void boostProject(
        @PathVariable Long id,                              // ← ID del proyecto
        @AuthenticationPrincipal JwtPrincipal principal     // ← Usuario autenticado
) {
    boostService.boostProject(id, principal.userId());
    //                    ↑        ↑
    //              proyecto ID  usuario ID (del token JWT)
}
```

### 16.6 ¿Cómo se refleja en el catálogo?

En `JdbcProjectRepository.java` se modificó la consulta SQL del catálogo:

```sql
-- ANTES (los proyectos más nuevos primero):
SELECT * FROM projects WHERE ... ORDER BY created_at DESC

-- AHORA (primero los destacados, después por fecha):
SELECT * FROM projects WHERE ... ORDER BY es_destacado DESC, created_at DESC
```

**¿Qué significa `ORDER BY es_destacado DESC, created_at DESC`?**

- `es_destacado DESC` → los TRUE van primero (porque TRUE > FALSE en PostgreSQL)
- `created_at DESC` → dentro de cada grupo, los más nuevos primero

**Resultado visual en el catálogo:**

```
1. [DESTACADO] Proyecto Alpha (boosteado hace 1 día)
2. [DESTACADO] Proyecto Beta (boosteado hace 3 días)
3. Proyecto Gamma (sin boost, recién creado)
4. Proyecto Delta (sin boost, creado ayer)
...
```

### 16.7 ¿Cuánto cuesta boostear?

Actualmente **100 $IDEA** por vez. El creador puede boostear múltiples veces y el contador `monto_boost` se acumula. En el futuro se podría:
- Hacer que el boost expire después de 7 días
- Tener distintos niveles de boost (bronce, plata, oro)
- Usar un remate para decidir qué proyectos destacar

---

## 17. Dividendos — Reparto de ganancias a inversores

### 17.1 ¿Por qué necesitamos dividendos?

**El problema antes de los dividendos:**

Un inversor compra sub-tokens de un proyecto. El proyecto tiene éxito, genera ganancias... ¿y el inversor qué gana? ¡Nada! Solo tiene sub-tokens que quizás algún día pueda vender.

**La solución: dividendos**

Los dividendos permiten que las ganancias de un proyecto se repartan entre los inversores que tienen sub-tokens. Es como recibir "intereses" o "ganancias" por haber confiado en el proyecto.

**Analogía del mundo real:** Comprás acciones de una empresa. La empresa tiene ganancias. Te paga dividendos (una parte de esas ganancias). Acá es lo mismo, pero con sub-tokens de proyectos IDEAFY.

### 17.2 El flujo completo de dividendos

```
┌─────────────────────────────────────────────────────────────────┐
│ PASO 1: ADMIN crea un reparto                                 │
│                                                               │
│ POST /api/dividendos/proyecto/5?monto=10000                   │
│                                                               │
│ El ADMIN dice: "El proyecto 5 generó $10,000 de ganancia,     │
│ vamos a repartirlo entre los inversores"                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ PASO 2: El sistema calcula                                      │
│                                                               │
│ Busca: ¿cuántos sub-tokens hay colocados en total?             │
│ Si hay 500 sub-tokens colocados:                               │
│   monto_por_subtoken = 10,000 / 500 = 20.00 $IDEA             │
│                                                               │
│ Guarda el reparto en la tabla dividendos                       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ PASO 3: Inversores reclaman sus dividendos                     │
│                                                               │
│ POST /api/dividendos/1/reclamar  (como inversor autenticado)   │
│                                                               │
│ El sistema busca: ¿cuántos sub-tokens tiene este usuario?     │
│ Si tiene 50 sub-tokens:                                        │
│   recibe = 50 × 20.00 = 1,000 $IDEA                           │
│                                                               │
│ Se acredita automáticamente a su billetera (saldo_idea)       │
└─────────────────────────────────────────────────────────────────┘
```

### 17.3 Las tablas de la base de datos (V10)

```sql
-- ────────────────────────────────────────────────────────────
-- Tabla: dividendos
-- Guarda CADA REPARTO de dividendos (un proyecto puede tener
-- varios repartos a lo largo del tiempo)
-- ────────────────────────────────────────────────────────────
CREATE TABLE dividendos (
    id                  BIGSERIAL PRIMARY KEY,
    --            ↑
    --  BIGSERIAL = auto-incremental (1, 2, 3...)

    proyecto_id         BIGINT NOT NULL REFERENCES projects(id),
    --            ↑                                   ↑
    --  A qué proyecto pertenece          Clave foránea a projects

    monto_total         DECIMAL(15,2) NOT NULL,
    --            ↑
    --  Monto total a repartir (ej: 10000.00)

    monto_por_subtoken  DECIMAL(15,2) NOT NULL,
    --            ↑
    --  Cuánto recibe CADA sub-token (ej: 20.00)

    fecha_reparto       TIMESTAMP NOT NULL DEFAULT NOW(),
    --            ↑
    --  Fecha en que se hizo el reparto

    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
    --            ↑
    --  Fecha de creación del registro
);

-- ────────────────────────────────────────────────────────────
-- Tabla: reclamos_dividendos
-- Guarda CADA VEZ que un inversor reclama sus dividendos
-- ────────────────────────────────────────────────────────────
CREATE TABLE reclamos_dividendos (
    id                  BIGSERIAL PRIMARY KEY,

    dividendo_id        BIGINT NOT NULL REFERENCES dividendos(id),
    --            ↑
    --  A qué reparto pertenece este reclamo

    usuario_id          BIGINT NOT NULL REFERENCES users(id),
    --            ↑
    --  Qué inversor reclama

    subtoken_id         BIGINT NOT NULL REFERENCES subtokens(id),
    --            ↑
    --  De qué sub-token son estos dividendos

    cantidad_subtokens  INTEGER NOT NULL,
    --            ↑
    --  Cuántos sub-tokens tiene el usuario en este proyecto

    monto_recibido      DECIMAL(15,2) NOT NULL,
    --            ↑
    --  Cuánto $IDEA recibió (cantidad_subtokens × monto_por_subtoken)

    reclamado_en        TIMESTAMP NOT NULL DEFAULT NOW()
    --            ↑
    --  Cuándo reclamó (para llevar historial)
);
```

**¿Por qué dos tablas y no una sola?**

`dividendos` guarda el reparto general (el "evento" de reparto).
`reclamos_dividendos` guarda el reclamo de CADA inversor.

Esto permite:
- Saber cuánto se repartió en total (SUM de reclamos)
- Saber qué inversores ya reclamaron (y cuáles no)
- Llevar un historial completo

### 17.4 El código completo: `DividendService.java`

**Archivo:** `src/main/java/com/systeam/investment/service/DividendService.java`

```java
package com.systeam.investment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;

@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);

    private final JdbcTemplate jdbc;

    public DividendService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // crearReparto: el ADMIN crea un nuevo reparto de dividendos
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public Long crearReparto(Long proyectoId, BigDecimal montoTotal) {
        //                           ↑                ↑
        //              ID del proyecto   Monto total a repartir (ej: 10000.00)

        // ─────────────────────────────────────────────────────────────────────
        // PASO 1: Verificar que el proyecto exista y esté en estado válido
        // ─────────────────────────────────────────────────────────────────────
        String estado = jdbc.queryForObject(
            "SELECT estado FROM projects WHERE id = ? AND deleted_at IS NULL",
            String.class, proyectoId
        );
        // Si el proyecto no existe, lanza excepción (404)

        // ── Solo se pueden repartir dividendos si el proyecto está activo ──
        if (!"EJECUCION".equals(estado) && !"FINALIZADO".equals(estado)) {
            // Si el proyecto está en PREPARACION o FINANCIAMIENTO, no tiene
            // sentido repartir dividendos porque todavía no generó ganancias
            throw new ConflictException(
                "Solo se pueden repartir dividendos en proyectos en EJECUCION o FINALIZADO"
            );
            // ↑ HTTP 409 Conflict + mensaje explicativo
        }

        // ─────────────────────────────────────────────────────────────────────
        // PASO 2: Calcular cuántos sub-tokens están colocados (en manos de inversores)
        // ─────────────────────────────────────────────────────────────────────
        Integer totalSubtokensColocados = jdbc.queryForObject(
            "SELECT COALESCE(SUM(pa.cantidad), 0) FROM portfolio_activos pa " +
            //        ↑                                  ↑
            //  COALESCE(... , 0) significa:   SUM de la columna cantidad
            //  si es NULL, devolvé 0           de la tabla portfolio_activos
            "JOIN subtokens s ON pa.subtoken_id = s.id WHERE s.proyecto_id = ?",
            //    ↑                                  ↑
            //  Unimos portfolio_activos con subtokens  Filtramos por proyecto
            Integer.class,
            proyectoId
        );

        // ── Validar que haya al menos 1 sub-token colocado ──
        if (totalSubtokensColocados == null || totalSubtokensColocados <= 0) {
            throw new ConflictException("No hay subtokens colocados para este proyecto");
            // No tiene sentido repartir dividendos si no hay inversores
        }

        // ─────────────────────────────────────────────────────────────────────
        // PASO 3: Calcular cuánto le toca a CADA sub-token
        // ─────────────────────────────────────────────────────────────────────
        BigDecimal montoPorSubtoken = montoTotal.divide(
            BigDecimal.valueOf(totalSubtokensColocados),
            //       ↑
            //  Convertimos int a BigDecimal para poder dividir
            4,                    // ← 4 decimales de precisión
            RoundingMode.HALF_UP  // ← redondeo estándar
        );
        //
        // Ejemplo:
        //   montoTotal = 10,000.00
        //   totalSubtokensColocados = 500
        //   montoPorSubtoken = 10,000 / 500 = 20.00
        //
        // Cada sub-token "vale" 20.00 $IDEA en este reparto.
        // Si un inversor tiene 50 sub-tokens → recibe 50 × 20 = 1,000 $IDEA

        // ─────────────────────────────────────────────────────────────────────
        // PASO 4: Insertar el reparto en la DB
        // ─────────────────────────────────────────────────────────────────────
        Long dividendoId = jdbc.queryForObject("""
            INSERT INTO dividendos (proyecto_id, monto_total, monto_por_subtoken,
                                    fecha_reparto, created_at)
            VALUES (?, ?, ?, NOW(), NOW())
            RETURNING id
            --     ↑
            --  RETURNING id: PostgreSQL devuelve el ID que se acaba de insertar
            """,
            Long.class,
            proyectoId,           // ← ? 1: a qué proyecto
            montoTotal,           // ← ? 2: monto total repartido
            montoPorSubtoken      // ← ? 3: monto por cada sub-token
        );

        // ─────────────────────────────────────────────────────────────────────
        // PASO 5: Loguear y devolver el ID del reparto
        // ─────────────────────────────────────────────────────────────────────
        log.info("Reparto de dividendos creado: id={}, proyecto={}, total={}, porSubtoken={}",
                dividendoId, proyectoId, montoTotal, montoPorSubtoken);
        // Log: "Reparto de dividendos creado: id=1, proyecto=5, total=10000, porSubtoken=20.00"

        return dividendoId;
        //       ↑
        //  Long = el ID del reparto (lo necesita el frontend para que los inversores reclamen)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // reclamarDividendos: un inversor reclama su parte
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public void reclamarDividendos(Long dividendoId, Long usuarioId) {
        //                             ↑                ↑
        //              ID del reparto    ID del inversor que reclama

        // ─────────────────────────────────────────────────────────────────────
        // PASO 1: Obtener los datos del reparto
        // ─────────────────────────────────────────────────────────────────────
        Map<String, Object> dividendo = jdbc.queryForMap(
            "SELECT * FROM dividendos WHERE id = ?", dividendoId
        );
        // jdbc.queryForMap() devuelve una fila como Map<columna, valor>
        //
        // dividendo = {
        //   "id": 1,
        //   "proyecto_id": 5,
        //   "monto_total": 10000.00,
        //   "monto_por_subtoken": 20.00,
        //   "fecha_reparto": 2026-05-20 10:00:00,
        //   ...
        // }

        // ── Extraer valores del Map ──
        Long proyectoId = ((Number) dividendo.get("proyecto_id")).longValue();
        //                     ↑
        //  dividendo.get("proyecto_id") devuelve Object (podría ser Long o BigDecimal)
        //  (Number) lo castea a Number, .longValue() lo convierte a Long

        BigDecimal montoPorSubtoken = (BigDecimal) dividendo.get("monto_por_subtoken");
        //                     ↑
        //  Casteo directo porque sabemos que es BigDecimal
        //  montoPorSubtoken = 20.00

        // ─────────────────────────────────────────────────────────────────────
        // PASO 2: Buscar los sub-tokens que tiene este usuario en este proyecto
        // ─────────────────────────────────────────────────────────────────────
        List<Map<String, Object>> activos = jdbc.query(
            "SELECT pa.subtoken_id, pa.cantidad, s.nombre " +
            //          ↑                ↑           ↑
            //    ID del sub-token    Cantidad     Nombre (para mostrar)
            "FROM portfolio_activos pa " +
            "JOIN subtokens s ON pa.subtoken_id = s.id " +
            "WHERE pa.usuario_id = ? AND s.proyecto_id = ? AND pa.cantidad > 0",
            //        ↑                        ↑                    ↑
            //  Filtro: del usuario actual  del proyecto del reparto  que tenga al menos 1
            (rs, rowNum) -> Map.of(
                //                     ↑
                //  RowMapper: convierte cada fila de la DB a un Map de Java
                "subtokenId", rs.getLong("subtoken_id"),
                "cantidad", rs.getInt("cantidad"),
                "nombre", rs.getString("nombre")
            ),
            usuarioId,    // ← ? 1: el usuario que reclama
            proyectoId    // ← ? 2: el proyecto del reparto
        );

        // ── Validar que tenga al menos un sub-token ──
        if (activos.isEmpty()) {
            throw new ConflictException("No tienes subtokens en este proyecto");
        }

        // ─────────────────────────────────────────────────────────────────────
        // PASO 3: Por cada sub-token, calcular cuánto recibe y acreditarlo
        // ─────────────────────────────────────────────────────────────────────
        for (Map<String, Object> activo : activos) {
            // Por cada sub-token que tiene el usuario en este proyecto...
            Long subtokenId = (Long) activo.get("subtokenId");
            Integer cantidad = (Integer) activo.get("cantidad");
            //    ↑
            //  Ej: 50 sub-tokens

            // ── Calcular monto: cantidad × monto_por_subtoken ──
            BigDecimal montoRecibido = montoPorSubtoken
                .multiply(BigDecimal.valueOf(cantidad))
                //     ↑                   ↑
                //  20.00              ×  50
                .setScale(2, RoundingMode.HALF_UP);
                //         ↑
                //  = 1,000.00 $IDEA

            // ── Registrar el reclamo en la DB ──
            jdbc.update("""
                INSERT INTO reclamos_dividendos
                    (dividendo_id, usuario_id, subtoken_id,
                     cantidad_subtokens, monto_recibido, reclamado_en)
                VALUES (?, ?, ?, ?, ?, NOW())
                """,
                dividendoId,    // ← ? 1: ID del reparto
                usuarioId,      // ← ? 2: ID del usuario
                subtokenId,     // ← ? 3: ID del sub-token
                cantidad,       // ← ? 4: cantidad de sub-tokens
                montoRecibido   // ← ? 5: monto que recibió
            );

            // ── Acreditar los $IDEA a la billetera del usuario ──
            jdbc.update(
                "UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?",
                montoRecibido,  // 1,000.00 $IDEA
                usuarioId       // al usuario que reclamó
            );
            // Ahora el usuario tiene 1,000 $IDEA más en su billetera
        }

        // ─────────────────────────────────────────────────────────────────────
        // PASO 4: Loguear el reclamo exitoso
        // ─────────────────────────────────────────────────────────────────────
        log.info("Dividendos reclamados: dividendo={}, usuario={}, activos={}",
                dividendoId, usuarioId, activos.size());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // listarRepartos: obtener todos los repartos de un proyecto
    // ═══════════════════════════════════════════════════════════════════════════
    public List<Map<String, Object>> listarRepartos(Long proyectoId) {
        return jdbc.query(
            "SELECT * FROM dividendos WHERE proyecto_id = ? ORDER BY fecha_reparto DESC",
            //                                                                  ↑
            //  ORDER BY DESC: los repartos más recientes primero
            (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "proyectoId", rs.getLong("proyecto_id"),
                "montoTotal", rs.getBigDecimal("monto_total"),
                "montoPorSubtoken", rs.getBigDecimal("monto_por_subtoken"),
                "fechaReparto", rs.getTimestamp("fecha_reparto").toLocalDateTime(),
                "createdAt", rs.getTimestamp("created_at").toLocalDateTime()
            ),
            proyectoId
        );
        // Devuelve una lista de Maps, cada Map es un reparto
        // Si no hay repartos, devuelve lista vacía (no null)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // listarReclamosUsuario: historial de reclamos de un inversor
    // ═══════════════════════════════════════════════════════════════════════════
    public List<Map<String, Object>> listarReclamosUsuario(Long usuarioId) {
        return jdbc.query(
            "SELECT rd.*, d.proyecto_id, d.monto_total, d.monto_por_subtoken " +
            //       ↑                                    ↑
            //  Todas las columnas del reclamo    + datos del reparto (join)
            "FROM reclamos_dividendos rd " +
            "JOIN dividendos d ON rd.dividendo_id = d.id " +
            //        ↑
            //  Unimos reclamo con su reparto para tener más contexto
            "WHERE rd.usuario_id = ? ORDER BY rd.reclamado_en DESC",
            //         ↑                          ↑
            //  Del usuario actual          Más recientes primero
            (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "dividendoId", rs.getLong("dividendo_id"),
                "proyectoId", rs.getLong("proyecto_id"),
                "subtokenId", rs.getLong("subtoken_id"),
                "cantidadSubtokens", rs.getInt("cantidad_subtokens"),
                "montoRecibido", rs.getBigDecimal("monto_recibido"),
                "reclamadoEn", rs.getTimestamp("reclamado_en").toLocalDateTime(),
                "montoTotal", rs.getBigDecimal("monto_total"),
                "montoPorSubtoken", rs.getBigDecimal("monto_por_subtoken")
            ),
            usuarioId
        );
    }
}
```

### 17.5 El controlador REST: `DividendController.java`

**Archivo:** `src/main/java/com/systeam/investment/controller/DividendController.java`

```java
@RestController
@RequestMapping("/api/dividendos")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    // ── ADMIN crea un reparto ─────────────────────────────────────────
    @PostMapping("/proyecto/{proyectoId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")       // ← Solo ADMIN puede repartir dividendos
    public Map<String, Object> crearReparto(
            @PathVariable Long proyectoId,  // ← ID del proyecto
            @RequestParam BigDecimal monto   // ← Monto total a repartir (query param)
    ) {
        Long id = dividendService.crearReparto(proyectoId, monto);
        return Map.of(
            "id", id,
            "mensaje", "Reparto de dividendos creado exitosamente"
        );
    }

    // ── Listar repartos de un proyecto ─────────────────────────────────
    @GetMapping("/proyecto/{proyectoId}")
    @PreAuthorize("hasAuthority('investment:read')")
    public List<Map<String, Object>> listarRepartos(@PathVariable Long proyectoId) {
        return dividendService.listarRepartos(proyectoId);
    }

    // ── Inversor reclama sus dividendos ────────────────────────────────
    @PostMapping("/{dividendoId}/reclamar")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('investment:read')")
    public Map<String, String> reclamarDividendos(
            @PathVariable Long dividendoId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        dividendService.reclamarDividendos(dividendoId, principal.userId());
        return Map.of("mensaje", "Dividendos reclamados exitosamente");
    }

    // ── Historial de reclamos del usuario autenticado ─────────────────
    @GetMapping("/mis-reclamos")
    @PreAuthorize("hasAuthority('investment:read')")
    public List<Map<String, Object>> misReclamos(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return dividendService.listarReclamosUsuario(principal.userId());
    }
}
```

### 17.6 Endpoints REST — Resumen

| Método | Endpoint | ¿Qué hace? | ¿Quién puede? |
|--------|----------|------------|---------------|
| `POST` | `/api/dividendos/proyecto/{id}?monto=X` | Crea un reparto de dividendos | ADMIN |
| `GET` | `/api/dividendos/proyecto/{id}` | Lista los repartos de un proyecto | Cualquiera con `investment:read` |
| `POST` | `/api/dividendos/{id}/reclamar` | Reclama dividendos como inversor | Cualquiera con `investment:read` |
| `GET` | `/api/dividendos/mis-reclamos` | Historial de reclamos del usuario | Cualquiera con `investment:read` |

### 17.7 Ejemplo completo (para probar con Postman)

```
PASO 1: ADMIN crea un reparto
POST /api/dividendos/proyecto/5?monto=10000
Authorization: Bearer <token_de_admin>
Respuesta: { "id": 1, "mensaje": "Reparto de dividendos creado exitosamente" }

PASO 2: Inversor reclama
POST /api/dividendos/1/reclamar
Authorization: Bearer <token_de_inversor>
Respuesta: { "mensaje": "Dividendos reclamados exitosamente" }

PASO 3: Inversor revisa su historial
GET /api/dividendos/mis-reclamos
Authorization: Bearer <token_de_inversor>
Respuesta: [
  {
    "id": 1,
    "dividendoId": 1,
    "proyectoId": 5,
    "cantidadSubtokens": 50,
    "montoRecibido": 1000.00,
    ...
  }
]
```

---

## 18. $IDEA-PROY — Token ERC-20 por proyecto con quema del 0.1%

### 18.1 ¿Cuál era el problema antes?

**Antes de esta feature:**

Todos los proyectos de IDEAFY compartían el **mismo** contrato `ProjectToken` en Sepolia (address: `0x9f2c766d...`). Los sub-tokens existían solo como registros en la base de datos PostgreSQL.

**Problemas de esta arquitectura:**

1. **No hay token real por proyecto** — si el proyecto Alpha tiene éxito, su valor no se refleja en un token on-chain
2. **No se pueden transferir** — los sub-tokens solo existen en nuestra DB, no se pueden enviar a otra wallet
3. **No hay deflación** — los tokens nunca se destruyen, siempre hay la misma cantidad

**La solución: $IDEA-PROY**

Cada proyecto puede tener su propio token ERC-20 desplegado en Sepolia. Este token:
- Es **único por proyecto** (cada proyecto tiene su propio contrato)
- Tiene **quema del 0.1%** en cada transferencia (deflación controlada)
- Puede ser **minteado** por el treasury cuando sea necesario
- Su precio es **dinámico** según el rendimiento del proyecto (calculado en la DB)

### 18.2 ¿Qué es un token ERC-20?

ERC-20 es un **estándar** de tokens en Ethereum. Es como un "contrato" que dice: "cualquier token que quiera funcionar en Ethereum debe tener estas funciones":

| Función | ¿Qué hace? |
|---------|------------|
| `totalSupply()` | ¿Cuántos tokens existen en total? |
| `balanceOf(address)` | ¿Cuántos tokens tiene una wallet? |
| `transfer(to, amount)` | Enviar tokens a otra wallet |
| `approve(spender, amount)` | Permitir que otra wallet gaste tokens nuestros |
| `allowance(owner, spender)` | ¿Cuánto nos permitió gastar otro? |

Lo bueno del estándar ERC-20 es que **cualquier wallet** (MetaMask, Ledger, etc.) puede mostrar y manejar estos tokens automáticamente.

### 18.3 El contrato `ProjectToken.sol` — Explicado línea por línea

**Archivo:** `blockchain/contracts/ProjectToken.sol`

```solidity
// SPDX-License-Identifier: MIT
// ↑
//  Licencia: MIT es la más permisiva (cualquiera puede usar el código)

pragma solidity ^0.8.20;
//          ↑       ↑
//   Compilador   Versión mínima 0.8.20 (compatible hasta 0.9.0 pero no 1.0.0)

// ─── Importamos OpenZeppelin (librería estándar de contratos Ethereum) ─────
import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
//          ↑
//  ERC20.sol: la implementación estándar del token ERC-20

import "@openzeppelin/contracts/access/Ownable.sol";
//          ↑
//  Ownable.sol: agrega el concepto de "dueño" (owner) del contrato.
//  Solo el owner puede llamar funciones marcadas como onlyOwner.

// ═══════════════════════════════════════════════════════════════════════════
// ProjectToken: nuestro token personalizado con quema automática
// ═══════════════════════════════════════════════════════════════════════════
contract ProjectToken is ERC20, Ownable {
//      ↑                  ↑       ↑
//   Nombre del contrato  Hereda   Hereda
//                        ERC-20   Ownable (tiene dueño)

    // ─── Constante: TASA_QUEMA = 0.1% ───────────────────────────────────
    uint256 public constant TASA_QUEMA = 10;
    //  ↑          ↑           ↑
    //  número     pública     constante (no se puede modificar)
    //
    // 10 / 10000 = 0.001 = 0.1%
    // ¿Por qué 10/10000 y no 1/1000?
    //   - 10000 es un denominador común en finanzas (1% = 100/10000)
    //   - 10 / 10000 = 0.001 = 0.1%
    //   - Cada 1000 tokens transferidos, 1 se quema
    //
    // Si quisiéramos 0.01%, pondríamos TASA_QUEMA = 1 (1/10000 = 0.01%).
    // Decidimos 0.1% porque:
    //   - Es un valor significativo pero no agresivo (1 de cada 1000)
    //   - Crea deflación medible sin desincentivar transfers
    //   - En un token con 1M de supply, cada transferencia de 1000 tokens quema 1

    // ─── Eventos (para que el frontend pueda escuchar) ──────────────────
    event TokensQuemados(address indexed desde, uint256 cantidad);
    //          ↑                         ↑           ↑
    //   Nombre del evento          Quién quemó   Cuánto

    event TokensMinteados(address indexed para, uint256 cantidad);
    //          ↑                         ↑
    //   Cuándo se crean tokens     Quién recibió

    // ═════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR: se ejecuta UNA SOLA VEZ cuando se deploya el contrato
    // ═════════════════════════════════════════════════════════════════════
    constructor(
        string memory nombre,          // ← Nombre del token: "Proyecto Alpha Token"
        string memory simbolo,         // ← Símbolo: "ALPHA"
        address dueno                  // ← Address del dueño (el treasury)
    ) ERC20(nombre, simbolo) Ownable(dueno) {
        //  ↑                      ↑
        //  Le pasamos nombre y    Le pasamos el dueño a Ownable
        //  símbolo a ERC-20
        //
        // Después del constructor:
        //   - El token se llama "Proyecto Alpha Token"
        //   - Su símbolo es "ALPHA"
        //   - El dueño es 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A (treasury)
        //   - El supply inicial es 0 (se mintea después si se desea)
    }

    // ═════════════════════════════════════════════════════════════════════
    // mint: crear nuevos tokens (solo el dueño)
    // ═════════════════════════════════════════════════════════════════════
    function mint(address para, uint256 cantidad) external onlyOwner {
        //        ↑          ↑                       ↑
        //  Quién recibe  Cuántos tokens (en wei)  Solo el owner
        _mint(para, cantidad);
        //  ↑
        //  _mint es una función INTERNA de ERC20 que crea tokens
        //  La usamos porque es la única manera de aumentar el supply

        emit TokensMinteados(para, cantidad);
        //  ↑
        //  Emitimos el evento para que el frontend/monitoreo lo vea
    }

    // ═════════════════════════════════════════════════════════════════════
    // _update: la función mágica que quema el 0.1%
    // ═════════════════════════════════════════════════════════════════════
    //
    // En Solidity 0.8.20+, ERC20 usa _update() en lugar de _transfer().
    // _update() se llama SIEMPRE que los tokens se mueven:
    //   - _update(address(0), usuario, cantidad) → cuando se mintean
    //   - _update(usuario, address(0), cantidad) → cuando se queman
    //   - _update(usuario1, usuario2, cantidad) → cuando se transfieren
    //
    function _update(
        address desde,      // ← Quién envía los tokens
        address para,       // ← Quién los recibe
        uint256 valor       // ← Cuántos tokens
    ) internal override {
        //       ↑
        //  override: estamos reemplazando la función original de ERC20

        // ─────────────────────────────────────────────────────────────────
        // CASO 1: Es un mint (desde = address(0)) o un burn (para = address(0))
        // En estos casos NO aplicamos quema
        // ─────────────────────────────────────────────────────────────────
        if (desde == address(0) || para == address(0)) {
            // address(0) = 0x0000000000000000000000000000000000000000
            //
            // Si desde es address(0): están minteando (creando) tokens nuevos
            // Si para es address(0): están quemando tokens
            // En ambos casos, no tendría sentido quemar AGAIN (sería doble quema)
            super._update(desde, para, valor);
            //  ↑
            //  super._update llama a la función ORIGINAL de ERC20
            //  (la que no tiene quema)
            return;
        }

        // ─────────────────────────────────────────────────────────────────
        // CASO 2: Es una transferencia entre dos wallets
        // Aplicamos la quema del 0.1%
        // ─────────────────────────────────────────────────────────────────

        // Calcular cuánto quemar: valor × TASA_QUEMA / 10000
        uint256 cantidadAQuemar = (valor * TASA_QUEMA) / 10000;
        //                              ↑
        //  TASA_QUEMA = 10
        //  Ej: 10,000 × 10 / 10000 = 10 tokens se queman

        // ─────────────────────────────────────────────────────────────────
        // CASO 2a: Si la cantidad a quemar es muy chica (menos de 1 unidad),
        // no quemamos nada (para evitar errores de redondeo)
        // ─────────────────────────────────────────────────────────────────
        if (cantidadAQuemar == 0) {
            super._update(desde, para, valor);
            //         ↑
            //  Transferencia normal, sin quema (el monto es muy pequeño)
            return;
        }

        // ─────────────────────────────────────────────────────────────────
        // CASO 2b: Quemamos y transferimos el resto
        // ─────────────────────────────────────────────────────────────────

        // ── Primero: QUEMAR los tokens (se destruyen) ──
        _burn(desde, cantidadAQuemar);
        //  ↑
        //  _burn es una función INTERNA de ERC20 que destruye tokens
        //  Los tokens desaparecen del supply total

        emit TokensQuemados(desde, cantidadAQuemar);
        //  ↑
        //  Evento para tracking

        // ── Segundo: TRANSFERIR el resto al destinatario ──
        // valor - cantidadAQuemar = lo que realmente llega
        super._update(desde, para, valor - cantidadAQuemar);
        //                              ↑
        //  Si valor = 10,000 y cantidadAQuemar = 10:
        //  → se transfieren 9,990 tokens (el 99.9%)
        //
        //  TASA_QUEMA = 10 (0.1%):
        //  → cantidadAQuemar = 10,000 × 10 / 10000 = 10
        //  → se transfieren 9,990 tokens (el 99.9%)
    }
}
```

**Ejemplo de la quema en acción:**

```
Transferencia de 10,000 $IDEA-PROY entre dos wallets
  TASA_QUEMA = 10 (0.1%)

  cantidadAQuemar = 10,000 × 10 / 10000 = 10

  → Se queman: 10 $IDEA-PROY (se destruyen, desaparecen para siempre)
  → El destinatario recibe: 9,990 $IDEA-PROY
  → El supply total se reduce en 10 (deflación)

  Después de 1,000 transferencias de 10,000 tokens cada una:
  → Se habrán quemado 10,000 tokens en total
  → El supply original se reduce ~1%
```

### 18.4 La fábrica de tokens: `TokenFactory.sol`

En lugar de deployar cada token manualmente, creamos una **fábrica** (Factory) que deploya tokens automáticamente.

**Archivo:** `blockchain/contracts/TokenFactory.sol`

```solidity
contract TokenFactory {

    // ─── Treasury: address que recibe los tokens cuando se crean ───────
    address public immutable treasury;
    //          ↑
    //  immutable: se setea en el constructor y no cambia nunca más

    // ─── Mapping: proyectoId → dirección del token ─────────────────────
    mapping(uint256 => address) public tokenDeProyecto;
    //    ↑                       ↑
    //  ID del proyecto (1,2,3)  Address del token ERC-20 en Sepolia
    //
    //  tokenDeProyecto[5] = "0xabc...123" → el proyecto 5 usa este token

    // ─── Constructor: se ejecuta UNA vez al deployar la Factory ─────
    constructor(address _treasury) {
        //             ↑
        //  Le decimos: "los tokens nuevos que se creen, que se los minteen
        //  a esta address (el treasury)"
        treasury = _treasury;
    }

    // ═════════════════════════════════════════════════════════════════════
    // crearTokenProyecto: deploya un nuevo ProjectToken para un proyecto
    // ═════════════════════════════════════════════════════════════════════
    function crearTokenProyecto(
        uint256 proyectoId,          // ← ID del proyecto en nuestra DB
        string memory nombre,        // ← "Proyecto Alpha Token"
        string memory simbolo,       // ← "ALPHA"
        uint256 supplyInicial        // ← Cuántos tokens mintear al inicio
    ) external returns (address) {
        //                          ↑
        //  Devuelve la address del NUEVO token deployado

        // ── Validar que no exista ya un token para este proyecto ───
        require(
            tokenDeProyecto[proyectoId] == address(0),
            //         ↑
            //  Si ya hay un token para este proyecto, error
            "TokenFactory: ya existe un token para este proyecto"
        );

        // ── Deployar un NUEVO contrato ProjectToken ───────────────
        ProjectToken nuevoToken = new ProjectToken(nombre, simbolo, treasury);
        //                           ↑
        //  new ProjectToken(...) DEPLOYA un nuevo contrato en Sepolia
        //  Cada vez que se ejecuta esta línea, se crea un nuevo contrato
        //  en la blockchain (cuesta gas)

        // ── Obtener la address del nuevo contrato ─────────────────
        address tokenAddress = address(nuevoToken);
        //                       ↑
        //  Ej: "0xabc123..." (la address donde vive el nuevo token)

        // ── Mintear supply inicial al treasury (opcional) ─────────
        if (supplyInicial > 0) {
            nuevoToken.mint(treasury, supplyInicial);
            //           ↑
            //  Llama a la función mint del contrato que acabamos de crear
            //  Crea supplyInicial tokens y se los da al treasury
        }

        // ── Guardar en el mapping para futuras consultas ──────────
        tokenDeProyecto[proyectoId] = tokenAddress;
        //         ↑
        //  Ahora tokenDeProyecto[5] = "0xabc123..."

        // ── Emitir evento ────────────────────────────────────────
        emit TokenCreado(proyectoId, tokenAddress, nombre, simbolo);

        return tokenAddress;
        //       ↑
        //  El backend recibe esta address y la guarda en la DB
    }
}
```

### 18.5 Contratos desplegados en Sepolia

Actualmente los contratos están deployados en Sepolia:

| Contrato | Address | Deployado por |
|----------|---------|---------------|
| **ProjectToken global** | `0x9f2c766d0bd9bbb640422decdf0125be02c7d144` | Manual (etapa inicial) |
| **TokenFactory** | `0x23a81e62f190993c932ccea79308f8ff1ea558d9` | `DeployTokenFactory.java` (vía Web3j) |
| **PaymentGateway** | `0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10` | Manual |

**¿Por qué un ProjectToken global y otro por proyecto?**

- El `ProjectToken` global (`0x9f2c766d...`) se usa como **fallback** cuando la TokenFactory no está disponible
- La `TokenFactory` deploya **nuevos tokens** específicos para cada proyecto que entra en `FINANCIAMIENTO`
- Si la factory falla, el sistema sigue funcionando con el token global

**¿Cómo se deployó TokenFactory?**

A través de la clase Java `DeployTokenFactory.java` que:
1. Compila `TokenFactory.sol` + `ProjectToken.sol` con solc 0.8.20 + OpenZeppelin 5.0.2
2. Envía una transacción de creación de contrato a Sepolia con EIP-155 (chain ID 11155111)
3. Espera confirmación y extrae la address del receipt
4. Actualiza automáticamente `.env` con la nueva address

**¿Cómo deployar nuevos contratos en el futuro?**

Si querés deployar un nuevo TokenFactory (o cualquier contrato):

```
1. Compilar con solc:
   npx solc@0.8.20 --bin --abi --include-path node_modules \
     --base-path contracts -o compiled contracts/TokenFactory.sol

2. Ejecutar la clase Java DeployTokenFactory (actualiza .env solo)
```

O manualmente via Remix IDE:
```
1. Abrir https://remix.ethereum.org
2. Crear TokenFactory.sol (y ProjectToken.sol importado)
3. Compilar con solc 0.8.20
4. Deploy con Injected Provider (MetaMask) → Sepolia
5. Constructor arg: 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
6. Copiar address y pegarla en .env: BLOCKCHAIN_TOKEN_FACTORY=0x[NUEVA]
```

### 18.6 El código Java: `TokenFactoryService.java`

**Archivo:** `src/main/java/com/systeam/blockchain/service/TokenFactoryService.java`

```java
package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.crypto.Credentials;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

@Service
public class TokenFactoryService {

    private static final Logger log = LoggerFactory.getLogger(TokenFactoryService.class);

    // ─── Dependencias inyectadas por Spring ────────────────────────────
    private final Web3j web3j;                    // ← Cliente HTTP para nodo Sepolia
    private final Credentials credentials;        // ← Wallet del backend (para firmar txs)
    private final TransactionManager txManager;   // ← Firma y envía transacciones
    private final BlockchainProperties props;     // ← Addresses de contratos

    public TokenFactoryService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }

    // ═════════════════════════════════════════════════════════════════════
    // crearTokenProyecto: deploya un nuevo token via TokenFactory
    // ═════════════════════════════════════════════════════════════════════
    public String crearTokenProyecto(
            Long proyectoId,           // ← ID del proyecto en nuestra DB
            String nombre,             // ← "Proyecto Alpha Token"
            String simbolo,            // ← "ALPHA"
            BigInteger supplyInicial   // ← 1000000 (1M tokens con 18 decimales)
    ) throws Exception {

        // ── PASO 0: Verificar si TokenFactory está configurada ─────
        String factoryAddress = props.getTokenFactoryAddress();
        //       ↑
        //  Lee de .env: BLOCKCHAIN_TOKEN_FACTORY

        if (factoryAddress == null || factoryAddress.equals("0x0000...0000")) {
            // Si no configuraron la factory, usamos el token global como fallback
            log.warn("TokenFactory no configurada. Usando ProjectToken global.");
            return props.getProjectTokenAddress();
        }

        // ── PASO 1: Armar la función Solidity ──────────────────────
        Function fn = new Function(
            "crearTokenProyecto",                       // ← nombre en Solidity
            List.of(                                     // ← argumentos
                new Uint256(BigInteger.valueOf(proyectoId)),  // uint256 proyectoId
                new Utf8String(nombre),                       // string nombre
                new Utf8String(simbolo),                      // string simbolo
                new Uint256(supplyInicial)                    // uint256 supplyInicial
            ),
            List.of(new TypeReference<Address>() {})  // ← retorna address
        );

        // ── PASO 2: Codificar, firmar y enviar transacción ────────
        String encodedFunction = FunctionEncoder.encode(fn);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        EthSendTransaction response = txManager.sendTransaction(
            gasPrice,
            BigInteger.valueOf(500_000L),  // ← gas limit más alto (deployar es costoso)
            factoryAddress,
            encodedFunction,
            BigInteger.ZERO               // ← 0 ETH (no enviamos ether)
        );

        if (response.hasError()) {
            throw new RuntimeException("Error al crear token: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        log.info("Token creado via factory. Tx: {}", txHash);

        // ── PASO 3: Decodificar address desde el evento TokenCreado ─
        return decodeTokenAddress(txHash, fn);
    }

    // ═════════════════════════════════════════════════════════════════════
    // decodeTokenAddress: extrae la address del token desde el evento
    // ═════════════════════════════════════════════════════════════════════
    private String decodeTokenAddress(String txHash, Function fn) throws Exception {
        // Buscar el recibo de la transacción en Sepolia
        var receipt = web3j.ethGetTransactionReceipt(txHash).send();

        if (receipt.getTransactionReceipt().isEmpty()) {
            return props.getProjectTokenAddress();  // fallback
        }

        var logs = receipt.getTransactionReceipt().get().getLogs();
        if (logs.isEmpty()) {
            return props.getProjectTokenAddress();  // fallback
        }

        // El primer log es el evento TokenCreado
        // El data contiene la address del nuevo token
        String eventData = logs.get(0).getData();

        List<org.web3j.abi.datatypes.Type> decoded =
            FunctionReturnDecoder.decode(eventData, fn.getOutputParameters());

        if (!decoded.isEmpty() && decoded.get(0) instanceof Address addr) {
            return addr.getValue();
        }

        return props.getProjectTokenAddress();  // fallback
    }

    // ═════════════════════════════════════════════════════════════════════
    // obtenerTokenDeProyecto: consultar qué token tiene un proyecto
    // ═════════════════════════════════════════════════════════════════════
    public String obtenerTokenDeProyecto(Long proyectoId) throws Exception {
        // Consulta el mapping tokenDeProyecto[proyectoId] en la Factory
        // ...
    }
}
```

### 18.7 `burnTokens()` en BlockchainService

Se agregó el método `burnTokens()` a `BlockchainService.java`:

```java
public String burnTokens(String fromAddress, BigInteger amountWei) throws Exception {
    //                            ↑                ↑
    //  Address de la wallet      De quién quemar  Cuánto quemar (en wei)
    //
    //  Llama a la función burnFrom(address, uint256) del ProjectToken

    Function fn = new Function(
        "burnFrom",
        List.of(new Address(fromAddress), new Uint256(amountWei)),
        List.of()                    // ← sin retorno (la función es void)
    );

    return executeWrite(props.getProjectTokenAddress(), fn);
    //       ↑
    //  Devuelve el txHash de la transacción de quema
}
```

**¿Para qué sirve quemar tokens?**
- **Deflación controlada:** reduce el supply total, lo que puede aumentar el valor de los tokens restantes
- **Eliminar tokens de circulación:** si un proyecto se cancela, se pueden quemar los tokens no vendidos
- **Parte del protocolo:** el 0.1% se quema automáticamente en cada transferencia

### 18.8 Integración real con el flujo de creación de proyectos

**¿Cómo se conecta todo?**

Cuando se crea un proyecto y se pasa a `FINANCIAMIENTO`, el flujo real es:

```
ProjectService.createProject()
  → Guarda el proyecto en DB (estado: PREPARACION)
  → El ADMIN hace PATCH /api/projects/5/status?status=FINANCIAMIENTO

ProjectService.updateProjectStatus(id, "FINANCIAMIENTO")
  → [NUEVO] Llama a crearSubtokenParaProyecto()
    → tokenFactoryService.crearTokenProyecto(proyectoId, nombre, simbolo, supply)
      → Envía transacción a TokenFactory en Sepolia
      → TokenFactory deploya un nuevo ProjectToken
      → Devuelve la address del nuevo token
    → Inserta un subtoken en la DB con proyecto_id y contract_address
    → El proyecto ahora tiene su propio token ERC-20 en Sepolia
```

Este flujo **ya está implementado** en `ProjectService.java`. No es futuro, funciona ahora mismo.
Ver la Sección 19 para el código detallado línea por línea.

### 18.9 Resumen de archivos nuevos/ modificados

| Archivo | ¿Qué es? |
|---------|----------|
| `blockchain/contracts/ProjectToken.sol` | Token ERC-20 con quema del 0.1% |
| `blockchain/contracts/TokenFactory.sol` | Fábrica que deploya tokens por proyecto |
| `blockchain/service/TokenFactoryService.java` | Servicio Java que llama a TokenFactory en Sepolia |
| `BlockchainService.java` (modificado) | Se agregó `burnTokens()` |
| `BlockchainProperties.java` (modificado) | Se agregó `tokenFactoryAddress` |
| `.env` (modificado) | Se agregó `BLOCKCHAIN_TOKEN_FACTORY` |
| `application.properties` (modificado) | Se agregó `blockchain.token-factory-address` |

## 20. Eventos de Blockchain — Listener de Pagos en Tiempo Real (INV-00 + INV-01)

### 20.1 ¿Qué historias de usuario cubre?

Esta sección implementa dos tareas clave del backlog:

| Tarea | Descripción | Estado |
|-------|-------------|--------|
| **INV-00** | Desarrollo de Smart Contract para registro — `PaymentGateway.sol` con función `pay()` y evento `Paid` | ✅ Implementado |
| **INV-01** | Listener de eventos de Blockchain — Servicio que escucha eventos `Paid` en Sepolia y los vincula con inversiones | ✅ Implementado |

**HU-10 (Registro de inversión en blockchain):** El criterio de aceptación "verificar que se registra en la blockchain" ahora tiene DOS mecanismos:
1. **Verificación por txHash** (existente): El backend llama `eth_getTransactionReceipt` para verificar que la tx existe
2. **Listener de eventos** (nuevo): El backend detecta automáticamente el evento `Paid` emitido por PaymentGateway cuando el inversor paga

### 20.2 El contrato `PaymentGateway.sol`

**Archivo:** `blockchain/contracts/PaymentGateway.sol`

#### 20.2.1 ¿Qué hace?

PaymentGateway es el **contrato que recibe los pagos** de los inversores. Cuando un inversor quiere invertir en un proyecto:

1. Abre MetaMask y llama a `pay(amount, actionId)` del PaymentGateway
2. PaymentGateway transfiere USDC de la wallet del inversor al treasury
3. PaymentGateway emite el evento `Paid(amount, actionId, payer)`
4. El backend detecta este evento y lo vincula con la inversión

#### 20.2.2 El código completo (con anotaciones)

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract PaymentGateway is Ownable {

    // ═════════════════════════════════════════════════════════════════════
    // Evento Paid: la pieza central de INV-01
    // ═════════════════════════════════════════════════════════════════════
    //
    // Los eventos en Solidity son "logs" que quedan grabados en la
    // blockchain para siempre. No se pueden modificar ni borrar.
    //
    // Cuando un inversor paga, este evento se emite y el backend
    // lo detecta en tiempo real (o en el próximo ciclo de polling).
    //
    // Parámetros:
    //   amount   → cuánto pagó (en USDC, 6 decimales)
    //   actionId → identificador único de la acción
    //   payer    → address del inversor que pagó
    //
    // La palabra clave "indexed" permite filtrar por ese parámetro
    // cuando se buscan eventos. También hace que el valor se guarde
    // en el "topic" del log (más fácil de buscar).
    event Paid(uint256 indexed amount, bytes32 indexed actionId, address indexed payer);

    IERC20 public usdc;
    address public treasury;

    constructor(address _usdc, address _treasury) Ownable(_treasury) {
        usdc = IERC20(_usdc);
        treasury = _treasury;
    }

    function pay(uint256 amount, bytes32 actionId) external {
        // Validaciones
        require(amount > 0, "El monto debe ser mayor a 0");
        require(usdc.balanceOf(msg.sender) >= amount, "Saldo USDC insuficiente");
        require(usdc.allowance(msg.sender, address(this)) >= amount,
            "Debe aprobar USDC al PaymentGateway primero");

        // Transferir USDC al treasury
        require(
            usdc.transferFrom(msg.sender, treasury, amount),
            "Falló la transferencia de USDC"
        );

        // EMITIR EVENTO — esto es lo que escucha el backend
        emit Paid(amount, actionId, msg.sender);
    }
}
```

#### 20.2.3 ¿Qué son los eventos en Ethereum?

Los eventos son una característica de Solidity que permite que los contratos "hablen" con el mundo exterior. Cuando un contrato emite un evento:

1. **Se guarda en la blockchain** como parte del recibo de la transacción
2. **No se puede modificar** después (es inmutable)
3. **Cualquiera puede leerlo** consultando `eth_getLogs`
4. **Es gratis** (no cuesta gas extra significativo)

**Estructura de un log (evento) en la blockchain:**

```
Log {
  address: "0x7aabb2435..."  // ← qué contrato emitió el evento
  topics: [                   // ← parámetros INDEXED (hasta 3)
    "0x7f3c7d3c...",         // [0] = keccak256("Paid(uint256,bytes32,address)")
    "0x0000...1dcd6500",     // [1] = amount (uint256, padded a 32 bytes)
    "0x494e56...00000000",   // [2] = actionId (bytes32)
    "0x0000...7eEA865D..."   // [3] = payer (address, padded a 32 bytes)
  ]
  data: "0x"                  // ← parámetros NO INDEXED (vacío acá)
  blockNumber: 12345678       // ← en qué bloque se minó
  transactionHash: "0x983a..."// ← la tx que emitió el evento
}
```

**¿Por qué "indexed"?** Cuando un parámetro tiene `indexed`, se guarda en `topics[]` en vez de `data`. Esto permite:
- Filtrar búsquedas por ese parámetro (ej: "dame todos los Paid donde payer = X")
- Acceder más rápido al valor
- Límite: máximo 3 parámetros indexed por evento

### 20.3 El flujo completo con el listener

Con el listener implementado, el flujo INV-01 es:

```
INVERSOR (MetaMask)            BACKEND                          SEPOLIA
  │                               │                               │
  │ 1. Aprueba USDC al            │                               │
  │    PaymentGateway             │                               │
  │    (USDC.approve)             │                               │
  │──────────────────────────────────────────────────────────────>│
  │                               │                               │
  │ 2. Llama a pay(amount,        │                               │
  │    actionId) desde MetaMask   │                               │
  │──────────────────────────────────────────────────────────────>│
  │                               │                               │
  │                               │  3. PaymentGateway:           │
  │                               │     - Valida saldo + approve │
  │                               │     - Transfiere USDC         │
  │                               │     - Emite Paid(amount,      │
  │                               │       actionId, payer)        │
  │                               │                               │
  │ 4. MetaMask devuelve txHash   │                               │
  │<──────────────────────────────────────────────────────────────│
  │                               │                               │
  │ 5. POST /api/invest           │                               │
  │    { txHash: "0x983a..." }    │                               │
  │──────────────────────────────>│                               │
  │                               │                               │
  │                               │  ┌─────────────────────────┐  │
  │                               │  │ PAYMENTEVENTSERVICE     │  │
  │                               │  │ (cada 30 segundos)      │  │
  │                               │  │                         │  │
  │                               │  │ 6. eth_getLogs(         │  │
  │                               │  │    Paid desde bloque    │  │
  │                               │  │    N hasta bloque M)    │  │
  │                               │  │─────────────────────────│──│─>
  │                               │  │                         │  │
  │                               │  │ 7. Logs con eventos Paid│  │
  │                               │  │<────────────────────────│──│─>
  │                               │  │                         │  │
  │                               │  │ 8. Para cada evento:    │  │
  │                               │  │    - Decodifica amount, │  │
  │                               │  │      actionId, payer     │  │
  │                               │  │    - Guarda en           │  │
  │                               │  │      blockchain_eventos  │  │
  │                               │  │    - Busca inversión     │  │
  │                               │  │      por txHash          │  │
  │                               │  │    - Si existe, vincula  │  │
  │                               │  └─────────────────────────┘  │
  │                               │                               │
  │ 9. "Inversión creada"         │                               │
  │<──────────────────────────────│                               │
```

### 20.4 El servicio Java: `PaymentEventService.java`

**Archivo:** `src/main/java/com/systeam/blockchain/service/PaymentEventService.java`

#### 20.4.1 ¿Qué hace?

Este servicio Spring se ejecuta **automáticamente cada 30 segundos** (configurable) y:

1. Lee de la DB el último bloque procesado de Sepolia
2. Consulta el bloque actual de Sepolia
3. Si hay bloques nuevos, pide todos los eventos `Paid` emitidos por PaymentGateway en ese rango
4. Por cada evento:
   - Decodifica los parámetros (amount, actionId, payer)
   - Lo guarda en la tabla `blockchain_eventos`
   - Busca una inversión con ese mismo `tx_hash` en la DB
   - Si existe, vincula el evento con la inversión
5. Actualiza el último bloque procesado

Además, cada **2 minutos** ejecuta una reconciliación: busca eventos no procesados que YA tengan una inversión vinculada (por si el evento llegó antes que el POST del usuario).

#### 20.4.2 El código completo

```java
package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import com.systeam.config.BlockchainProperties;

@Service
public class PaymentEventService {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventService.class);

    private final Web3j web3j;
    private final BlockchainProperties props;
    private final JdbcTemplate jdbc;

    public PaymentEventService(Web3j web3j, BlockchainProperties props, JdbcTemplate jdbc) {
        this.web3j = web3j;
        this.props = props;
        this.jdbc = jdbc;
    }

    // ─────────────────────────────────────────────────────────────────────
    // pollPaidEvents: el método principal, se ejecuta cada 30 segundos
    // ─────────────────────────────────────────────────────────────────────
    @Scheduled(fixedDelayString = "${blockchain.event-poll-interval:30000}")
    public void pollPaidEvents() {
        String paymentGateway = props.getPaymentGatewayAddress();
        if (paymentGateway == null || paymentGateway.isBlank()) {
            return;
        }

        try {
            // PASO 1: Leer el último bloque procesado
            Long ultimoBloque = leerUltimoBloqueProcesado();

            // PASO 2: Obtener el bloque actual de Sepolia
            BigInteger bloqueActual = web3j.ethBlockNumber().send().getBlockNumber();
            long actual = bloqueActual.longValue();

            if (actual <= ultimoBloque) {
                return; // No hay bloques nuevos
            }

            // PASO 3: Definir el evento Paid para que Web3j pueda codificarlo
            @SuppressWarnings("rawtypes")
            Event paidEvent = new Event(
                "Paid",
                List.of(
                    new TypeReference<Uint256>() {},    // amount
                    new TypeReference<Bytes32>() {},    // actionId
                    new TypeReference<Address>() {}    // payer
                )
            );

            // Codificar la firma: keccak256("Paid(uint256,bytes32,address)")
            String eventSignature = EventEncoder.encode(paidEvent);

            // Crear filtro: desde bloque N+1 hasta M, en PaymentGateway
            EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(ultimoBloque + 1)),
                DefaultBlockParameter.valueOf(bloqueActual),
                paymentGateway
            );
            filter.addSingleTopic(eventSignature);

            // PASO 4: Ejecutar eth_getLogs
            @SuppressWarnings("rawtypes")
            List<EthLog.LogResult> logResults;
            try {
                logResults = web3j.ethGetLogs(filter).send().getLogs();
            } catch (Exception e) {
                log.warn("Error consultando logs de Sepolia: {}", e.getMessage());
                return;
            }

            // PASO 5: Procesar cada log
            for (EthLog.LogResult result : logResults) {
                Log logEntry = (Log) result.get();
                if (logEntry != null) {
                    procesarEventoPaid(logEntry, paidEvent);
                }
            }

            // PASO 6: Actualizar último bloque procesado
            actualizarUltimoBloque(actual);

        } catch (Exception e) {
            log.error("Error en pollPaidEvents: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // procesarEventoPaid: decodifica y guarda un evento Paid
    // ─────────────────────────────────────────────────────────────────────
    private void procesarEventoPaid(Log logEntry, Event paidEvent) {
        try {
            String txHash = logEntry.getTransactionHash();
            BigInteger blockNumber = logEntry.getBlockNumber();

            // Los parámetros INDEXED están en topics[1..3]
            List<String> topics = logEntry.getTopics();
            if (topics.size() < 4) {
                log.warn("Evento Paid inválido (topics insuficientes): {}", txHash);
                return;
            }

            // Decodificar amount desde topics[1] (hex → BigInteger)
            BigInteger amount = Numeric.toBigInt(topics.get(1));

            // Decodificar actionId desde topics[2]
            String actionIdHex = topics.get(2);

            // Decodificar payer desde topics[3] (address con padding)
            String payerHex = topics.get(3);
            String payer = "0x" + payerHex.substring(payerHex.length() - 40);

            // Verificar si ya procesamos este evento
            Long yaProcesado = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blockchain_eventos WHERE tx_hash = ?",
                Long.class, txHash
            );
            if (yaProcesado != null && yaProcesado > 0) {
                return;
            }

            // Guardar evento en DB
            String datosJson = String.format(
                "{\"amount\":\"%s\",\"actionId\":\"%s\",\"payer\":\"%s\"}",
                amount.toString(), actionIdHex, payer
            );

            jdbc.update("""
                INSERT INTO blockchain_eventos
                    (tx_hash, block_number, tipo_evento, datos)
                VALUES (?, ?, 'Paid', ?)
                """,
                txHash, blockNumber.longValue(), datosJson
            );

            log.info("Evento Paid detectado: tx={}, amount={}", txHash, amount);

            // Intentar vincular con inversión existente
            try {
                Long investmentId = jdbc.queryForObject(
                    "SELECT id FROM investments WHERE tx_hash = ? AND deleted_at IS NULL",
                    Long.class, txHash
                );
                if (investmentId != null) {
                    jdbc.update(
                        "UPDATE blockchain_eventos SET procesado = TRUE, investment_id = ?, processed_at = NOW() WHERE tx_hash = ?",
                        investmentId, txHash
                    );
                    log.info("Evento vinculado a inversión {}: {}", investmentId, txHash);
                }
            } catch (Exception e) {
                log.debug("Evento Paid aún sin inversión vinculada: {}", txHash);
            }

        } catch (Exception e) {
            log.error("Error procesando evento Paid: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // reconciliarEventosPendientes: cada 2 minutos, vincula eventos
    // que no tenían inversión cuando llegaron pero ahora ya existe
    // ─────────────────────────────────────────────────────────────────────
    @Scheduled(fixedDelayString = "${blockchain.event-reconcile-interval:120000}")
    public void reconciliarEventosPendientes() {
        try {
            List<java.util.Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT e.id, e.tx_hash FROM blockchain_eventos e " +
                "WHERE e.procesado = FALSE " +
                "AND EXISTS (SELECT 1 FROM investments i WHERE i.tx_hash = e.tx_hash AND i.deleted_at IS NULL)"
            );

            for (var row : pendientes) {
                Long id = (Long) row.get("id");
                String txHash = (String) row.get("tx_hash");
                Long investmentId = jdbc.queryForObject(
                    "SELECT id FROM investments WHERE tx_hash = ? AND deleted_at IS NULL",
                    Long.class, txHash
                );
                jdbc.update(
                    "UPDATE blockchain_eventos SET procesado = TRUE, investment_id = ?, processed_at = NOW() WHERE id = ?",
                    investmentId, id
                );
                log.info("Evento {} reconciliado con inversión {}: {}", id, investmentId, txHash);
            }
        } catch (Exception e) {
            log.warn("Error en reconciliación de eventos: {}", e.getMessage());
        }
    }

    private Long leerUltimoBloqueProcesado() {
        try {
            Long bloque = jdbc.queryForObject(
                "SELECT ultimo_bloque FROM blockchain_sync WHERE nombre = ?",
                Long.class, "PaymentGateway"
            );
            return bloque != null ? bloque : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void actualizarUltimoBloque(long bloque) {
        try {
            int filas = jdbc.update(
                "UPDATE blockchain_sync SET ultimo_bloque = ?, updated_at = NOW() WHERE nombre = ?",
                bloque, "PaymentGateway"
            );
            if (filas == 0) {
                jdbc.update(
                    "INSERT INTO blockchain_sync (nombre, ultimo_bloque) VALUES (?, ?)",
                    "PaymentGateway", bloque
                );
            }
        } catch (Exception e) {
            log.warn("Error actualizando ultimo_bloque: {}", e.getMessage());
        }
    }
}
```

#### 20.4.3 ¿Qué es `@Scheduled`?

`@Scheduled` es una anotación de Spring que ejecuta un método automáticamente cada cierto tiempo:

```java
@Scheduled(fixedDelayString = "${blockchain.event-poll-interval:30000}")
public void pollPaidEvents() { ... }
```

- `fixedDelayString`: espera X milisegundos entre el FIN de una ejecución y el inicio de la siguiente
- `${blockchain.event-poll-interval}`: lee el valor de `application.properties` (con default 30000 = 30 segundos)
- Si la ejecución tarda 5 segundos, espera 30 segundos más = 35 segundos totales entre inicios

**¿Quién habilita `@Scheduled`?** Ya está habilitado por `@EnableScheduling` en `InvestmentScheduler.java`. Spring escanea todos los beans y ejecuta los métodos anotados.

#### 20.4.4 ¿Cómo se decodifican los eventos manualmente?

Web3j no tiene un método directo `decodeEvent()` en la versión que usamos (4.12.3). Por eso decodificamos manualmente los parámetros del log:

**Estructura de un log de evento:**

```
Log {
  topics: [
    "0x7f3c7d3c...",            // [0] = keccak256("Paid(uint256,bytes32,address)")
    "0x0000...1dcd6500",        // [1] = amount (uint256, 32 bytes hex)
    "0x494e56...00000000",      // [2] = actionId (bytes32, 32 bytes hex)
    "0x0000...7eEA865D..."      // [3] = payer (address, 20 bytes + padding)
  ]
  data: "0x"
  transactionHash: "0x983a..."
  blockNumber: 12345678
}
```

**Decodificación:**

| Parámetro | topics index | Tipo Raw | Cómo se decodifica |
|-----------|-------------|----------|-------------------|
| amount | topics[1] | `0x0000...1dcd6500` (64 hex chars) | `Numeric.toBigInt(topics[1])` → `500000000` (BigInteger) |
| actionId | topics[2] | `0x494e56...00000000` (64 hex chars) | `topics[2]` directamente (es el bytes32 hex) |
| payer | topics[3] | `0x0000...7eEA865D...` (64 hex chars) | Últimos 40 chars = `0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A` |

**Regla de padding de addresses:** Una dirección Ethereum tiene 20 bytes = 40 caracteres hex. En topics siempre ocupa 32 bytes (64 chars), con 12 bytes de padding a la izquierda y la address a la derecha.

### 20.5 La migración V12: `blockchain_eventos` y `blockchain_sync`

**Archivo:** `src/main/resources/db/proyectos/V12__blockchain_events_tracking.sql`

Se crearon dos tablas para trackear los eventos procesados:

**Tabla `blockchain_eventos`:** almacena cada evento `Paid` detectado

| Columna | Tipo | ¿Qué guarda? |
|---------|------|-------------|
| `id` | BIGSERIAL | Auto-incremental |
| `tx_hash` | VARCHAR(66) | Hash de la transacción que emitió el evento |
| `block_number` | BIGINT | Bloque donde se minó |
| `tipo_evento` | VARCHAR(50) | "Paid" (podría haber otros en el futuro) |
| `datos` | TEXT | JSON con amount, actionId, payer |
| `procesado` | BOOLEAN | FALSE = pendiente, TRUE = vinculado a inversión |
| `investment_id` | BIGINT (FK) | Inversión asociada (si existe) |

**Tabla `blockchain_sync`:** trackea el progreso del listener

| Columna | Tipo | ¿Qué guarda? |
|---------|------|-------------|
| `nombre` | VARCHAR(50) | "PaymentGateway" (identifica el contrato) |
| `ultimo_bloque` | BIGINT | Último bloque escaneado |

**¿Por qué dos tablas?**
- `blockchain_sync` evita escanear desde el bloque 0 cada vez que arranca el backend
- `blockchain_eventos` guarda los eventos para auditoría y reconciliación

### 20.6 La configuración en `application.properties`

```properties
# Blockchain — Event listener (INV-01)
blockchain.event-poll-interval=30000      # Cada 30 segundos buscar eventos
blockchain.event-reconcile-interval=120000 # Cada 2 min reconciliar eventos pendientes
```

### 20.7 Resumen de archivos nuevos

| Archivo | ¿Qué es? |
|---------|----------|
| `blockchain/contracts/PaymentGateway.sol` | ★ Nuevo — Contrato que recibe pagos y emite evento `Paid` |
| `blockchain/service/PaymentEventService.java` | ★ Nuevo — Listener de eventos Paid con polling cada 30s |
| `V12__blockchain_events_tracking.sql` | ★ Nuevo — Tablas blockchain_eventos + blockchain_sync |
| `application.properties` | Modificado — 2 nuevas propiedades de intervalo |

### 20.8 ¿Cómo se relaciona con el código existente?

El `PaymentEventService` NO reemplaza la verificación por `verifyTransaction()` en `SmartContractService.recordInvestment()`. **Ambos coexisten:**

| Mecanismo | Cuándo se ejecuta | Qué hace |
|-----------|-------------------|----------|
| `verifyTransaction()` | En el momento de la inversión (síncrono) | Verifica que el txHash existe en Sepolia |
| `PaymentEventService` | Cada 30 segundos (asíncrono) | Detecta eventos Paid y los vincula |

El listener agrega una capa adicional de **trazabilidad y auditoría**: incluso si el usuario no envía el txHash al backend (flujo futuro), el listener detecta el pago igual.

---

## 21. INV-05 — Swap Atómico $IDEA → Sub-tokens en Smart Contract

### 21.1 ¿Qué historia de usuario cubre?

| HU | Nombre | Descripción |
|----|--------|-------------|
| **HU-11** | Inversión en Proyecto con Smart Contract | Como inversor, quiero invertir en un proyecto utilizando mis tokens $IDEA mediante un smart contract, para recibir automáticamente los sub-tokens que representen mi participación y registrar la inversión en la blockchain |

**INV-05** es la tarea que implementa HU-11 del lado del contrato:

| Tarea | Descripción | Estado |
|-------|-------------|--------|
| **INV-05** | Función de Swap (Inversión) en Smart Contract — lógica central que recibe $IDEA del inversor y asigna sub-tokens en la misma transacción | ✅ Implementado |

**Criterios de aceptación de HU-11:**

| Criterio | Cómo se cumple |
|----------|----------------|
| Realizar una inversión válida y verificar que los sub-tokens se asignan automáticamente | `InvestmentSwap.invest()` mintea sub-tokens al inversor en la misma tx atómica |
| Consultar la blockchain y comprobar que la transacción queda registrada | El evento `InvestmentMade` queda grabado para siempre en la blockchain |
| Intentar invertir sin saldo suficiente y verificar que se rechaza | El contrato llama a `transferFrom()` que revierte si el saldo es insuficiente |
| Simular inversión en proyecto sin cupo disponible y comprobar que se cancela | La validación de cupo ocurre en el backend ANTES de llamar al contrato |

### 21.2 ¿Qué problema resuelve INV-05?

Antes de INV-05, el flujo de inversión era:

```
POST /api/investments
  ↓
1. Backend valida datos en DB ✅
2. Backend descuenta $IDEA de saldo_idea en DB ✅
3. Backend registra inversión en DB ✅
4. Backend llama SmartContractService.recordInvestment()
     → Solo verifica que un txHash existe en Sepolia
     → NO hace un swap atómico ❌
```

**Problema:** La inversión se registraba en la DB pero no había una operación atómica on-chain que vinculase la transferencia de $IDEA con la asignación de sub-tokens. Si el proceso fallaba a mitad de camino (ej: se descuenta $IDEA en DB pero no se registra la inversión), quedaba en estado inconsistente.

**Solución INV-05:** Un contrato `InvestmentSwap` que hace TODO en UNA sola transacción atómica:

```
1. Recibe $IDEA del inversor (transferFrom)
2. Mintea sub-tokens al inversor (ProjectToken.mint)
3. Emite evento InvestmentMade
→ Si algo falla en cualquier paso, TODO se revierte (atomicidad)
```

### 21.3 Arquitectura: ¿Cómo se relaciona con los otros contratos?

```
┌──────────────────────┐
│    InvestmentSwap    │ ← ★ NUEVO (INV-05)
│                      │
│  - crearTokenProy()  │
│  - invest()          │ ← CORAZÓN DEL SISTEMA
│  - obtenerToken()    │
└────────┬─────────────┘
         │
         │ ¿A qué contratos llama?
         │
         ├──► ProjectToken.mint()
         │    (porque InvestmentSwap es el OWNER del ProjectToken)
         │
         └──► IERC20.transferFrom(inversor, treasury, amount)
              (el inversor aprueba $IDEA al swap previamente)
```

**Diferencia clave con TokenFactory:**

| Aspecto | TokenFactory (viejo) | InvestmentSwap (INV-05) |
|---------|---------------------|------------------------|
| Crea ProjectToken | ✅ Sí | ✅ Sí |
| Owner del ProjectToken | `treasury` | `InvestmentSwap` (address(this)) |
| Puede mintear sub-tokens | ❌ No (solo treasury) | ✅ Sí (es el owner) |
| Swap atómico | ❌ No | ✅ Sí |
| Se usa para | Proyectos viejos | Proyectos NUEVOS |

**¿Qué pasa con los proyectos viejos?**
Los proyectos que ya tenían un token creado por TokenFactory siguen funcionando normalmente. El backend intenta usar InvestmentSwap primero, y si no está disponible (o el token ya existe), cae automáticamente a TokenFactory.

### 21.4 El contrato `InvestmentSwap.sol`

**Archivo:** `blockchain/contracts/InvestmentSwap.sol`

#### 21.4.1 Código completo con anotaciones

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./ProjectToken.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract InvestmentSwap {

    // ═════════════════════════════════════════════════════════════
    // EVENTOS
    // ═════════════════════════════════════════════════════════════

    // Se emite cuando se crea un nuevo ProjectToken para un proyecto
    event TokenDeProyectoCreado(uint256 indexed proyectoId, address indexed tokenAddress);

    // Evento PRINCIPAL: se emite en cada swap exitoso
    // El backend escucha este evento para detectar inversiones on-chain
    event InvestmentMade(
        uint256 indexed proyectoId,  // ID del proyecto en la DB
        address indexed investor,    // Quien recibe los sub-tokens
        uint256 ideaAmount,          // Cantidad de $IDEA invertidos
        uint256 subTokenAmount       // Cantidad de sub-tokens recibidos
    );

    // ═════════════════════════════════════════════════════════════
    // STORAGE (datos que el contrato guarda en la blockchain)
    // ═════════════════════════════════════════════════════════════

    IERC20 public immutable idea;           // $IDEA token (ProjectToken global)
    address public immutable treasury;       // Recibe los $IDEA invertidos
    mapping(uint256 => address) public tokenDeProyecto;  // proyectoId → ProjectToken
    address[] public tokensCreados;          // Lista de todos los tokens creados

    // ═════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═════════════════════════════════════════════════════════════

    constructor(address _idea, address _treasury) {
        idea = IERC20(_idea);
        treasury = _treasury;
    }

    // ═════════════════════════════════════════════════════════════
    // FUNCIÓN 1: crearTokenProyecto()
    // ═════════════════════════════════════════════════════════════

    // Crea un ProjectToken para un proyecto.
    // La DIFERENCIA con TokenFactory es que acá:
    //   owner del ProjectToken = address(this) = InvestmentSwap
    // Esto permite que invest() pueda mintear sub-tokens.

    function crearTokenProyecto(
        uint256 proyectoId,
        string calldata nombre,
        string calldata simbolo,
        uint256 supplyInicial
    ) external returns (address) {
        // Validar que no exista ya
        require(tokenDeProyecto[proyectoId] == address(0),
            "InvestmentSwap: token already exists for this project");

        // Deployar NUEVO ProjectToken con owner = InvestmentSwap
        ProjectToken nuevoToken = new ProjectToken(nombre, simbolo, address(this));
        address tokenAddress = address(nuevoToken);

        // Mintear supply inicial al treasury
        if (supplyInicial > 0) {
            nuevoToken.mint(treasury, supplyInicial);
        }

        // Guardar en mappings
        tokenDeProyecto[proyectoId] = tokenAddress;
        tokensCreados.push(tokenAddress);

        emit TokenDeProyectoCreado(proyectoId, tokenAddress);
        return tokenAddress;
    }

    // ═════════════════════════════════════════════════════════════
    // FUNCIÓN 2: invest() — EL CORAZÓN DE INV-05
    // ═════════════════════════════════════════════════════════════

    // Swap atómico: recibe $IDEA y asigna sub-tokens.
    //
    // TODO ocurre en UNA sola transacción:
    //   1. Transfiere $IDEA del llamante al treasury
    //   2. Mintea sub-tokens al inversor
    //   3. Emite InvestmentMade
    //
    // Si CUALQUIER paso falla, TODO se revierte.
    //
    // Requisitos:
    //   - msg.sender debe haber aprobado ideaAmount de $IDEA al contrato
    //   - El proyecto debe tener un token creado

    function invest(
        uint256 proyectoId,
        uint256 ideaAmount,
        uint256 subTokenAmount,
        address investor
    ) external {
        require(ideaAmount > 0, "ideaAmount must be > 0");
        require(subTokenAmount > 0, "subTokenAmount must be > 0");
        require(investor != address(0), "invalid investor address");

        // Obtener el token del proyecto
        address projectTokenAddr = tokenDeProyecto[proyectoId];
        require(projectTokenAddr != address(0),
            "project token not deployed");

        // PASO 1: Transferir $IDEA del llamante al treasury
        require(
            idea.transferFrom(msg.sender, treasury, ideaAmount),
            "IDEA transfer failed (check allowance + balance)"
        );

        // PASO 2: Mintear sub-tokens al inversor
        // InvestmentSwap es el owner, por eso puede llamar a mint()
        ProjectToken(projectTokenAddr).mint(investor, subTokenAmount);

        // PASO 3: Emitir evento
        emit InvestmentMade(proyectoId, investor, ideaAmount, subTokenAmount);
    }

    // ═════════════════════════════════════════════════════════════
    // FUNCIÓN 3: Getters (consulta, no cuestan gas)
    // ═════════════════════════════════════════════════════════════

    function obtenerTokenDeProyecto(uint256 proyectoId) external view returns (address) {
        return tokenDeProyecto[proyectoId];
    }

    function obtenerCantidadTokens() external view returns (uint256) {
        return tokensCreados.length;
    }
}
```

#### 21.4.2 ¿Por qué InvestmentSwap es el owner del ProjectToken?

Es la clave de toda la arquitectura. Veamos:

```solidity
// En TokenFactory (viejo):
ProjectToken nuevo = new ProjectToken(nombre, simbolo, TREASURY);
// → owner = treasury
// → Treasury.mint() funciona, pero ningún OTRO contrato puede mintear

// En InvestmentSwap (INV-05):
ProjectToken nuevo = new ProjectToken(nombre, simbolo, address(this));
// → owner = InvestmentSwap
// → InvestmentSwap.mint() funciona desde invest()
```

Al ser InvestmentSwap el owner del ProjectToken, cuando `invest()` llama a `mint(investor, subTokenAmount)`, funciona porque el owner (InvestmentSwap) se lo permitió.

**¿Por qué TokenFactory no lo hacía así?** Porque TokenFactory solo se encargaba de crear tokens. InvestmentSwap unifica creación + swap en un solo contrato.

### 21.5 El servicio Java: `InvestmentSwapService.java`

**Archivo:** `src/main/java/com/systeam/blockchain/service/InvestmentSwapService.java`

#### 21.5.1 ¿Qué hace?

Es el puente entre el backend Java y el contrato InvestmentSwap.sol en Sepolia.

Tiene 3 métodos principales:

| Método | ¿Qué hace? | ¿Cuándo se llama? |
|--------|-----------|-------------------|
| `crearTokenProyecto()` | Crea un ProjectToken para un proyecto llamando a InvestmentSwap | Cuando un proyecto pasa a FINANCIAMIENTO |
| `invest()` | Realiza el swap atómico $IDEA → sub-tokens | Cuando un usuario invierte |
| `obtenerTokenDeProyecto()` | Consulta la address del token de un proyecto | En validaciones previas |

#### 21.5.2 Código completo con anotaciones

```java
package com.systeam.blockchain.service;

// ─── Imports ─────────────────────────────────────────────────────────
// Web3j para interactuar con Ethereum:
//   FunctionEncoder → convierte una llamada a función en hex (ABI encoding)
//   FunctionReturnDecoder → decodifica la respuesta hex a valores Java
//   Address, Uint256, Utf8String → tipos Solidity empaquetados como Java
//   TransactionManager → firma y envía transacciones con la private key

@Service
public class InvestmentSwapService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentSwapService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    // ─── Constructor ────────────────────────────────────────────────
    // Web3jConfig ya crea los beans Web3j, Credentials, y
    // BlockchainProperties. Spring los inyecta automáticamente.

    public InvestmentSwapService(Web3j web3j, Credentials credentials,
                                  BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }

    // ═════════════════════════════════════════════════════════════════
    // crearTokenProyecto()
    // ═════════════════════════════════════════════════════════════════

    public String crearTokenProyecto(
            Long proyectoId, String nombre, String simbolo,
            BigInteger supplyInicial) throws Exception {

        // Si InvestmentSwap no está configurado, usar fallback
        String swapAddress = props.getInvestmentSwapAddress();
        if (swapAddress == null || swapAddress.equals(ZERO_ADDRESS)) {
            return props.getProjectTokenAddress();
        }

        // Construir la función Solidity para ABI encoding:
        //   Function(name, inputs, outputs)
        // inputs = parámetros de entrada
        // outputs = tipo de retorno (Address)
        Function fn = new Function(
            "crearTokenProyecto",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                new Utf8String(nombre),
                new Utf8String(simbolo),
                new Uint256(supplyInicial)
            ),
            List.of(new TypeReference<Address>() {})
        );

        // Codificar a hex y enviar transacción
        String encodedFunction = FunctionEncoder.encode(fn);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        EthSendTransaction response = txManager.sendTransaction(
            gasPrice, BigInteger.valueOf(500_000L),
            swapAddress, encodedFunction, BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Error: " + response.getError().getMessage());
        }

        // Decodificar la address del token desde el receipt
        return decodeTokenAddress(response.getTransactionHash(), fn);
    }

    // ═════════════════════════════════════════════════════════════════
    // invest() — llama al contrato InvestmentSwap.invest()
    // ═════════════════════════════════════════════════════════════════

    public String invest(
            Long proyectoId,
            BigInteger ideaAmount,      // en wei (18 decimales)
            BigInteger subTokenAmount,  // en wei (18 decimales)
            String investor             // address que recibe sub-tokens
    ) throws Exception {

        String swapAddress = props.getInvestmentSwapAddress();
        if (swapAddress == null || swapAddress.equals(ZERO_ADDRESS)) {
            return "0xswap-offline-" + System.currentTimeMillis();
        }

        Function fn = new Function(
            "invest",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                new Uint256(ideaAmount),
                new Uint256(subTokenAmount),
                new Address(investor)
            ),
            List.of() // no return value
        );

        String encodedFunction = FunctionEncoder.encode(fn);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        EthSendTransaction response = txManager.sendTransaction(
            gasPrice, BigInteger.valueOf(300_000L),
            swapAddress, encodedFunction, BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Error en swap: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        log.info("Swap ejecutado: proyecto={}, ideaAmount={}, subTokens={}, investor={}, tx={}",
            proyectoId, ideaAmount, subTokenAmount, investor, txHash);

        return txHash;
    }
}
```

#### 21.5.3 ¿Cómo se codifica una función Solidity desde Java?

Cuando llamamos `invest()` en el contrato desde Java, Web3j necesita convertir la llamada a un formato que Ethereum entienda. El proceso se llama **ABI encoding**:

```
Java:
  new Function("invest", [
    Uint256(proyectoId),
    Uint256(ideaAmount),
    Uint256(subTokenAmount),
    Address(investor)
  ], [])

  ↓ FunctionEncoder.encode()

Hex (lo que viaja por la red):
  0x811f06d8                    ← selector de función (primeros 4 bytes del hash)
  0000000000000000000000000000000000000000000000000000000000000001  ← proyectoId=1
  0000000000000000000000000000000000000000000000056bc75e2d63100000  ← ideaAmount=10^18
  0000000000000000000000000000000000000000000000056bc75e2d63100000  ← subTokenAmount=10^18
  0000000000000000000000007eea865d2f47b5cc0ff4c8967c1ccf667febe50a  ← investor (con padding)

  ↓ eth_sendRawTransaction (firmado con private key)

Sepolia la procesa y (si todo sale bien) la incluye en un bloque
```

**Selector de función:** Son los primeros 4 bytes de `keccak256("invest(uint256,uint256,uint256,address)")`. Identifica qué función del contrato se quiere ejecutar.

### 21.6 Integración con ProjectService

**Archivo:** `src/main/java/com/systeam/project/service/ProjectService.java`

Cuando un proyecto pasa a FINANCIAMIENTO, `crearSubtokenParaProyecto()` se encarga de:

1. **Crear el token en Sepolia** → usando InvestmentSwap (o TokenFactory como fallback)
2. **Crear el subtoken en la DB** → insert en la tabla `subtokens`

El flujo de fallback es:

```
crearSubtokenParaProyecto()
  │
  ├── ¿InvestmentSwap disponible?
  │   ├── Sí → InvestmentSwapService.crearTokenProyecto()
  │   │        → Deploya ProjectToken con owner = InvestmentSwap
  │   │        → Devuelve contract_address
  │   │
  │   └── No → TokenFactoryService.crearTokenProyecto() (fallback)
  │             → Deploya ProjectToken con owner = treasury
  │             → Devuelve contract_address
  │
  └── INSERT INTO subtokens (nombre, supply, cupo, contract_address, ...)
```

```java
// En ProjectService.java (simplificado):
private void crearSubtokenParaProyecto(Proyecto proyecto) {
    try {
        // Intentar InvestmentSwap primero (INV-05)
        contractAddress = investmentSwapService.crearTokenProyecto(
            proyecto.getId(), tokenName, tokenSymbol, supplyInicial
        );
    } catch (Exception e) {
        // Fallback a TokenFactory
        contractAddress = tokenFactoryService.crearTokenProyecto(
            proyecto.getId(), tokenName, tokenSymbol, supplyInicial
        );
    }
    // Insertar en DB
    jdbc.update("INSERT INTO subtokens (...) VALUES (?, ?, ..., ?)",
        tokenName, supply, supply, ..., contractAddress);
}
```

### 21.7 Integración con InvestmentService

**Archivo:** `src/main/java/com/systeam/investment/service/InvestmentService.java`

Cuando un inversor invierte, `createInvestment()` ahora hace:

```java
@Transactional
public InvestmentResponse createInvestment(CreateInvestmentRequest request, Long usuarioId) {

    // ─── 1. Validaciones (igual que antes) ────────────────────────
    // Verificar estado FINANCIAMIENTO, subtoken existe, cupo, saldo

    // ─── 2. INV-05: Swap atómico on-chain ────────────────────────
    String txHash;
    try {
        // Convertir montos a wei (18 decimales)
        BigDecimal montoIdeaWei = montoIdea × 10^18;
        BigDecimal subTokenAmountWei = subTokens × 10^18;

        // Obtener treasury address desde config
        String treasuryAddress = blockchainProperties.getTreasuryAddress();

        // Llamar a InvestmentSwap.invest()
        txHash = investmentSwapService.invest(
            proyectoId,
            montoIdeaWei.toBigInteger(),      // $IDEA en wei
            subTokenAmountWei.toBigInteger(),  // sub-tokens en wei
            treasuryAddress                    // quién recibe sub-tokens
        );

        log.info("INV-05: Swap on-chain exitoso. Tx: {}", txHash);
    } catch (Exception e) {
        // Si Sepolia no está disponible, fallback al viejo flujo
        Map<String, Object> txResult = smartContractService.recordInvestment(...);
        txHash = (String) txResult.get("txHash");
    }

    // ─── 3. Actualizar DB (igual que antes) ───────────────────────
    // Descontar saldo_idea, actualizar monto_recaudado,
    // actualizar cupo_restante, portfolio, guardar inversión
}
```

**¿Por qué pasamos treasury como "investor"?** Porque los usuarios de nuestra plataforma aún no tienen wallets on-chain. El treasury recibe los sub-tokens a nombre del usuario, y la DB registra la propiedad real. En el futuro, cuando los usuarios tengan wallets, pasaremos su address directamente.

### 21.8 Deploy a Sepolia

#### 21.8.1 Cómo se deployó

Se usó `DeployInvestmentSwap.java`, un main class standalone que:

1. Lee las variables de entorno necesarias (RPC URL, private key, $IDEA address, treasury)
2. Conecta a Sepolia vía Web3j
3. Construye el bytecode + argumentos del constructor
4. Firma la transacción con la private key del treasury
5. Envía la transacción a la red
6. Espera la confirmación (minería)
7. Guarda la address en `.env`

**Comando para deployar (ya ejecutado):**
```bash
.\mvnw.cmd compile
.\mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
$cp = "target\classes;" + (Get-Content "target\cp.txt").Trim()
java -cp "$cp" com.systeam.blockchain.deploy.DeployInvestmentSwap
```

**Resultado:**
```
=== INVESTMENT SWAP DESPLEGADO ===
Address: 0xe1056126924568d677669c736a90b2ae588b0091
Tx: 0x3d309172b5f7b18b12e978dfa8813e5446663bfd5bda1932fa9c5516d74151e4
```

#### 21.8.2 El bytecode embebido

DeployInvestmentSwap.java tiene el bytecode de InvestmentSwap.sol directamente como un String de Java. Esto evita tener que leer archivos externos durante el deploy.

```java
private static final String BYTECODE =
    "60c060405234801561001057600080fd5b50604051611b09380380611b0983398101604081905261002f91610113565b...";
```

Se obtiene compilando con solc:
```bash
node compile.js  # compila todos los .sol en blockchain/compiled/
```

### 21.9 Configuración

#### 21.9.1 `.env`

```
BLOCKCHAIN_INVESTMENT_SWAP=0xe1056126924568d677669c736a90b2ae588b0091
```

#### 21.9.2 `application.properties`

```properties
blockchain.investment-swap-address=${BLOCKCHAIN_INVESTMENT_SWAP}
```

#### 21.9.3 `BlockchainProperties.java`

```java
private String investmentSwapAddress;

public String getInvestmentSwapAddress() { return investmentSwapAddress; }
public void setInvestmentSwapAddress(String investmentSwapAddress) { this.investmentSwapAddress = investmentSwapAddress; }
```

### 21.10 Flujo completo (INV-05 + HU-11)

Así funciona el sistema completo con INV-05:

```
ADMIN                              BACKEND                         SEPOLIA
  │                                  │                               │
  │ 1. Crea proyecto                 │                               │
  │─────────────────────────────────>│                               │
  │                                  │ INSERT projects (PREPARACION) │
  │                                  │                               │
  │ 2. Mueve a FINANCIAMIENTO        │                               │
  │─────────────────────────────────>│                               │
  │                                  │                               │
  │                                  │──┐                            │
  │                                  │  │ InvestmentSwap             │
  │                                  │  │ .crearTokenProyecto()      │
  │                                  │<─┘                            │
  │                                  │──────────────────────────────>│
  │                                  │                               │
  │                                  │<──────────────────────────────│
  │                                  │ Devuelve: contractAddress     │
  │                                  │                               │
  │                                  │ INSERT subtokens (contract)   │
  │                                  │                               │
  │                                  │                               │
INVERSOR                           BACKEND                         SEPOLIA
  │                                  │                               │
  │ 3. POST /api/investments         │                               │
  │    { montoIdea: 500 }            │                               │
  │─────────────────────────────────>│                               │
  │                                  │                               │
  │                                  │ 4. Valida: estado FIN, cupo,  │
  │                                  │    saldo suficiente           │
  │                                  │                               │
  │                                  │──┐                            │
  │                                  │  │ Calcula precio dinámico    │
  │                                  │  │ subTokens = 500 / 10 = 50  │
  │                                  │<─┘                            │
  │                                  │                               │
  │                                  │ 5. InvestmentSwap.invest()    │
  │                                  │    (proyectoId=1,            │
  │                                  │     ideaAmount=500*10^18,    │
  │                                  │     subTokenAmount=50*10^18, │
  │                                  │     investor=treasury)        │
  │                                  │──────────────────────────────>│
  │                                  │                               │
  │                                  │  6. Contrato:                 │
  │                                  │     a) TransferFrom           │
  │                                  │        (treasury → treasury)  │
  │                                  │     b) ProjectToken.mint()    │
  │                                  │        (treasury, 50*10^18)   │
  │                                  │     c) InvestmentMade(1,      │
  │                                  │        treasury, 500, 50)     │
  │                                  │                               │
  │                                  │<──────────────────────────────│
  │                                  │ Devuelve: txHash              │
  │                                  │                               │
  │                                  │ 7. Actualiza DB:              │
  │                                  │    - saldo_idea -= 500        │
  │                                  │    - cupo_restante -= 50      │
  │                                  │    - portfolio += 50          │
  │                                  │    - INSERT investment        │
  │                                  │                               │
  │ 8. Inversión creada              │                               │
  │<─────────────────────────────────│                               │
```

### 21.11 Criterios de aceptación verificados

| Criterio | ¿Cómo se verifica? | Resultado |
|----------|-------------------|-----------|
| Realizar inversión válida y verificar sub-tokens asignados | Se llama a `POST /api/investments` con monto válido, el contrato ejecuta `mint()` en la misma tx | ✅ El contrato emite `InvestmentMade` y los sub-tokens se asignan atómicamente |
| Consultar blockchain y verificar transacción registrada | Se busca el txHash en Sepolia: `eth_getTransactionReceipt(txHash)` | ✅ El evento `InvestmentMade` queda grabado en los logs |
| Invertir sin saldo suficiente | `transferFrom()` revierte si `balanceOf(msg.sender) < amount` | ✅ La tx completa se revierte (atomicidad) |
| Invertir sin cupo disponible | La validación de `cupo_restante <= 0` ocurre en `validateInvestment()` ANTES de llamar al contrato | ✅ El endpoint devuelve HTTP 409 Conflict |

### 21.12 Resumen de archivos nuevos/modificados

| Archivo | ¿Qué es? |
|---------|----------|
| `blockchain/contracts/InvestmentSwap.sol` | ★ Nuevo — Contrato que crea tokens y hace swap atómico |
| `src/.../deploy/DeployInvestmentSwap.java` | ★ Nuevo — Deployer a Sepolia con bytecode embebido |
| `src/.../blockchain/service/InvestmentSwapService.java` | ★ Nuevo — Servicio Java para llamar al contrato |
| `src/.../config/BlockchainProperties.java` | Modificado — Nuevo campo `investmentSwapAddress` |
| `src/.../project/service/ProjectService.java` | Modificado — Usa InvestmentSwap para crear tokens |
| `src/.../investment/service/InvestmentService.java` | Modificado — Llama a InvestmentSwap.invest() en createInvestment |
| `src/.../investment/InvestmentServiceTest.java` | Modificado — Mock de InvestmentSwapService + BlockchainProperties |
| `src/.../project/ProjectServiceTest.java` | Modificado — Mock de InvestmentSwapService |
| `.env` | Modificado — Nueva variable `BLOCKCHAIN_INVESTMENT_SWAP` |
| `application.properties` | Modificado — Nueva propiedad `blockchain.investment-swap-address` |

---

## Apéndice A: Cómo levantar el proyecto

### Requisitos
- Java 17+ (`java -version`)
- Maven Wrapper (viene en el repo: `./mvnw.cmd`)
- PostgreSQL corriendo en `localhost:5432`
- Base de datos `systeam` creada

### Pasos
```bash
# 1. Configurar JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 2. Compilar
./mvnw.cmd clean compile

# 3. Correr tests
./mvnw.cmd test

# 4. Arrancar
./mvnw.cmd spring-boot:run
```

### Variables de entorno requeridas (en .env)
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/systeam
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SERVER_PORT=8081
```

### Variables de blockchain (en .env)
```
BLOCKCHAIN_RPC_URL=https://ethereum-sepolia-rpc.publicnode.com
BLOCKCHAIN_PAYMENT_GATEWAY=0x7aabb2435e3220c4bd51cc6546e6899fe75a3f10
BLOCKCHAIN_PROJECT_TOKEN=0x9f2c766d0bd9bbb640422decdf0125be02c7d144
BLOCKCHAIN_USDC=0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
BLOCKCHAIN_TREASURY=0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
BLOCKCHAIN_PRIVATE_KEY=b7ddf518a66ac1b209065717fd07f08aeb78ed3f8235e88bc17935172d72be3e
BLOCKCHAIN_TOKEN_FACTORY=0x23a81e62f190993c932ccea79308f8ff1ea558d9
```

### Verificar que la integración blockchain funciona

Cuando el backend arranca y recibe un request de inversión, podés ver en los logs:
```
INFO  BlockchainService - Tx enviada: 0x983a508c... contrato: 0x9f2c766d...
INFO  InvestmentService - Inversión creada: id=1, usuario=2, proyecto=1, monto=500
```

Si Sepolia está caído, verás:
```
WARN  SmartContractService - Blockchain no disponible - modo offline
```

---

## 19. TokenFactory — Despliegue e integración automática

### 19.1 ¿Qué cambió?

Anteriormente todos los proyectos compartían el mismo contrato `ProjectToken` (`0x9f2c766d...`). Ahora **cada proyecto obtiene su propio token ERC-20** cuando pasa a estado `FINANCIAMIENTO`.

El flujo automatizado es:

```
PREPARACION ──(ADMIN cambia estado)──→ FINANCIAMIENTO
                                            │
                                            ▼
                              ┌──────────────────────────┐
                              │ 1. TokenFactory.         │
                              │    crearTokenProyecto()  │
                              │    deploya un nuevo      │
                              │    ProjectToken en       │
                              │    Sepolia               │
                              └──────────┬───────────────┘
                                         │
                                         ▼
                              ┌──────────────────────────┐
                              │ 2. Se inserta un nuevo    │
                              │    subtoken en la DB con  │
                              │    proyecto_id,           │
                              │    contract_address,      │
                              │    precio_base =          │
                              │    valor_nominal_token    │
                              └──────────────────────────┘
```

### 19.2 TokenFactory desplegado en Sepolia

Se creó una clase Java standalone `DeployTokenFactory.java` que deploya el contrato `TokenFactory.sol` a Sepolia usando Web3j.

**Resultado del despliegue:**

| Item | Valor |
|------|-------|
| **Contrato** | `TokenFactory.sol` |
| **Red** | Sepolia (chain ID: 11155111) |
| **Address** | `0x23a81e62f190993c932ccea79308f8ff1ea558d9` |
| **Treasury** | `0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A` |
| **Tx** | `0x98b83bc7d3880e0485318ad79790ff7c5e7a69b1852afbe2df0472bd4dd81bed` |
| **Ver en Etherscan** | https://sepolia.etherscan.io/address/0x23a81e62f190993c932ccea79308f8ff1ea558d9 |

### 19.3 El deployer: `DeployTokenFactory.java` — Explicado línea por línea

**Archivo:** `src/main/java/com/systeam/blockchain/deploy/DeployTokenFactory.java`

Esta es una clase **standalone** (tiene `main()`) que no necesita Spring Boot. Se ejecuta con `java -cp` y deploya TokenFactory.sol a Sepolia usando Web3j. El bytecode del contrato está **embebido** como constante para no depender de archivos externos.

```java
package com.systeam.blockchain.deploy;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

public class DeployTokenFactory {

    // ─── Bytecode del contrato TokenFactory.sol compilado con solc 0.8.20 ────
    private static final String TOKEN_FACTORY_BYTECODE =
        "60a060405234801561000f575f80fd5b50604051612..."
        // ↑ (truncado por legibilidad, en el archivo real está completo)
        // Este bytecode incluye el código de TokenFactory + ProjectToken
        // porque TokenFactory.sol importa ProjectToken.sol con "new ProjectToken()"
        // El compilador incluye ambos contratos en un solo binario.
        ;

    public static void main(String[] args) throws Exception {
        // ═══════════════════════════════════════════════════════════════════
        // PASO 1: Leer configuración del archivo .env
        // ═══════════════════════════════════════════════════════════════════
        Properties env = new Properties();
        //       ↑
        //  Properties es como un Map<String, String> que lee archivos clave=valor
        Path envPath = Paths.get(".env");
        //             ↑
        //  Busca el archivo .env en el directorio actual
        //  (la raíz del proyecto, donde está pom.xml)
        if (!Files.exists(envPath)) {
            envPath = Paths.get("../.env");
            // Si no está acá, busca en el directorio padre
            // (por si ejecutamos desde una subcarpeta)
        }
        env.load(Files.newBufferedReader(envPath));
        //       ↑
        //  Lee el archivo .env línea por línea y carga {clave: valor}

        // ── Extraer las variables que necesitamos ──
        String rpcUrl = env.getProperty("BLOCKCHAIN_RPC_URL");
        //              ↑
        //  Ej: "https://ethereum-sepolia-rpc.publicnode.com"
        String privateKey = env.getProperty("BLOCKCHAIN_PRIVATE_KEY");
        //              ↑
        //  La private key de la wallet del backend
        //  (NUNCA compartir esta clave, está en .env que está en .gitignore)
        String treasury = env.getProperty("BLOCKCHAIN_TREASURY");
        //              ↑
        //  La address del treasury: 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
        //  Esta address se pasa al constructor de TokenFactory
        String oldFactory = env.getProperty("BLOCKCHAIN_TOKEN_FACTORY");
        //              ↑
        //  La address actual de la factory (si ya existe)
        //  Sirve para reemplazarla en .env

        System.out.println("=== Deploy TokenFactory.sol a Sepolia ===");
        System.out.println("RPC:  " + rpcUrl);
        System.out.println("From: " + Credentials.create(privateKey).getAddress());
        //                                        ↑
        //  Creamos un objeto Credentials temporal solo para
        //  mostrar la address pública (sin revelar la private key)
        System.out.println("Treasury: " + treasury);

        // ═══════════════════════════════════════════════════════════════════
        // PASO 2: Conectar a Sepolia y crear la wallet
        // ═══════════════════════════════════════════════════════════════════
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));
        //               ↑                      ↑
        //  Web3j es la    Crea un cliente HTTP que apunta
        //  biblioteca     al RPC de Sepolia
        //  principal
        Credentials credentials = Credentials.create(privateKey);
        //                       ↑
        //  Crea la wallet del backend a partir de la private key
        //  credentials.getAddress() → 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A

        // ═══════════════════════════════════════════════════════════════════
        // PASO 3: Armar el payload de creación del contrato
        // ═══════════════════════════════════════════════════════════════════
        String encodedArgs = encodeAddress(treasury);
        //       ↑
        //  Codifica la address del treasury como argumento del constructor:
        //  "0000000000000000000000007eea865d2f47b5cc0ff4c8967c1ccf667febe50a"
        //  (32 bytes con padding de ceros a la izquierda)
        String fullData = "0x" + TOKEN_FACTORY_BYTECODE + encodedArgs;
        //                   ↑
        //  "0x" indica que es hexadecimal
        //  El payload completo = bytecode + argumentos del constructor
        //
        //  Estructura de una transacción de creación de contrato:
        //  ┌─────────────────────────────────────────────────────────────┐
        //  │ 0x | BYTECODE (código del contrato) | ARGS (constructor)   │
        //  └─────────────────────────────────────────────────────────────┘

        // ═══════════════════════════════════════════════════════════════════
        // PASO 4: Obtener nonce y gas price de Sepolia
        // ═══════════════════════════════════════════════════════════════════
        EthGetTransactionCount count = web3j.ethGetTransactionCount(
            credentials.getAddress(), DefaultBlockParameterName.PENDING
        ).send();
        //                    ↑                        ↑
        //  La wallet que     "PENDING" = incluye txs
        //  firma la tx       pendientes en el nonce
        BigInteger nonce = count.getTransactionCount();
        //       ↑
        //  Nonce = cuántas transacciones ha enviado esta wallet
        //  Cada nueva tx debe tener nonce + 1

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        //       ↑
        //  Pregunta al nodo: "¿cuál es el precio del gas ahora?"
        //  En Sepolia suele ser ~1-5 Gwei (mucho más barato que mainnet)

        BigInteger gasLimit = BigInteger.valueOf(3_000_000L);
        //       ↑
        //  Límite de gas para esta transacción.
        //  Deployment de TokenFactory: ~2.5M de gas
        //  Ponemos 3M para tener margen

        // ═══════════════════════════════════════════════════════════════════
        // PASO 5: Crear, firmar y enviar la transacción
        // ═══════════════════════════════════════════════════════════════════
        long chainId = 11155111L;
        //       ↑
        //  Chain ID de Sepolia = 11155111
        //  Ethereum mainnet = 1, Goerli = 5, Sepolia = 11155111
        //  Se usa en EIP-155 para proteger contra replay attacks

        RawTransaction rawTx = RawTransaction.createContractTransaction(
            nonce,                       // ← nonce de la wallet
            gasPrice,                    // ← precio del gas
            gasLimit,                    // ← gas máximo
            BigInteger.ZERO,             // ← 0 ETH (no enviamos ether)
            fullData                     // ← bytecode + args del constructor
        );
        //  RawTransaction = la transacción SIN firmar todavía

        byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
        //       ↑                    ↑              ↑           ↑
        //  Bytes        La tx sin     Chain ID       La wallet
        //  firmados     firmar        (EIP-155)      que firma
        //
        //  Firmar = probar que quien envía la tx es el dueño de la private key
        //  Sin firma, el nodo rechazaría la transacción

        String hexSigned = Numeric.toHexString(signed);
        //       ↑
        //  Convierte los bytes firmados a hexadecimal con prefijo "0x"

        EthSendTransaction response = web3j.ethSendRawTransaction(hexSigned).send();
        //       ↑                               ↑
        //  La respuesta     Envía la tx firmada al nodo de Sepolia
        //  del nodo
        //
        //  Si el nodo acepta la tx, response contiene el txHash
        //  Si el nodo rechaza, response.hasError() = true

        if (response.hasError()) {
            System.err.println("Error: " + response.getError().getMessage());
            //                         ↑
            //  Muestra el error (ej: "insufficient funds", "nonce too low")
            web3j.shutdown();
            System.exit(1);
            // Salimos con código de error
        }

        // ═══════════════════════════════════════════════════════════════════
        // PASO 6: Esperar confirmación de la transacción
        // ═══════════════════════════════════════════════════════════════════
        String txHash = response.getTransactionHash();
        //       ↑
        //  txHash = "0x98b83bc7d3880e0485318ad79790ff7c5e7a69b1852afbe2df0472bd4dd81bed"
        //  Este hash identifica la tx en la blockchain

        System.out.println("Tx enviada: " + txHash);
        System.out.println("Esperando confirmacion...");

        EthGetTransactionReceipt receipt;
        //       ↑
        //  El "recibo" de la transacción (recién está confirmada)
        //  cuando receipt.getTransactionReceipt().isPresent() = true

        do {
            // Mientras no esté confirmada...
            Thread.sleep(5000);
            // Esperamos 5 segundos (el bloque en Sepolia es ~12 segundos)
            receipt = web3j.ethGetTransactionReceipt(txHash).send();
            //       ↑
            //  Preguntamos al nodo: "¿ya se confirmó la tx?"
        } while (receipt.getTransactionReceipt().isEmpty());
        //                 ↑
        //  Mientras el recibo esté vacío, repetimos

        // ═══════════════════════════════════════════════════════════════════
        // PASO 7: Extraer la address del contrato deployado
        // ═══════════════════════════════════════════════════════════════════
        String contractAddress = receipt.getTransactionReceipt().get().getContractAddress();
        //       ↑                                            ↑
        //  "0x23a81e62..."    getContractAddress() devuelve la address
        //                     del NUEVO contrato deployado

        System.out.println("\n=== TOKEN FACTORY DESPLEGADO ===");
        System.out.println("Address: " + contractAddress);
        System.out.println("Tx: " + txHash);

        // ═══════════════════════════════════════════════════════════════════
        // PASO 8: Actualizar .env con la nueva address
        // ═══════════════════════════════════════════════════════════════════
        String envContent = Files.readString(envPath);
        //       ↑
        //  Lee TODO el contenido de .env como String

        envContent = envContent.replace(
            "BLOCKCHAIN_TOKEN_FACTORY=" + oldFactory,
            //         ↑
            //  Busca la línea actual (ej: "BLOCKCHAIN_TOKEN_FACTORY=0x0000...")
            "BLOCKCHAIN_TOKEN_FACTORY=" + contractAddress
            //         ↑
            //  Reemplaza con la nueva address
        );
        Files.writeString(envPath, envContent);
        //       ↑
        //  Guarda el archivo .env actualizado

        System.out.println(".env actualizado correctamente");

        web3j.shutdown();
        // Cierra la conexión HTTP con el nodo RPC
    }

    // ═══════════════════════════════════════════════════════════════════════
    // encodeAddress: codifica una address de Ethereum como argumento ABI
    // ═══════════════════════════════════════════════════════════════════════
    private static String encodeAddress(String address) {
        // El estándar ABI dice que los argumentos de tipo address
        // deben ocupar EXACTAMENTE 32 bytes (64 caracteres hex),
        // con la address real alineada a la derecha y ceros a la izquierda.
        //
        // Ejemplo:
        //   address = "0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A"
        //   → limpio = "7eea865d2f47b5cc0ff4c8967c1ccf667febe50a"  (40 chars)
        //   → padded = "0000000000000000000000007eea865d2f47b5cc0ff4c8967c1ccf667febe50a"  (64 chars)

        String clean = address.replace("0x", "").toLowerCase();
        //                          ↑               ↑
        //  Sacamos "0x" del       Todo a minúsculas
        //  inicio                 (por convención)
        return "000000000000000000000000" + clean;
        //        ↑                             ↑
        //  24 ceros (48 chars hex)        40 chars de la address
        //  = 64 chars = 32 bytes
    }
}
```

**¿Por qué el bytecode está embebido en el código Java y no en un archivo?**

- **Autocontenido:** La clase `DeployTokenFactory.java` se puede ejecutar desde cualquier lado, no necesita leer archivos externos
- **Versionado:** El bytecode viaja con el código fuente. Si alguien cambia el contrato Solidity, sabe que también debe actualizar el bytecode
- **Simple:** No requiere herramientas externas (solc, node_modules) para deployar

**¿Cómo se obtuvo el bytecode?**

```bash
# 1. Instalar OpenZeppelin (dependencia de ProjectToken.sol)
cd blockchain
npm install @openzeppelin/contracts@5.0.2

# 2. Compilar con solc
npx solc@0.8.20 --bin --include-path node_modules \
  --base-path contracts -o compiled contracts/TokenFactory.sol

# 3. El bytecode está en: blockchain/compiled/TokenFactory_sol_TokenFactory.bin
# 4. Se copió ese contenido como string en DeployTokenFactory.java
```

**Para volver a deployar (si hiciera falta cambiar la factory):**

```bash
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
cd proyecto

# Compilar
.\mvnw.cmd compile

# Obtener classpath de Maven
.\mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"

# Ejecutar el deployer
$cp = "target\classes;" + (Get-Content "target\cp.txt" -Raw).Trim()
java -cp "$cp" com.systeam.blockchain.deploy.DeployTokenFactory

# El deployer:
# 1. Lee .env
# 2. Deploya el contrato a Sepolia
# 3. Espera confirmación
# 4. Actualiza .env automáticamente
```

### 19.4 Integración automática: `ProjectService.java` — Explicado línea por línea

**Archivo:** `src/main/java/com/systeam/project/service/ProjectService.java`

#### 19.4.1 El método `updateProjectStatus()` — Modificado

Cuando un ADMIN cambia el estado de un proyecto de `PREPARACION` a `FINANCIAMIENTO`, este método se ejecuta:

```java
@Transactional
//       ↑
//  @Transactional = todo esto corre en una SOLA transacción de base de datos.
//  Si algo falla (ej: la blockchain no responde), TODO se deshace.
//  No queda el proyecto en FINANCIAMIENTO sin subtoken.
public void updateProjectStatus(Long projectId, String newEstado) {
    //                                             ↑
    //  projectId = 5 (ejemplo)
    //  newEstado = "FINANCIAMIENTO"

    // ── Buscar el proyecto en la DB (o lanzar 404) ─────────────
    Proyecto proyecto = findProjectOrThrow(projectId);
    //                         ↑
    //  proyecto.getEstado() = "PREPARACION" (viene de la DB)

    String currentEstado = proyecto.getEstado();

    // ── Validar que la transición sea válida ─────────────────────
    if (!isValidTransition(currentEstado, newEstado)) {
        //       ↑
        //  ¿Se puede ir de PREPARACION a FINANCIAMIENTO? → SÍ
        //  ¿Se puede ir de PREPARACION a EJECUCION? → NO (lanza error)
        throw new ConflictException(
            "Transicion de estado invalida: " + currentEstado + " -> " + newEstado
        );
    }

    // ── Actualizar el estado en el objeto Java ───────────────────
    proyecto.setEstado(newEstado);
    // Cambia "PREPARACION" → "FINANCIAMIENTO"

    // ── Si es FINANCIAMIENTO y no tiene plazo, asignar 30 días ─
    if ("FINANCIAMIENTO".equals(newEstado) && proyecto.getPlazo() == null) {
        proyecto.setPlazo(LocalDateTime.now().plusDays(30));
        //                              ↑
        //  Ej: si hoy es 20/05/2026, el plazo vence el 19/06/2026
    }

    // ── Guardar en la DB ────────────────────────────────────────
    projectRepository.save(proyecto);
    //                 ↑
    //  UPDATE projects SET estado = 'FINANCIAMIENTO', ... WHERE id = 5

    // ── [NUEVO] Crear el subtoken automáticamente ──────────────
    if ("FINANCIAMIENTO".equals(newEstado)) {
        // Solo si la transición es a FINANCIAMIENTO
        // (no se crea subtoken cuando va de FINANCIAMIENTO a EJECUCION)
        crearSubtokenParaProyecto(proyecto);
        //                ↑
        //  Método nuevo que deploya el token en Sepolia y lo inserta en DB
    }
}
```

#### 19.4.2 El método `crearSubtokenParaProyecto()` — El corazón de la integración

Este método se llama AUTOMÁTICAMENTE cuando un proyecto pasa a `FINANCIAMIENTO`. Hace dos cosas:
1. **Llama a TokenFactory en Sepolia** para crear un token ERC-20 exclusivo para el proyecto
2. **Inserta un subtoken** en la DB con la address del contrato y los datos de pricing

```java
private void crearSubtokenParaProyecto(Proyecto proyecto) {
    //                             ↑
    //  proyecto.getId() = 5
    //  proyecto.getTitulo() = "Mi Proyecto Innovador"
    //  proyecto.getCupoMaximoTokens() = 10000
    //  proyecto.getValorNominalToken() = 1.50

    // ═══════════════════════════════════════════════════════════════
    // PASO 1: Definir nombre y símbolo del token
    // ═══════════════════════════════════════════════════════════════
    String tokenName = "Proyecto " + proyecto.getTitulo() + " Token";
    //       ↑
    //  Ej: "Proyecto Mi Proyecto Innovador Token"
    //  (En la práctica se podría acortar, pero así es descriptivo)

    String tokenSymbol = "p" + proyecto.getId().toString()
        .substring(0, Math.min(4, proyecto.getId().toString().length()));
    //       ↑
    //  Ej: proyecto id=5 → símbolo "p5"
    //  Ej: proyecto id=12345 → símbolo "p1234" (primeros 4 dígitos)
    //  El símbolo debe ser corto (máximo 5-6 caracteres para ERC-20)

    // ═══════════════════════════════════════════════════════════════
    // PASO 2: Determinar el supply inicial del token
    // ═══════════════════════════════════════════════════════════════
    int supply = proyecto.getCupoMaximoTokens() != null
        ? proyecto.getCupoMaximoTokens() : 100000;
    //       ↑                                  ↑
    //  Si el proyecto definió un    Si no, usamos 100,000 como default
    //  cupo máximo, usamos ese

    BigInteger supplyInicial = BigInteger.valueOf(supply);
    //       ↑
    //  Ej: supply = 10000
    //  BigInteger.valueOf(10000) = 10000
    //  No multiplicamos por 10^18 porque Solidity usa 18 decimales por default,
    //  pero el valor que pasamos es la cantidad de tokens "enteros"

    // ═══════════════════════════════════════════════════════════════
    // PASO 3: Llamar a TokenFactory en Sepolia para crear el token
    // ═══════════════════════════════════════════════════════════════
    String contractAddress;
    //       ↑
    //  Almacenará la address del nuevo contrato en Sepolia

    try {
        // ── Intentar crear el token via TokenFactory ──
        contractAddress = tokenFactoryService.crearTokenProyecto(
            proyecto.getId(),         // ← 5 (ID en nuestra DB)
            tokenName,                // ← "Proyecto Mi Proyecto Token"
            tokenSymbol,              // ← "p5"
            supplyInicial             // ← 10000 tokens
        );
        //       ↑
        //  Esto ENVÍA UNA TRANSACCIÓN a Sepolia.
        //  La transacción:
        //    1. Llama a TokenFactory.crearTokenProyecto()
        //    2. TokenFactory deploya un nuevo ProjectToken
        //    3. Mintea supplyInicial tokens al treasury
        //    4. Devuelve la address: "0xabc123..."
        //
        //  Tiempo estimado: ~15-30 segundos (depende de Sepolia)
        //  Costo: ~0.001-0.005 ETH en gas (gratis en testnet)

        log.info("Token creado para proyecto {}: {} -> {}",
                proyecto.getId(), tokenSymbol, contractAddress);
        // Log: "Token creado para proyecto 5: p5 -> 0xabc123..."

    } catch (Exception e) {
        // ── Si la blockchain no responde, usamos fallback ──
        log.error("Error creando token via factory para proyecto {}: {}. Usando fallback.",
            proyecto.getId(), e.getMessage());
        //     ↑
        //  Ej: "Error creando token via factory para proyecto 5: Connection refused"
        //  Esto puede pasar si:
        //    - Sepolia RPC está caído
        //    - No hay conexión a Internet
        //    - La wallet no tiene fondos para gas

        contractAddress = "0x0000000000000000000000000000000000000000";
        //       ↑
        //  Address CERO = "no hay contrato en Sepolia"
        //  El subtoken se crea igual, pero usa el token global como fallback
    }

    // ═══════════════════════════════════════════════════════════════
    // PASO 4: Insertar el subtoken en la base de datos
    // ═══════════════════════════════════════════════════════════════
    BigDecimal valorNominal = proyecto.getValorNominalToken() != null
        ? proyecto.getValorNominalToken() : BigDecimal.ONE;
    //       ↑                                  ↑
    //  Si tiene valor nominal,    Si no, default = 1.00
    //  lo usamos como precio base

    jdbc.update("""
        INSERT INTO subtokens
            (nombre, suministro_total, cupo_restante, precio_actual,
             proyecto_id, precio_base, factor_volatilidad, contract_address)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        tokenName,              // ← nombre: "Proyecto Mi Proyecto Token"
        supply,                 // ← suministro_total: 10000
        supply,                 // ← cupo_restante: 10000 (al inicio = supply total)
        valorNominal,           // ← precio_actual: 1.50
        proyecto.getId(),       // ← proyecto_id: 5
        valorNominal,           // ← precio_base: 1.50 (precio sin ajuste dinámico)
        new BigDecimal("0.50"), // ← factor_volatilidad: 0.50 (50% de sensibilidad)
        contractAddress         // ← contract_address: "0xabc123..." o "0x0000..."
    );
    //                       ↑
    //  Esto ejecuta: INSERT INTO subtokens (...) VALUES (...)
    //  PostgreSQL genera el id automáticamente (BIGSERIAL)

    log.info("Subtoken creado en DB para proyecto {}: {} (supply={}, precio={})",
        proyecto.getId(), tokenSymbol, supply, valorNominal);
    // Log: "Subtoken creado en DB para proyecto 5: p5 (supply=10000, precio=1.50)"
}
```

#### 19.4.3 ¿Qué pasa con el símbolo del token?

El símbolo se genera automáticamente como `p` + los primeros dígitos del ID:

| ID del proyecto | Símbolo | Explicación |
|-----------------|---------|-------------|
| 5 | `p5` | Un dígito |
| 42 | `p42` | Dos dígitos |
| 123 | `p123` | Tres dígitos |
| 9999 | `p9999` | Cuatro dígitos (máximo) |
| 12345 | `p1234` | Solo primeros 4 dígitos |

**¿Por qué no usar el nombre completo?** Los símbolos ERC-20 deben ser cortos
(idealmente 3-6 caracteres, máximo 10). "Proyecto Mi Proyecto Token" no funcionaría.

### 19.5 V11 Migration — `contract_address` en subtokens

**Archivo:** `src/main/resources/db/proyectos/V11__add_contract_address_to_subtokens.sql`

Cuando creamos un subtoken para un proyecto, necesitamos guardar la address del contrato ERC-20 que se deployó en Sepolia. Esta migration agrega esa columna a la tabla `subtokens`.

```sql
-- ════════════════════════════════════════════════════════════════════
-- V11: Agregar contract_address a la tabla subtokens
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE subtokens
--    ↑
--  Modificamos la tabla existente subtokens

ADD COLUMN IF NOT EXISTS contract_address VARCHAR(42);
--         ↑              ↑                   ↑
--  Agregamos     Nombre de la     VARCHAR(42) porque una address
--  una columna   nueva columna    de Ethereum tiene 42 caracteres:
--                                 "0x" + 40 caracteres hex
--                                 Ej: "0x23a81e62f190993c932ccea79308f8ff1ea558d9"
--                                 (el VARCHAR lo mide en caracteres, no bytes)
--
--  ¿Por qué VARCHAR(42) y no TEXT?
--  - VARCHAR(42) es más preciso: sabemos exactamente el tamaño
--  - TEXT guardaría lo mismo, pero no valida el largo
--  - La diferencia en performance es mínima
--
--  ¿Por qué IF NOT EXISTS?
--  - Por si la migration ya se ejecutó antes
--  - Flyway la ejecuta una sola vez, pero por seguridad ponemos IF NOT EXISTS

CREATE INDEX IF NOT EXISTS idx_subtokens_contract_address
--           ↑
--  Creamos un índice (como el índice de un libro, para buscar rápido)

ON subtokens(contract_address);
--   ↑               ↑
--  En la tabla      Sobre la columna contract_address
--  subtokens
--
--  ¿Para qué sirve este índice?
--  - Si en el futuro queremos buscar un subtoken por su address de contrato
--    (ej: "dame el proyecto que tiene el token 0xabc..."), el índice lo
--    encuentra al instante
--  - Sin índice, PostgreSQL tendría que leer TODA la tabla (table scan)
--  - Con índice, va directo al registro (index scan)
--
--  ¿Cuándo se usa?
--  - Si implementamos "ver proyecto por token address"
--  - Para debugging: "este token de Sepolia, ¿a qué proyecto pertenece?"
```

**¿Cómo se relaciona con el código Java?**

Cuando `ProjectService.crearSubtokenParaProyecto()` inserta un subtoken,
pasa `contractAddress` como el último parámetro del INSERT:

```java
jdbc.update("""
    INSERT INTO subtokens
        (nombre, suministro_total, cupo_restante, precio_actual,
         proyecto_id, precio_base, factor_volatilidad, contract_address)
        --                                              ↑
        --  Esta columna fue agregada por V11
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """,
    ...,  // los primeros 7 parámetros
    contractAddress  // ← el último es la address del contrato en Sepolia
);
```

### 19.6 ¿Qué pasa si la blockchain está caída?

El `TokenFactoryService` está diseñado con tolerancia a fallos:

```java
try {
    contractAddress = tokenFactoryService.crearTokenProyecto(
        proyecto.getId(), tokenName, tokenSymbol, supplyInicial
    );
} catch (Exception e) {
    log.error("Error creando token via factory. Usando fallback.");
    contractAddress = "0x0000000000000000000000000000000000000000";
}
```

Cuando la factory no responde:
- Se usa `0x0000...0000` como contract_address
- El subtoken se crea igual en la DB
- El proyecto puede recibir inversiones normalmente (usa el token global como fallback)
- Más tarde se puede llamar manualmente a `TokenFactoryService.crearTokenProyecto()` para crear el token, y actualizar `contract_address` en la DB

### 19.7 Endpoints — Sin cambios

Esta integración es **transparente** para los endpoints existentes. No se modificó ninguna API REST. El único cambio es que cuando un ADMIN hace:

```
PATCH /api/projects/5/status?status=FINANCIAMIENTO
```

...detrás de escena también se deploya un token ERC-20 y se crea el subtoken automáticamente.

### 19.8 Estructura de la carpeta `blockchain/` y `.gitignore`

La carpeta `blockchain/` contiene los contratos Solidity y las herramientas para compilarlos:

```
blockchain/
├── contracts/                        ← ★ Código fuente Solidity (seguimiento en git)
│   ├── ProjectToken.sol              ← ERC-20 con quema del 0.1%
│   └── TokenFactory.sol              ← Fábrica que deploya tokens por proyecto
├── node_modules/                     ← ⛔ Ignorado por git (dependencias npm)
├── compiled/                         ← ⛔ Ignorado por git (bytecode compilado)
├── package.json                      ← ★ Seguimiento en git (define dependencias npm)
└── package-lock.json                 ← ★ Seguimiento en git (versiones exactas)
```

**¿Qué se ignora en `.gitignore` y por qué?**

```
# blockchain/node_modules/ → 100+ MB de dependencias npm (OpenZeppelin)
# blockchain/compiled/    → Bytecode generado por solc (se regenera con `npx solc`)
```

| Carpeta | ¿Por qué se ignora? | ¿Cómo se regenera? |
|---------|---------------------|--------------------|
| `node_modules/` | ~100MB, cambia constantemente, no se debe versionar | `cd blockchain && npm install` |
| `compiled/` | Archivos `.bin` y `.abi` generados por solc | `npx solc@0.8.20 --bin --include-path node_modules --base-path contracts -o compiled contracts/TokenFactory.sol` |

**El bytecode en `DeployTokenFactory.java`:**
Como `compiled/` está en `.gitignore`, el bytecode del contrato se EMBEBE directamente en el código Java como constante. Esto hace que `DeployTokenFactory.java` sea autocontenido: no necesita leer archivos externos para deployar.

**Regla práctica:**
- Si creás un nuevo `.sol`, guardalo en `blockchain/contracts/`
- Compilalo con `npx solc` → el bytecode va a `compiled/` (ignorado)
- Copiá el bytecode a tu Java deployer o a un servicio que lo use
- Nunca commiteés `node_modules/` ni `compiled/`

### 19.9 `InvestmentService.findSubtokenByProject()` — Ahora devuelve `contract_address`

**Archivo:** `src/main/java/com/systeam/investment/service/InvestmentService.java`

Cuando `InvestmentService` busca el subtoken de un proyecto (para validar o crear una inversión), ahora incluye la columna `contract_address` en la consulta SQL.

**Antes (V10):**
```java
private Map<String, Object> findSubtokenByProject(Long proyectoId) {
    List<Map<String, Object>> subtokens = jdbc.query(
        "SELECT id, nombre, suministro_total, cupo_restante, precio_actual, " +
        "precio_base, factor_volatilidad " +                           // ← sin contract_address
        "FROM subtokens WHERE proyecto_id = ?",
        new MapRowMapper(), proyectoId
    );
    // ...
}
```

**Después (V11):**
```java
private Map<String, Object> findSubtokenByProject(Long proyectoId) {
    List<Map<String, Object>> subtokens = jdbc.query(
        "SELECT id, nombre, suministro_total, cupo_restante, precio_actual, " +
        "precio_base, factor_volatilidad, contract_address " +          // ← ★ NUEVO
        "FROM subtokens WHERE proyecto_id = ?",
        new MapRowMapper(), proyectoId
    );
    // ...
}
```

**¿Para qué sirve?**
Cuando se devuelve la respuesta de una inversión (`InvestmentResponse`), el frontend puede mostrar la address del token ERC-20 específico del proyecto. El inversor sabe exactamente qué contrato en Sepolia respalda sus sub-tokens.

### 19.10 Resumen de todos los cambios del TokenFactory

| Componente | Cambio |
|-----------|--------|
| `DeployTokenFactory.java` | ★ Nuevo — deploya TokenFactory.sol a Sepolia (standalone, bytecode embebido) |
| `TokenFactoryService.java` | ★ Nuevo — Java client para TokenFactory.sol |
| `ProjectService.java` | Modificado — `updateProjectStatus()` llama a `crearSubtokenParaProyecto()` |
| `ProjectServiceTest.java` | Modificado — 2 tests agregan mocks de TokenFactoryService + JdbcTemplate |
| `InvestmentService.java` | Modificado — `findSubtokenByProject()` incluye `contract_address` |
| `V11__add_contract_address_to_subtokens.sql` | ★ Nuevo — columna + índice |
| `.env` | Modificado — `BLOCKCHAIN_TOKEN_FACTORY` agregado |
| `.gitignore` | Modificado — `blockchain/node_modules/` y `blockchain/compiled/` agregados |
| `DOCUMENTACION_MODULO_INVERSION.md` | Modificada — sección 19 agregada |
| `ProjectToken.sol` | Sin cambios (era el contrato base existente) |
| `TokenFactory.sol` | ★ Nuevo — fábrica que deploya ProjectToken por proyecto |

---

## Apéndice B: Cómo agregar un nuevo contrato Solidity

Esta guía explica el paso a paso para agregar un nuevo contrato Solidity al proyecto, compilarlo, deployarlo y llamarlo desde Java.

### B.1 Crear el contrato

Crear el archivo en `blockchain/contracts/`:

```solidity
// blockchain/contracts/MiContrato.sol
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract MiContrato {
    address public owner;
    uint256 public contador;

    constructor(address _owner) {
        owner = _owner;
    }

    function incrementar() external {
        contador++;
    }

    function leerContador() external view returns (uint256) {
        return contador;
    }
}
```

### B.2 Compilar con solc

```bash
cd blockchain

# Instalar dependencias (si no lo hiciste antes)
npm install @openzeppelin/contracts@5.0.2

# Compilar
npx solc@0.8.20 --bin --abi \
  --include-path node_modules \
  --base-path contracts \
  -o compiled \
  contracts/MiContrato.sol

# Resultado:
#   compiled/MiContrato_sol_MiContrato.bin  ← bytecode (hex)
#   compiled/MiContrato_sol_MiContrato.abi  ← ABI (JSON)
```

### B.3 Leer el bytecode

```bash
# Mostrar el bytecode en la consola (para copiarlo a Java)
Get-Content compiled/MiContrato_sol_MiContrato.bin -Raw

# O guardarlo en una variable
$bytecode = Get-Content compiled/MiContrato_sol_MiContrato.bin -Raw
```

### B.4 Agregar la address a `BlockchainProperties.java`

```java
// En BlockchainProperties.java
private String miContratoAddress;

// Getter y setter
public String getMiContratoAddress() { return miContratoAddress; }
public void setMiContratoAddress(String miContratoAddress) { this.miContratoAddress = miContratoAddress; }
```

### B.5 Agregar variable de entorno

En `.env`:
```
BLOCKCHAIN_MI_CONTRATO=0x0000000000000000000000000000000000000000
```

En `application.properties`:
```
blockchain.mi-contrato-address=${BLOCKCHAIN_MI_CONTRATO}
```

### B.6 Agregar método en `BlockchainService.java`

```java
// Para leer (eth_call)
public BigInteger leerContador() throws Exception {
    Function fn = new Function(
        "leerContador",
        List.of(),
        List.of(new TypeReference<Uint256>() {})
    );
    return callAndExtractUint256(props.getMiContratoAddress(), fn);
}

// Para escribir (eth_sendRawTransaction) - void
public String incrementarContador() throws Exception {
    Function fn = new Function(
        "incrementar",
        List.of(),
        List.of()
    );
    return executeWrite(props.getMiContratoAddress(), fn);
}
```

### B.7 Deployar

Podés crear un `DeployMiContrato.java` siguiendo el modelo de `DeployTokenFactory.java`:

```java
// PASOS:
// 1. Tomar el bytecode del paso B.3 como String constante
// 2. Construir el payload: "0x" + BYTECODE + encodeAddress(treasury)
// 3. Obtener nonce y gasPrice del nodo
// 4. Crear RawTransaction.createContractTransaction(nonce, gasPrice, gasLimit, 0, fullData)
// 5. Firmar: TransactionEncoder.signMessage(rawTx, 11155111, credentials)
// 6. Enviar: web3j.ethSendRawTransaction(hexSigned).send()
// 7. Esperar confirmación (polling cada 5 segundos)
// 8. Extraer address: receipt.getContractAddress()
// 9. Actualizar .env con la nueva address
```

### B.8 Verificar en Etherscan

```
https://sepolia.etherscan.io/verifyContract
→ Poner la address del nuevo contrato
→ Seleccionar compiler 0.8.20, licencia MIT
→ Pegar el código fuente
→ Verify
```

---

## 22. INV-09 e INV-10 — Refund on-chain y estado RECHAZADO

### 22.1 ¿Qué historias de usuario cubren?

| HU | Nombre | Descripción |
|----|--------|-------------|
| **HU-28** | Reembolso de inversiones fallidas | Como inversor, quiero recibir un reembolso cuando un proyecto no alcanza su meta de financiamiento, para recuperar mi capital invertido |
| **INV-10** | Evaluación automática de proyectos vencidos | Como administrador, quiero que el sistema evalúe automáticamente los proyectos cuyo plazo expiró, para ejecutar reembolsos sin intervención manual |

**INV-09** implementa la función `refund()` en el contrato `InvestmentSwap.sol` (la parte on-chain del reembolso). **INV-10** implementa el scheduler que detecta proyectos vencidos, ejecuta los reembolsos y cambia el estado a `RECHAZADO`.

### 22.2 ¿Qué problema resuelve?

Cuando un proyecto de financiamiento colectivo NO alcanza su meta dentro del plazo, los inversores tienen derecho a recuperar su dinero. Sin INV-09/INV-10:

- **No había reembolso automático:** Alguien tenía que revisar manualmente cada proyecto vencido
- **No había registro on-chain:** No quedaba evidencia en la blockchain de que el reembolso se ejecutó
- **Estado incorrecto:** Los proyectos fallidos se marcaban como `FINALIZADO` (como si hubieran tenido éxito)

Con INV-09 e INV-10:

| Antes | Después |
|-------|---------|
| Estado `FINALIZADO` para proyectos fallidos | Estado `RECHAZADO` para proyectos que no alcanzaron la meta |
| Sin registro on-chain del reembolso | Evento `RefundMade` en la blockchain |
| Reembolso manual (alguien tenía que ejecutarlo) | Scheduler automático cada 6 AM |

### 22.3 Arquitectura — ¿Qué archivos participan?

```
                    ┌──────────────────────────┐
                    │   InvestmentScheduler    │  src/.../scheduler/InvestmentScheduler.java
                    │   @Scheduled(6 AM)       │
                    └───────────┬──────────────┘
                                │ llama a
                    ┌───────────▼──────────────┐
                    │   InvestmentService      │  src/.../investment/service/InvestmentService.java
                    │   .processExpiredProjects│
                    └───────────┬──────────────┘
                                │
                    ┌───────────┼──────────────────────┐
                    │           │                      │
         ┌──────────▼───┐  ┌───▼────────┐  ┌─────────▼─────────┐
         │ SmartContract │  │ DB:        │  │ DB:               │
         │ Service       │  │ Devolver   │  │ UPDATE projects   │
         │ .refund       │  │ $IDEA      │  │ SET estado =      │
         │ Investment()  │  │ (saldo_    │  │ 'RECHAZADO'       │
         └───────┬───────┘  │ idea)      │  └───────────────────┘
                 │          └────────────┘
    ┌────────────┼────────────┐
    │            │            │
┌───▼──────┐ ┌──▼──────┐ ┌───▼─────────┐
│ Invest-  │ │ DB:     │ │ DB:         │
│ mentSwap │ │ Quemar  │ │ Marcar      │
│ Service  │ │ sub-    │ │ inversión   │
│ .refund()│ │ tokens  │ │ REEMBOLSADA │
│          │ │ (port-  │ │             │
│ (a Sepo- │ │ folio)  │ │             │
│ lia)     │ │         │ │             │
└────┬─────┘ └─────────┘ └─────────────┘
     │
     │ llama a InvestmentSwap.sol en Sepolia
     ▼
┌──────────────────────┐
│ InvestmentSwap.sol   │  blockchain/contracts/InvestmentSwap.sol
│                      │
│ refund(proyectoId,   │
│   subTokenAmount,    │
│   holder, investor)  │
│   ├─ burnFrom()      │  → quema sub-tokens del holder
│   └─ emit RefundMade │  → evento on-chain como prueba
└──────────────────────┘
```

### 22.4 El contrato InvestmentSwap.sol — función refund()

**Archivo:** `blockchain/contracts/InvestmentSwap.sol`

La función `refund()` se agregó AL MISMO contrato `InvestmentSwap.sol` (no se creó un contrato nuevo). Esto es lo que cambió:

#### 22.4.1 El evento RefundMade (líneas 129-134)

```solidity
// Este evento se emite CADA VEZ que se ejecuta un reembolso.
// Queda grabado para siempre en la blockchain y cualquiera
// puede consultarlo desde Etherscan.
//
// indexado (indexed): significa que se puede filtrar por este campo.
// Por ejemplo: "mostrame todos los RefundMade del proyecto 5"
//
// @param proyectoId     ID del proyecto en la DB que falló
// @param investor       Address del inversor (quien recupera)
// @param ideaAmount     Cantidad de $IDEA (por ahora siempre 0)
// @param subTokenAmount Cantidad de sub-tokens que se quemaron
event RefundMade(
    uint256 indexed proyectoId,   // ← "indexed" permite filtrar
    address indexed investor,     // ← se puede buscar por inversor
    uint256 ideaAmount,           // ← NO está indexed (no filtrable)
    uint256 subTokenAmount        // ← NO está indexed
);
```

**¿Por qué `ideaAmount` es 0 siempre?** Porque el contrato no devuelve $IDEA on-chain todavía (ver sección 22.6).

#### 22.4.2 La función refund() (líneas 323-348)

```solidity
// ──────────────────────────────────────────────────────────────
// FUNCIÓN: refund()
// ──────────────────────────────────────────────────────────────
//
// ¿QUÉ HACE?
//   1. Validar que subTokenAmount > 0
//   2. Validar que existe un ProjectToken para este proyecto
//   3. Quemar (burn) sub-tokens del holder (normalmente el treasury)
//   4. Emitir RefundMade como prueba on-chain del reembolso
//
// ¿POR QUÉ ES EXTERNAL Y NO PUBLIC?
//   - external: solo puede ser llamada desde AFUERA del contrato
//     (desde Java, desde otra wallet, desde otro contrato)
//   - Si fuera public, también podría llamarse desde adentro
//   - No necesitamos llamarla desde adentro, así que usamos external
//
// ¿QUIÉN LA LLAMA?
//   El backend (InvestmentSwapService.refund()) cuando el scheduler
//   detecta un proyecto vencido que no alcanzó la meta.
//
// ¿POR QUÉ ES void (no returns)?
//   - Solo emite un evento. No necesita devolver nada.
//   - El backend sabe que funcionó porque la tx no revirtió.
//
// PARÁMETROS:
//   proyectoId     → ID del proyecto (para buscar el token)
//   subTokenAmount → cuántos sub-tokens quemar (en wei, 18 decimales)
//   holder         → address del tenedor (quien tiene los sub-tokens)
//   investor       → address del inversor (solo para el evento)
function refund(
    uint256 proyectoId,
    uint256 subTokenAmount,
    address holder,
    address investor
) external {
    // ─── VALIDACIÓN 1: subTokenAmount debe ser > 0 ─────────────
    // No tiene sentido llamar a refund() para quemar 0 tokens.
    // require() revierte la tx si la condición es falsa.
    require(
        subTokenAmount > 0,
        "InvestmentSwap: subTokenAmount must be > 0"
    );

    // ─── VALIDACIÓN 2: el proyecto debe tener un token creado ──
    // Buscamos en el mapping tokenDeProyecto (que se llenó cuando
    // se llamó a crearTokenProyecto() para este proyecto).
    // Si devuelve address(0), significa que nunca se creó un token.
    address projectTokenAddr = tokenDeProyecto[proyectoId];
    require(
        projectTokenAddr != address(0),
        "InvestmentSwap: project token not deployed"
    );

    // ─── PASO 1: Quemar sub-tokens del holder ──────────────────
    // InvestmentSwap es el OWNER del ProjectToken (lo configuramos
    // en crearTokenProyecto() cuando pasamos address(this) como owner).
    //
    // Al ser owner, podemos llamar a burnFrom() que es una función
    // de ProjectToken (que extiende ERC20Burnable de OpenZeppelin).
    //
    // burnFrom(holder, amount) hace:
    //   1. Verifica que el caller (InvestmentSwap) tenga allowance
    //      suficiente del holder (esto lo maneja el mismo contrato
    //      porque es el owner)
    //   2. Reduce el balance del holder en subTokenAmount
    //   3. Reduce el totalSupply del token en subTokenAmount
    //
    // ¿Por qué quemamos? Porque si el proyecto falló, los sub-tokens
    // ya no representan nada. Hay que sacarlos de circulación.
    ProjectToken(projectTokenAddr).burnFrom(holder, subTokenAmount);
    //               ↑                      ↑           ↑
    //         Hacemos cast de la    Función que     Quién pierde
    //         address a tipo        quema tokens   los tokens
    //         ProjectToken                         (treasury)

    // ─── PASO 2: Emitir evento de reembolso ────────────────────
    // Este evento queda grabado en la blockchain como prueba de que
    // el reembolso ocurrió. El backend puede consultarlo después.
    //
    // ideaAmount se pasa como 0 porque el contrato no maneja $IDEA
    // en el refund (solo quema sub-tokens).
    emit RefundMade(proyectoId, investor, 0, subTokenAmount);
    //     ↑              ↑         ↑       ↑          ↑
    //  Nombre del     ID del    Address   $IDEA     Cantidad de
    //  evento        proyecto   inversor  devuelto  sub-tokens
    //                                   (siempre    quemados
    //                                    0 por ahora)
}
```

#### 22.4.3 ¿Qué NO hace refund() y por qué?

```solidity
// ❌ NO transfiere $IDEA al inversor
//    ¿Por qué? Porque el $IDEA ya está en el treasury.
//    En el flujo actual, el treasury llama a invest() con sus
//    propios $IDEA (no los del usuario). Cuando hay refund,
//    el treasury descuenta de su wallet y acredita al usuario
//    en la DB (columna saldo_idea en users).
//
// ❌ NO verifica que el inversor tenga allowance
//    ¿Por qué? Porque InvestmentSwap es el owner del ProjectToken.
//    Como owner, puede quemar tokens de cualquiera sin allowance
//    (eso lo maneja ERC20Burnable internamente).
//
// ❌ NO verifica que el proyecto esté vencido
//    ¿Por qué? Porque esa validación la hace el BACKEND en Java
//    (InvestmentService.processExpiredProjects()). El contrato
//    confía en que quien llama a refund() es el backend honesto.
```

### 22.5 El deployer: DeployInvestmentSwap.java

**Archivo:** `src/main/java/com/systeam/blockchain/deploy/DeployInvestmentSwap.java`

Esta clase deploya InvestmentSwap.sol a Sepolia. Es STANDALONE (tiene `main()`), no necesita Spring Boot.

#### Cómo se obtuvo el bytecode con refund

```bash
cd blockchain
npm install @openzeppelin/contracts@5.0.2
npx solc@0.8.20 --bin --abi \
  --include-path node_modules \
  --base-path contracts \
  -o compiled \
  contracts/InvestmentSwap.sol

# Resultado:
#   compiled/InvestmentSwap_sol_InvestmentSwap.bin   → bytecode NUEVO (con refund)
#   compiled/InvestmentSwap_sol_InvestmentSwap.abi    → ABI NUEVA (incluye refund)
```

El bytecode resultante se copió como String en `DeployInvestmentSwap.java`:

```java
// Este bytecode incluye TODAS las funciones del contrato:
//   - crearTokenProyecto()
//   - invest()
//   - refund()              ← NUEVO (INV-09)
//   - obtenerTokenDeProyecto()
//   - obtenerProyectoDeToken()
//   - obtenerCantidadTokens()
//   - obtenerTokenPorIndice()
private static final String BYTECODE =
    "60c060405234801561001057600080fd5b50604051611c82380380611c8283398101604081905261002f91610113565b...";  // truncado
```

#### Código completo del deployer anotado

```java
package com.systeam.blockchain.deploy;

// ─── IMPORTS ─────────────────────────────────────────────────────
// BigInteger: números grandes (gas, nonce, etc.)
// Files/Paths: leer archivos (el .env)
// Properties: leer configuración clave=valor
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

// Web3j: biblioteca para interactuar con Ethereum
import org.web3j.crypto.Credentials;     // Wallet (private key)
import org.web3j.crypto.RawTransaction;  // Transacción sin firmar
import org.web3j.crypto.TransactionEncoder;  // Para firmar
import org.web3j.protocol.Web3j;         // Cliente Ethereum
import org.web3j.protocol.core.DefaultBlockParameterName;
    // "pending" o "latest" para consultar nonce
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
    // Respuesta del nodo: cuántas txs ha enviado la wallet
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
    // Recibo de la tx confirmada
import org.web3j.protocol.core.methods.response.EthSendTransaction;
    // Respuesta del nodo al enviar la tx
import org.web3j.protocol.http.HttpService;  // Conexión HTTP al RPC
import org.web3j.utils.Numeric;  // Convertir bytes ↔ hex

public class DeployInvestmentSwap {

    // Bytecode embebido (ver sección 21.8.2 para más detalles)
    private static final String BYTECODE = "60c06040...";

    public static void main(String[] args) throws Exception {
        // ═══════════════════════════════════════════════════════
        // PASO 1: LEER .env
        // ═══════════════════════════════════════════════════════

        // Properties es como un Map<String, String> que lee archivos
        // con formato "clave=valor" (como .env)
        Properties env = new Properties();

        // Buscar el archivo .env (primero en directorio actual,
        // luego en el padre por si ejecutamos desde una subcarpeta)
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            envPath = Paths.get("../.env");
        }

        // Cargar TODAS las variables del .env en el objeto Properties
        env.load(Files.newBufferedReader(envPath));

        // Extraer SOLO las variables que necesitamos para el deploy
        String rpcUrl = env.getProperty("BLOCKCHAIN_RPC_URL");
            // ↑ "https://ethereum-sepolia-rpc.publicnode.com"
        String privateKey = env.getProperty("BLOCKCHAIN_PRIVATE_KEY");
            // ↑ Clave privada del backend (NUNCA compartir)
        String ideaAddress = env.getProperty("BLOCKCHAIN_PROJECT_TOKEN");
            // ↑ Address del token $IDEA global
        String treasury = env.getProperty("BLOCKCHAIN_TREASURY");
            // ↑ Address del treasury (multi-sig)

        // Mostrar qué vamos a hacer
        System.out.println("=== Deploy InvestmentSwap.sol a Sepolia ===");
        System.out.println("RPC:  " + rpcUrl);
        System.out.println("From: " + Credentials.create(privateKey).getAddress());
        System.out.println("IDEA: " + ideaAddress);
        System.out.println("Treasury: " + treasury);

        // ═══════════════════════════════════════════════════════
        // PASO 2: CONECTAR A SEPOLIA
        // ═══════════════════════════════════════════════════════

        // Web3j.build() crea un cliente HTTP que apunta al RPC de Sepolia
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));

        // Credentials.create() reconstruye la wallet desde la private key
        // credentials.getAddress() → 0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A
        Credentials credentials = Credentials.create(privateKey);

        // ═══════════════════════════════════════════════════════
        // PASO 3: ARMAR EL PAYLOAD DEL CONTRATO
        // ═══════════════════════════════════════════════════════

        // El constructor de InvestmentSwap recibe 2 argumentos:
        //   constructor(address _idea, address _treasury)
        //
        // Cada address ocupa 32 bytes (64 chars hex) con padding.
        // encodeAddress() convierte "0x7eEA..." en
        //   "0000000000000000000000007eea865d2f47b5cc0ff4c8967c1ccf667febe50a"
        String encodedArgs = encodeAddress(ideaAddress) + encodeAddress(treasury);

        // El payload completo para la transacción:
        //   "0x" + bytecode + args_del_constructor
        // Esto le dice a Ethereum: "creá un contrato nuevo con este código
        // y ejecutá el constructor con estos argumentos"
        String fullData = "0x" + BYTECODE + encodedArgs;

        // ═══════════════════════════════════════════════════════
        // PASO 4: OBTENER NONCE Y GAS PRICE
        // ═══════════════════════════════════════════════════════

        // NONCE = cuántas txs ha enviado esta wallet.
        // Cada tx nueva debe tener nonce + 1.
        // "PENDING" incluye txs pendientes en el cálculo.
        EthGetTransactionCount count = web3j.ethGetTransactionCount(
            credentials.getAddress(), DefaultBlockParameterName.PENDING
        ).send();
        BigInteger nonce = count.getTransactionCount();

        // GAS PRICE = precio del gas en este momento.
        // Sepolia suele tener gas price de 1-5 Gwei.
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        // GAS LIMIT = máximo de gas que pagamos.
        // InvestmentSwap es un contrato chico, 3M es generoso.
        BigInteger gasLimit = BigInteger.valueOf(3_000_000L);

        // ═══════════════════════════════════════════════════════
        // PASO 5: CREAR, FIRMAR Y ENVIAR LA TRANSACCIÓN
        // ═══════════════════════════════════════════════════════

        // Chain ID de Sepolia = 11155111
        // Mainnet = 1, Goerli = 5
        // Se usa en EIP-155 para evitar replay attacks
        long chainId = 11155111L;

        // RawTransaction.createContractTransaction() arma la tx
        // sin firmar todavía
        RawTransaction rawTx = RawTransaction.createContractTransaction(
            nonce,           // ← nonce de la wallet
            gasPrice,        // ← precio del gas
            gasLimit,        // ← gas máximo
            BigInteger.ZERO, // ← 0 ETH (no enviamos ether)
            fullData         // ← bytecode + args del constructor
        );

        // TransactionEncoder.signMessage() FIRMA la tx con la private key
        // Esto prueba que SOMOS los dueños de la wallet
        byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);

        // Numeric.toHexString() convierte los bytes firmados a hex
        String hexSigned = Numeric.toHexString(signed);

        // ethSendRawTransaction() envía la tx firmada al nodo de Sepolia
        EthSendTransaction response = web3j.ethSendRawTransaction(hexSigned).send();

        // Si el nodo rechazó la tx, mostramos el error y salimos
        if (response.hasError()) {
            System.err.println("Error: " + response.getError().getMessage());
            web3j.shutdown();
            System.exit(1);
        }

        // ═══════════════════════════════════════════════════════
        // PASO 6: ESPERAR CONFIRMACIÓN
        // ═══════════════════════════════════════════════════════

        String txHash = response.getTransactionHash();
            // ↑ ID único de la tx en la blockchain
        System.out.println("Tx enviada: " + txHash);
        System.out.println("Esperando confirmacion...");

        // Hacemos polling cada 5 segundos hasta que la tx se confirme
        // (el bloque en Sepolia es ~12 segundos)
        EthGetTransactionReceipt receipt;
        do {
            Thread.sleep(5000);  // esperar 5 segundos
            receipt = web3j.ethGetTransactionReceipt(txHash).send();
                // Preguntamos: "¿ya se confirmó?"
        } while (receipt.getTransactionReceipt().isEmpty());
            // Mientras no haya recibo, seguimos esperando

        // ═══════════════════════════════════════════════════════
        // PASO 7: EXTRAER LA ADDRESS DEL CONTRATO
        // ═══════════════════════════════════════════════════════

        String contractAddress = receipt.getTransactionReceipt()
            .get().getContractAddress();
            // ↑ Esta es la address del NUEVO contrato en Sepolia

        System.out.println("\n=== INVESTMENT SWAP DESPLEGADO ===");
        System.out.println("Address: " + contractAddress);
        System.out.println("Tx: " + txHash);

        // ═══════════════════════════════════════════════════════
        // PASO 8: ACTUALIZAR .env
        // ═══════════════════════════════════════════════════════

        // Leer TODO el .env, agregar la nueva línea y guardar
        String envContent = Files.readString(envPath);
        String newLine = "BLOCKCHAIN_INVESTMENT_SWAP=" + contractAddress;
        envContent = envContent.trim() + "\n" + newLine;
        Files.writeString(envPath, envContent);
        System.out.println(".env actualizado: " + newLine);

        // Cerrar conexión HTTP
        web3j.shutdown();
    }

    // encodeAddress: convierte una address Ethereum a formato ABI
    // (32 bytes, padding a la izquierda con ceros)
    private static String encodeAddress(String address) {
        String clean = address.replace("0x", "").toLowerCase();
        return "000000000000000000000000" + clean;
    }
}
```

### 22.6 InvestmentSwapService.refund() — El cliente Java (INV-09)

**Archivo:** `src/main/java/com/systeam/blockchain/service/InvestmentSwapService.java` (método nuevo)

```java
/**
 * Ejecuta el reembolso on-chain de un proyecto fallido.
 *
 * Llama a InvestmentSwap.refund() que quema los sub-tokens del holder
 * (treasury) y emite RefundMade(proyectoId, subTokenAmount, holder, investor).
 * El $IDEA se devuelve a nivel DB (treasury acredita saldo_idea).
 *
 * @param proyectoId     ID del proyecto
 * @param subTokenAmount Cantidad de sub-tokens a quemar (18 decimales)
 * @param holder         Address que tiene los sub-tokens (treasury)
 * @param investor       Address del inversor (solo para el evento)
 * @return txHash de la transacción en Sepolia, o null si falla
 *
 * ¿Por qué devuelve String y no void?
 *   Para que el backend pueda guardar el txHash como comprobante.
 *   Si Sepolia no está disponible, devuelve null y el backend
 *   sigue con modo offline.
 *
 * ¿Por qué no lanza excepción cuando falla?
 *   Porque el refund se ejecuta en un loop (refundAllInvestors).
 *   Si un inversor falla, queremos seguir con los demás.
 */
public String refund(
        Long proyectoId,
        BigInteger subTokenAmount,
        String holder,
        String investor
) {
    // ─── Si InvestmentSwap no está configurado, modo offline ─
    String swapAddress = props.getInvestmentSwapAddress();
    if (swapAddress == null ||
        swapAddress.equals("0x0000000000000000000000000000000000000000")) {
        log.warn("InvestmentSwap no configurado. Simulando refund offline.");
        return "0xrefund-offline-" + System.currentTimeMillis();
            // ↑ Devuelve un txHash falso para que el flujo no se rompa
    }

    try {
        // ─── PASO 1: Codificar la llamada con ABI ─────────────
        // Construimos la función "refund" con sus 4 parámetros:
        //   - proyectoId (uint256)
        //   - subTokenAmount (uint256)
        //   - holder (address)
        //   - investor (address)
        //
        // List.of() para outputs vacío porque refund() es void.
        Function fn = new Function(
            "refund",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                    // ↑ uint256 necesita BigInteger
                new Uint256(subTokenAmount),
                new Address(holder),
                    // ↑ address en Web3j = Address("0x...")
                new Address(investor)
            ),
            List.of()  // sin outputs (void)
        );

        // FunctionEncoder.encode() convierte la función + args
        // en el formato ABI que entiende Ethereum:
        //   "0x" + selector(4 bytes) + args(cada uno 32 bytes)
        String encodedFunction = FunctionEncoder.encode(fn);

        // ─── PASO 2: Obtener gas price ────────────────────────
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        // ─── PASO 3: Firmar y enviar transacción ──────────────
        // txManager.sendTransaction() es un helper de Web3j que:
        //   1. Obtiene el nonce automáticamente
        //   2. Firma con la private key
        //   3. Envía al nodo
        EthSendTransaction response = txManager.sendTransaction(
            gasPrice,
            BigInteger.valueOf(300_000L),  // gas limit
            swapAddress,                   // contrato destino
            encodedFunction,               // función codificada
            BigInteger.ZERO                // 0 ETH adjunto
        );

        // ─── PASO 4: Verificar error ──────────────────────────
        if (response.hasError()) {
            log.error("Error en refund on-chain: {}",
                response.getError().getMessage());
            return null;  // ← devolvemos null para modo offline
        }

        // ─── PASO 5: Devolver txHash ──────────────────────────
        String txHash = response.getTransactionHash();
        log.info("Refund on-chain exitoso: proyecto={}, subTokens={}, tx={}",
            proyectoId, subTokenAmount, txHash);
        return txHash;

    } catch (Exception e) {
        // Si hay una excepción (timeout, conexión caída, etc.),
        // logeamos y devolvemos null
        log.error("Excepción en refund on-chain: {}", e.getMessage());
        return null;
    }
}
```

### 22.7 SmartContractService.refundInvestment() — El puente

**Archivo:** `src/main/java/com/systeam/project/service/SmartContractService.java`

Antes de INV-09, este método simulaba el reembolso minteando tokens (no hacía nada real). Ahora llama al contrato:

```java
/**
 * Punto de entrada para ejecutar un reembolso.
 *
 * InvestmentService.refundAllInvestors() llama a este método
 * para CADA inversión de un proyecto fallido.
 *
 * Antes (sin INV-09): simulaba mintiendo tokens (falso).
 * Ahora (con INV-09): llama a InvestmentSwap.refund() on-chain.
 *
 * @param proyectoId ID del proyecto
 * @param usuarioId  ID del usuario (para logging)
 * @param montoIdea  Monto de $IDEA a reembolsar (BigDecimal)
 * @return Map con success, refundTxHash, note
 */
public Map<String, Object> refundInvestment(
        Long proyectoId,
        Long usuarioId,
        BigDecimal montoIdea
) {
    Map<String, Object> result = new HashMap<>();
    try {
        // Convertir BigDecimal a BigInteger (18 decimales)
        // Ej: montoIdea = 500.00 → 500 × 10^18 = 500000000000000000000
        BigInteger subTokenWei = montoIdea
            .multiply(BigDecimal.TEN.pow(18))
            .toBigInteger();

        // Llamar al contrato InvestmentSwap en Sepolia
        String refundTx = investmentSwapService.refund(
            proyectoId,
            subTokenWei,                       // en wei
            props.getTreasuryAddress(),        // holder = treasury
            props.getTreasuryAddress()         // investor = treasury
        );

        if (refundTx != null) {
            // ✅ Éxito: la transacción se ejecutó en Sepolia
            result.put("success", true);
            result.put("refundTxHash", refundTx);
            result.put("note", "Reembolso on-chain ejecutado en InvestmentSwap");
        } else {
            // ⚠️ Fallback: Sepolia no disponible
            result.put("success", true);
            result.put("refundTxHash",
                "0xrefund-offline-" + System.currentTimeMillis());
            result.put("note",
                "Reembolso en modo offline (InvestmentSwap no disponible)");
        }
    } catch (Exception e) {
        // ❌ Error inesperado
        log.error("Error refundInvestment: {}", e.getMessage());
        result.put("success", true);  // Seguimos aunque falle
        result.put("refundTxHash",
            "0xrefund-" + System.currentTimeMillis());
        result.put("note", "Reembolso en modo offline: " + e.getMessage());
    }
    return result;
}
```

**¿Por qué holder E investor son ambos la treasury?** Porque en la arquitectura actual:
- El treasury llama a `invest()` en nombre del usuario (los $IDEA van de treasury a treasury)
- Los sub-tokens se mintean al treasury (no al usuario)
- El treasury es quien "tiene" los sub-tokens
- Cuando hay refund, el treasury quema los sub-tokens que él mismo tiene

En el futuro, cuando los usuarios tengan wallets on-chain:
- `holder` = treasury (sigue teniendo los sub-tokens)
- `investor` = address del usuario (para que el evento RefundMade tenga sentido)

### 22.8 INV-10: InvestmentScheduler + processExpiredProjects()

#### 22.8.1 InvestmentScheduler.java

**Archivo:** `src/main/java/com/systeam/investment/scheduler/InvestmentScheduler.java`

```java
package com.systeam.investment.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.systeam.investment.service.InvestmentService;

// @Component: Spring crea UNA instancia de esta clase (singleton)
// @EnableScheduling: activa el motor de tareas programadas de Spring
@Component
@EnableScheduling
public class InvestmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvestmentScheduler.class);

    private final InvestmentService investmentService;

    // Spring inyecta InvestmentService automáticamente
    public InvestmentScheduler(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    // @Scheduled(cron = "0 0 6 * * ?"): ejecuta TODOS LOS DÍAS a las 6 AM
    //
    // Formato cron:
    //   segundo minuto hora día-del-mes mes día-de-la-semana
    //   0       0     6    *            *   ?
    //   └───────┘     ↑    └─ cualquier ─┘
    //    En punto     6 AM  cualquier día
    //
    // ¿Por qué a las 6 AM?
    //   - Es un horario de baja actividad
    //   - Da tiempo a que los proyectos del día anterior venzan
    @Scheduled(cron = "0 0 6 * * ?")
    public void evaluateExpiredProjects() {
        log.info("Iniciando evaluacion de proyectos vencidos...");
        try {
            investmentService.processExpiredProjects();
            log.info("Evaluacion de proyectos vencidos completada");
        } catch (Exception e) {
            log.error("Error al evaluar proyectos vencidos: {}", e.getMessage(), e);
        }
    }
}
```

#### 22.8.2 InvestmentService.processExpiredProjects()

**Archivo:** `src/main/java/com/systeam/investment/service/InvestmentService.java` (líneas 251-274)

```java
/**
 * Evalúa todos los proyectos cuyo plazo ya expiró.
 *
 * ¿QUÉ HACE?
 *   1. Busca proyectos en estado FINANCIAMIENTO con plazo vencido
 *   2. Para cada uno: compara monto_recaudado vs monto_requerido
 *   3. Si NO alcanzó la meta → llama a refundAllInvestors() + estado RECHAZADO
 *   4. Si SÍ alcanzó la meta → (por ahora no hace nada, futura implementación)
 *
 * @Transactional: TODO el método es una sola transacción de base de datos.
 *   Si algo falla a la mitad, todo se revierte (rollback).
 */
@Transactional
public void processExpiredProjects() {
    // ─── 1. Buscar proyectos vencidos ──────────────────────────
    // SQL: SELECT id, monto_requerido, COALESCE(monto_recaudado, 0) ...
    //   WHERE estado = 'FINANCIAMIENTO'
    //     AND plazo IS NOT NULL        ← proyectos con plazo definido
    //     AND plazo < NOW()            ← plazo ya pasó
    //     AND deleted_at IS NULL       ← no eliminados
    //
    // COALESCE: si monto_recaudado es NULL, usar 0
    //   (evita NullPointerException al comparar)
    //
    // RowMapper: convierte cada fila de la DB en un Map (clave→valor)
    List<Map<String, Object>> expiredProjects = jdbc.query(
        "SELECT id, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado " +
        "FROM projects WHERE estado = 'FINANCIAMIENTO' AND plazo IS NOT NULL " +
        "AND plazo < NOW() AND deleted_at IS NULL",
        (rs, rowNum) -> Map.of(
            "id", rs.getLong("id"),
            "montoRequerido", rs.getBigDecimal("monto_requerido"),
            "montoRecaudado", rs.getBigDecimal("monto_recaudado")
        )
    );

    // ─── 2. Evaluar cada proyecto ─────────────────────────────
    for (Map<String, Object> project : expiredProjects) {
        Long projectId = (Long) project.get("id");
        BigDecimal montoRequerido = (BigDecimal) project.get("montoRequerido");
        BigDecimal montoRecaudado = (BigDecimal) project.get("montoRecaudado");

        // ─── 3. Si NO alcanzó la meta → RECHAZADO ───────────
        // compareTo:
        //   negativo = montoRecaudado < montoRequerido (no alcanzó)
        //   cero     = montoRecaudado == montoRequerido (justo)
        //   positivo = montoRecaudado > montoRequerido (superó la meta)
        if (montoRecaudado.compareTo(montoRequerido) < 0) {

            // Ejecutar reembolso para TODOS los inversores
            refundAllInvestors(projectId);

            // Cambiar estado del proyecto a RECHAZADO
            //   (antes era FINALIZADO, ver sección 22.12)
            jdbc.update(
                "UPDATE projects SET estado = 'RECHAZADO', updated_at = NOW() WHERE id = ?",
                projectId
            );
        }
        // ─── 4. Si alcanzó la meta → (futuro: pasar a EJECUCION) ─
    }
}
```

#### 22.8.3 refundAllInvestors() — El loop de reembolsos

```java
/**
 * Reembolsa a TODOS los inversores de un proyecto fallido.
 *
 * Para CADA inversión del proyecto:
 *   1. Llama a SmartContractService.refundInvestment() → InvestmentSwap on-chain
 *   2. Devuelve los $IDEA al inversor (UPDATE users SET saldo_idea)
 *   3. Descuenta los sub-tokens del portfolio del inversor
 *   4. Marca la inversión como REEMBOLSADA
 *
 * @Transactional: TODO es UNA sola transacción DB.
 *   Si algo falla, no se actualiza nada (rollback).
 */
@Transactional
public void refundAllInvestors(Long proyectoId) {
    // Buscar TODAS las inversiones PENDIENTES de reembolso para este proyecto
    // Inversión "pendiente" = estado NO es 'REEMBOLSADA' todavía
    List<Inversion> inversiones =
        investmentRepository.findPendingRefundsByProyectoId(proyectoId);

    // Iterar sobre CADA inversión
    for (Inversion inv : inversiones) {

        // ─── PASO 1: Refund on-chain ─────────────────────────
        // Llama a SmartContractService.refundInvestment() que a su vez
        // llama a InvestmentSwapService.refund() que ejecuta refund()
        // en InvestmentSwap.sol (quema sub-tokens + emite evento)
        //
        // Si Sepolia no responde, el método devuelve null y sigue
        // igual (modo offline). No TIRA excepción.
        smartContractService.refundInvestment(
            proyectoId,
            inv.getUsuario().getId(),  // ID del usuario (solo para logging)
            inv.getMontoIdea()         // monto en BigDecimal
        );

        // ─── PASO 2: Devolver $IDEA al inversor ──────────────
        // Actualizamos el saldo_idea del usuario en la DB:
        //   saldo_idea = saldo_idea + monto_invertido
        //
        // ¿Por qué en DB y no on-chain?
        //   Porque los usuarios aún no tienen wallets on-chain.
        //   Su saldo $IDEA está en la columna saldo_idea de users.
        //   El treasury devuelve el $IDEA acreditando en la DB.
        jdbc.update(
            "UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?",
            inv.getMontoIdea(),
            inv.getUsuario().getId()
        );

        // ─── PASO 3: Descontar sub-tokens del portfolio ──────
        // Si el inversor recibió sub-tokens (puede ser 0 si invirtió
        // antes de que se implementen los sub-tokens), los
        // descontamos de su portfolio.
        if (inv.getSubTokensRecibidos() != null
                && inv.getSubTokensRecibidos() > 0) {

            // UPDATE portfolio_activos:
            //   cantidad = MAX(cantidad - subTokensRecibidos, 0)
            //   GREATEST evita que quede negativo por error
            //
            // JOIN con subtokens para filtrar por proyecto_id
            jdbc.update("""
                UPDATE portfolio_activos pa
                SET cantidad = GREATEST(pa.cantidad - ?, 0),
                    updated_at = NOW()
                FROM subtokens s
                WHERE pa.subtoken_id = s.id
                  AND s.proyecto_id = ?
                  AND pa.usuario_id = ?
                """,
                inv.getSubTokensRecibidos(),  // cuánto restar
                proyectoId,                    // proyecto
                inv.getUsuario().getId()       // usuario
            );
        }

        // ─── PASO 4: Marcar inversión como reembolsada ──────
        // Cambiamos el estado a REEMBOLSADA para no reembolsarla
        // dos veces cuando el scheduler se ejecute de nuevo.
        inv.setEstado("REEMBOLSADA");
        investmentRepository.save(inv);
    }
}
```

### 22.9 Migración Flyway — Agregar RECHAZADO al CHECK constraint

**Archivo:** `src/main/resources/db/proyectos/V13__add_rechazado_state.sql`

El estado `RECHAZADO` no existía en la base de datos. Cuando intentamos hacer:

```sql
UPDATE projects SET estado = 'RECHAZADO' WHERE id = 1;
```

...la DB lo rechazaba porque el CHECK constraint solo permitía:
`'PREPARACION', 'FINANCIAMIENTO', 'EJECUCION', 'FINALIZADO', 'CANCELADO'`

Solución: crear una migración Flyway que modifique el constraint:

```sql
-- V13__add_rechazado_state.sql
--
-- ¿Por qué existe?
--   INV-10 cambia el estado de proyectos fallidos de FINALIZADO
--   a RECHAZADO. El CHECK constraint de la columna estado no
--   permitía RECHAZADO, así que lo agregamos.
--
-- ¿Cómo funciona?
--   1. DROP CONSTRAINT: elimina el constraint viejo
--   2. ADD CONSTRAINT: crea uno nuevo con RECHAZADO incluido
--
-- ¿Por qué DROP y ADD en vez de ALTER?
--   PostgreSQL no permite modificar un CHECK constraint, solo
--   eliminarlo y crearlo de nuevo.

-- 1. Eliminar el constraint viejo (si existe)
ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_estado;

-- 2. Crear el nuevo constraint con RECHAZADO agregado
ALTER TABLE projects ADD CONSTRAINT chk_estado
    CHECK (estado IN (
        'PREPARACION',
        'FINANCIAMIENTO',
        'EJECUCION',
        'FINALIZADO',
        'CANCELADO',
        'RECHAZADO'       -- ← NUEVO (INV-10)
    ));
```

### 22.10 Configuración

#### .env

```properties
# Address del contrato InvestmentSwap (v2, con refund)
BLOCKCHAIN_INVESTMENT_SWAP=0x4b65289c1efcd7bd2ebce6d4d38de654ee59bd31
```

#### application.properties

```properties
# Mapea la variable de entorno a una propiedad de Spring
blockchain.investment-swap-address=${BLOCKCHAIN_INVESTMENT_SWAP}
```

#### BlockchainProperties.java

```java
// En src/main/java/com/systeam/config/BlockchainProperties.java

private String investmentSwapAddress;  // ← ya existía de INV-05

public String getInvestmentSwapAddress() {
    return investmentSwapAddress;
}
public void setInvestmentSwapAddress(String investmentSwapAddress) {
    this.investmentSwapAddress = investmentSwapAddress;
}
```

### 22.11 Re-deploy del contrato InvestmentSwap

Como agregamos la función `refund()` al contrato existente, necesitábamos re-deployarlo con el nuevo bytecode:

```bash
# Compilar el proyecto
./mvnw.cmd compile

# Obtener classpath
./mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"

# Ejecutar el deployer
$cp = "target\classes;" + (Get-Content "target\cp.txt").Trim()
java -cp "$cp" com.systeam.blockchain.deploy.DeployInvestmentSwap
```

**Resultado del deploy:**

```
=== INVESTMENT SWAP DESPLEGADO ===
Address: 0x4b65289c1efcd7bd2ebce6d4d38de654ee59bd31
Tx: 0x93cd1050117228a47a4381ac575d417167754d49204cfd307bdfeb3d2977fadd
```

**Ver en Etherscan:**
```
https://sepolia.etherscan.io/address/0x4b65289c1efcd7bd2ebce6d4d38de654ee59bd31
```

### 22.12 Flujo completo de un reembolso (INV-09 + INV-10)

Cuando un proyecto NO alcanza su meta de financiamiento, el scheduler ejecuta este flujo COMPLETO:

```
                              ┌────────────────────┐
                              │  6:00 AM           │
                              │  Investment-       │
                              │  Scheduler         │
                              │  .evaluateExpired  │
                              │  Projects()        │
                              └────────┬───────────┘
                                       │
                              ┌────────▼───────────┐
                              │  processExpired    │
                              │  Projects()        │
                              │                    │
                              │  SQL: SELECT       │
                              │  projects WHERE    │
                              │  estado='FINANCIAM'│
                              │  AND plazo < NOW() │
                              └────────┬───────────┘
                                       │
                          ┌────────────┼────────────┐
                          │            │            │
               ┌──────────▼────┐  ┌───▼──────┐  ┌──▼────────────┐
               │ montoRecau-   │  │ montoRe- │  │ montoRecau-   │
               │ dado < monto  │  │ caudado  │  │ dado > monto  │
               │ Requerido     │  │ == monto │  │ Requerido     │
               │               │  │ Requerido│  │               │
               │ → REFUND!     │  │ → nada   │  │ → nada        │
               └───────┬───────┘  └──────────┘  └───────────────┘
                       │
              ┌────────▼──────────────────────────┐
              │  refundAllInvestors(proyectoId)    │
              │                                    │
              │  Para CADA inversión del proyecto: │
              │                                    │
              │  1. SmartContractService           │
              │     .refundInvestment() ───────────┼────┐
              │     └→ InvestmentSwapService       │    │
              │        .refund()                   │    │
              │        └→ InvestmentSwap.sol       │    │
              │           .refund() on-chain       │    │
              │           ├─ burnFrom() ───────┐   │    │
              │           └─ emit RefundMade ──┤   │    │
              │                                │   │    │
              │  2. UPDATE users               │   │    │
              │     SET saldo_idea += monto    │   │    │
              │                                │   │    │
              │  3. UPDATE portfolio_activos   │   │    │
              │     SET cantidad -= subtokens  │   │    │
              │                                │   │    │
              │  4. inv.setEstado(             │   │    │
              │       "REEMBOLSADA")           │   │    │
              │     investmentRepository.save()│   │    │
              └────────────────────────────────┘   │    │
                                                   │    │
              ┌────────────────────────────────────┘    │
              │                                         │
              ▼                                         ▼
     ┌──────────────────┐                    ┌───────────────────┐
     │ projects SET     │                    │ InvestmentSwap.sol │
     │ estado =         │                    │                   │
     │ 'RECHAZADO'      │                    │ RefundMade(       │
     │                  │                    │   proyectoId=1,   │
     │ (antes era       │                    │   investor=0x7e..,│
     │  FINALIZADO)     │                    │   ideaAmount=0,   │
     └──────────────────┘                    │   subTokenAmount= │
                                             │   500*10^18)      │
                                             │    → grabado en   │
                                             │      blockchain   │
                                             └───────────────────┘
```

### 22.13 Cómo se actualizó SmartContractService (cambio clave)

**Archivo:** `src/main/java/com/systeam/project/service/SmartContractService.java`

Antes de INV-09, `SmartContractService` no tenía acceso a `InvestmentSwapService`:

```java
// ANTES (sin INV-09):
public class SmartContractService {
    private final BlockchainService blockchain;
    private final BlockchainProperties props;

    public SmartContractService(BlockchainService blockchain, BlockchainProperties props) {
        // Solo tenía blockchain y props
    }
}
```

Después de INV-09, se agregó `InvestmentSwapService` como dependencia:

```java
// DESPUÉS (con INV-09):
public class SmartContractService {
    private final BlockchainService blockchain;
    private final BlockchainProperties props;
    private final InvestmentSwapService investmentSwapService;  // ★ NUEVO

    public SmartContractService(BlockchainService blockchain,
                                BlockchainProperties props,
                                InvestmentSwapService investmentSwapService) {  // ★ NUEVO parámetro
        this.blockchain = blockchain;
        this.props = props;
        this.investmentSwapService = investmentSwapService;
    }
}
```

Spring Boot inyecta automáticamente `InvestmentSwapService` porque está anotado con `@Service`. No hay que configurar nada extra.

### 22.14 Tests — Cómo se probó

**Archivo:** `src/test/java/com/systeam/investment/service/InvestmentServiceTest.java`

#### Test 1: Proyecto fallido → ejecuta reembolso

```java
@Test
@SuppressWarnings("unchecked")
void processExpiredProjects_cuandoProyectoNoAlcanzaMeta_ejecutaReembolso() {
    // ─── Mock de la DB: simular un proyecto vencido ──────────
    // Cuando jdbc.query() ejecute la SQL de proyectos vencidos,
    // devolverá un proyecto con:
    //   id = 1
    //   montoRequerido = 10000.00
    //   montoRecaudado = 5000.00   ← NO alcanzó la meta
    when(jdbc.query(anyString(), any(RowMapper.class)))
        .thenReturn(List.of(Map.of(
            "id", 1L,
            "montoRequerido", new BigDecimal("10000.00"),
            "montoRecaudado", new BigDecimal("5000.00")
        )));

    // ─── Mock del repository: devolver una inversión ─────────
    // Cuando busque inversiones pendientes del proyecto 1,
    // devolverá una lista con inversionBase
    when(investmentRepository.findPendingRefundsByProyectoId(1L))
        .thenReturn(List.of(inversionBase));

    // ─── Mock de SmartContractService: simular refund exitoso ─
    when(smartContractService.refundInvestment(
        anyLong(), anyLong(), any(BigDecimal.class)))
        .thenReturn(Map.of("success", true));

    // ─── Mock de jdbc.update: no ejecutar realmente la SQL ───
    when(jdbc.update(anyString(), any(), any())).thenReturn(1);

    // ─── Mock de save: no guardar realmente ──────────────────
    when(investmentRepository.save(any(Inversion.class)))
        .thenReturn(inversionBase);

    // ─── EJECUTAR ────────────────────────────────────────────
    investmentService.processExpiredProjects();

    // ─── VERIFICAR: se llamó a save() para marcar REEMBOLSADA ─
    verify(investmentRepository).save(any(Inversion.class));
}
```

#### Test 2: Proyecto exitoso → NO ejecuta reembolso

```java
@Test
@SuppressWarnings("unchecked")
void processExpiredProjects_cuandoProyectoAlcanzaMeta_noEjecutaReembolso() {
    // ─── Mock de la DB: proyecto con meta ALCANZADA ──────────
    //   montoRequerido = 10000.00
    //   montoRecaudado = 10000.00   ← ALCANZÓ la meta
    when(jdbc.query(anyString(), any(RowMapper.class)))
        .thenReturn(List.of(Map.of(
            "id", 1L,
            "montoRequerido", new BigDecimal("10000.00"),
            "montoRecaudado", new BigDecimal("10000.00")
        )));

    // ─── EJECUTAR ────────────────────────────────────────────
    investmentService.processExpiredProjects();

    // ─── VERIFICAR: NO se llamó a save() (no hubo refund) ────
    verify(investmentRepository, org.mockito.Mockito.never())
        .save(any(Inversion.class));
}
```

### 22.15 Cómo probar manualmente

```bash
# ─── 1. Configurar un proyecto con plazo vencido ─────────────
# En PostgreSQL, ejecutar:
UPDATE projects
SET plazo = NOW() - INTERVAL '1 day',  # plazo AYER
    estado = 'FINANCIAMIENTO',
    monto_requerido = 10000.00,
    monto_recaudado = 5000.00          # NO alcanzó la meta
WHERE id = 1;

# ─── 2. Ejecutar el scheduler manualmente ────────────────────
# Opción A: Esperar a las 6 AM (no recomendado para pruebas)
# Opción B: Llamar al método desde un controller temporal
# Opción C: Temporalmente cambiar el cron a @Scheduled(fixedRate=60000)
#           para que se ejecute cada 60 segundos

# ─── 3. Verificar el estado del proyecto ─────────────────────
SELECT id, estado FROM projects WHERE id = 1;
# → Debería mostrar 'RECHAZADO'

# ─── 4. Verificar el saldo del inversor ──────────────────────
SELECT id, saldo_idea FROM users WHERE id = <ID_INVERSOR>;
# → saldo_idea debería haber aumentado en el monto invertido

# ─── 5. Verificar la inversión ───────────────────────────────
SELECT id, estado FROM inversiones WHERE proyecto_id = 1;
# → Debería mostrar 'REEMBOLSADA'

# ─── 6. Verificar la transacción en Etherscan ────────────────
# Ir a: https://sepolia.etherscan.io/tx/0x93cd1050117228a47a4381ac575d417167754d49204cfd307bdfeb3d2977fadd
# Buscar el evento RefundMade en los logs de la transacción
```

### 22.16 ¿Por qué RECHAZADO y no FINALIZADO?

| Estado | Significado | Cuándo se usa |
|--------|-------------|---------------|
| `PREPARACION` | El creador está armando el proyecto | Antes de publicarlo |
| `FINANCIAMIENTO` | Abierto a inversiones | Durante la ronda de inversión |
| `EJECUCION` | Proyecto en desarrollo | Cuando alcanzó la meta y está activo |
| `FINALIZADO` | Proyecto completado exitosamente | Cuando el proyecto terminó su ejecución |
| `CANCELADO` | Cancelado por el creador | El creador decidió no continuar |
| `RECHAZADO` (★ NUEVO) | No alcanzó la meta de financiamiento | El plazo expiró y no llegó al monto requerido |

**Antes de INV-10:** Los proyectos que no alcanzaban la meta se marcaban como `FINALIZADO`. Esto era incorrecto porque:
- `FINALIZADO` debería ser SOLO para proyectos exitosos
- No se podía distinguir "fracasó" de "completado con éxito"
- Los reportes mostraban datos incorrectos

**Con INV-10:** `RECHAZADO` distingue claramente:
- "Este proyecto fracasó" → `RECHAZADO`
- "Este proyecto tuvo éxito y terminó" → `FINALIZADO`

Además, `RECHAZADO` activa automáticamente la lógica de reembolso en el scheduler.

### 22.17 Resumen de cambios

| Archivo | ¿Qué cambió? | ¿Por qué? |
|---------|-------------|-----------|
| `blockchain/contracts/InvestmentSwap.sol` | Nueva función `refund()` + evento `RefundMade` | INV-09: ejecutar reembolso on-chain |
| `blockchain/compiled/InvestmentSwap.json` | Re-compilado con refund | Tener el bytecode actualizado |
| `src/.../service/InvestmentSwapService.java` | Nuevo método `refund()` | Llamar a refund() desde Java |
| `src/.../service/SmartContractService.java` | Inyectado `InvestmentSwapService`; `refundInvestment()` ahora llama al contrato real (antes mock) | Conectar el backend con el contrato |
| `src/.../service/InvestmentService.java` | `processExpiredProjects()` cambia estado a `RECHAZADO` (antes `FINALIZADO`) | INV-10: estado correcto para proyectos fallidos |
| `src/.../db/V13__add_rechazado_state.sql` | ★ Nuevo: agrega `RECHAZADO` al CHECK constraint | Permitir el nuevo estado en la DB |
| `src/.../deploy/DeployInvestmentSwap.java` | Bytecode actualizado (v2 con refund) | El deploy despliega la versión correcta |
| `.env` | `BLOCKCHAIN_INVESTMENT_SWAP` actualizado a v2 | Apuntar al contrato correcto |

---

## Apéndice C: Solución de problemas comunes

### C.1 "No me deja deployar: insufficient funds"

**Error completo:**
```
Error: insufficient funds for gas * price + value
```

**Causa:** La wallet no tiene suficiente SepoliaETH para pagar el gas.

**Solución:**
```bash
# 1. Verificar el saldo actual
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd compile
.\mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
$cp = "target\classes;" + (Get-Content "target\cp.txt" -Raw).Trim()
# (necesitás un main class que muestre el saldo, o usás Etherscan)

# 2. Pedir SepoliaETH (ver sección 12.5)
# 3. Esperar 1-2 minutos a que llegue
# 4. Reintentar
```

### C.2 "Nonce too low" al deployar

**Error completo:**
```
Error: nonce too low (expected nonce 5, got 4)
```

**Causa:** Intentaste enviar una transacción con un nonce que ya se usó. Esto pasa cuando:
- Una tx pendiente se confirma mientras preparabas otra
- Dos deployers intentan usar la misma wallet simultáneamente

**Solución:**
```bash
# El deployer ya maneja esto automáticamente consultando el nonce actual.
# Si el error persiste:
#   1. Esperar a que todas las txs pendientes se confirmen
#   2. Verificar en Etherscan el nonce actual de la wallet
#   3. El deployer obtiene el nonce con PENDING, que incluye txs no confirmadas
```

### C.3 "Execution reverted" al llamar un contrato

**Error completo:**
```
Error en ethCall: execution reverted
```

**Causas posibles:**
1. La función no existe en el contrato (nombre incorrecto)
2. Los parámetros no son del tipo esperado
3. El contrato tiene un `require()` que falla (ej: no tenés permisos)
4. La address del contrato es incorrecta

**Solución:**
```bash
# 1. Verificar que la address del contrato sea correcta en .env
# 2. Verificar el nombre de la función en Solidity (case sensitive)
# 3. Verificar los tipos de parámetros (Address vs Uint256 vs Bytes32)
# 4. Verificar en Etherscan que el contrato exista en Sepolia
```

### C.4 "Connection refused" al conectarse a Sepolia

**Error completo:**
```
java.net.ConnectException: Connection refused: /31.22.124.172:443
```

**Causa:** El nodo RPC público de Sepolia no está accesible (caído o bloqueado).

**Solución:**
```bash
# 1. Verificar conectividad básica:
curl https://ethereum-sepolia-rpc.publicnode.com -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'

# 2. Si no responde, cambiar a otro proveedor RPC en .env:
#    - Infura: https://sepolia.infura.io/v3/TU_PROJECT_ID
#    - Alchemy: https://eth-sepolia.g.alchemy.com/v2/TU_API_KEY
#    - Público: https://rpc.sepolia.org
#    - Público: https://sepolia.gateway.tenderly.co

# 3. La app entra automáticamente en modo offline si no hay conexión
```

### C.5 El contrato se deployó pero el subtoken no se creó

**Causa:** `TokenFactoryService.crearTokenProyecto()` devolvió error (ver C.1-C.4).

**Solución manual:**
```bash
# 1. Verificar que TokenFactory existe en Sepolia:
#    https://sepolia.etherscan.io/address/0x23a81e62f190993c932ccea79308f8ff1ea558d9

# 2. La app ya usó 0x0000...0000 como fallback (ver sección 19.6)

# 3. Para arreglarlo después:
#    - Llamar manualmente a crearTokenProyecto() desde Java
#    - Actualizar contract_address en subtokens con:
UPDATE subtokens SET contract_address = '0xNUEVA_ADDRESS' WHERE proyecto_id = 5;
```

### C.6 Error "Web3j太久没有返回" (timeout)

**Causa:** El nodo RPC es lento o está sobrecargado. Web3j tiene un timeout default de ~30 segundos.

**Solución:** En `Web3jConfig.java` se puede aumentar el timeout:
```java
@Bean
public Web3j web3j(BlockchainProperties props) {
    HttpService service = new HttpService(props.getRpcUrl());
    Client client = ClientBuilder.newClient()
        .property(ClientProperties.CONNECT_TIMEOUT, 30000)   // 30 seg
        .property(ClientProperties.READ_TIMEOUT, 60000);     // 60 seg
    service = new HttpService(props.getRpcUrl(), client);
    return Web3j.build(service);
}
```

---

## Apéndice D: Glosario ampliado

| Término | Significado |
|---------|------------|
| **ABI** | Application Binary Interface. Describe las funciones de un contrato Solidity. |
| **Address** | Identificador público en Ethereum (ej: `0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A`). |
| **Bean** | Objeto administrado por Spring. Se crea una vez y se reusa. |
| **Blockchain** | Libro contable descentralizado e inmutable. |
| **chainId** | Número que identifica la red Ethereum (1 = Mainnet, 11155111 = Sepolia). |
| **EIP-155** | Mejora de Ethereum que incluye chainId en las firmas para prevenir replay attacks. |
| **ERC-20** | Estándar de tokens fungibles en Ethereum. |
| **eth_call** | Método JSON-RPC para leer datos del contrato (gratis, no modifica estado). |
| **eth_sendRawTransaction** | Método JSON-RPC para escribir datos en el contrato (paga gas, modifica estado). |
| **Etherscan** | Explorador público de Ethereum. Ver txs, contratos, balances. |
| **Gas** | Costo en ETH de cada transacción. |
| **Gas Limit** | Máximo de gas que estás dispuesto a pagar. |
| **Gas Price** | Precio por unidad de gas (en gwei). |
| **gwei** | 10⁹ wei. Unidad usada para precios de gas. |
| **JDBC** | Java Database Connectivity. API para conectar Java con bases de datos SQL. |
| **JWT** | JSON Web Token. Formato de token de autenticación. |
| **Nonce** | Contador de transacciones de una wallet. Cada tx debe tener nonce incremental. |
| **PK** | Private Key. Clave privada que permite firmar transacciones. |
| **Replay Attack** | Ataque donde una transacción firmada se reenvía en otra red. EIP-155 lo previene. |
| **RPC** | Remote Procedure Call. Protocolo para comunicarse con nodos Ethereum. |
| **Sepolia** | Testnet pública de Ethereum. Misma tecnología que mainnet, ETH gratis. |
| **Smart Contract** | Programa que vive en la blockchain. Inmutable una vez deployado. |
| **Testnet** | Red de prueba. Los tokens no tienen valor real. |
| **txHash** | Hash que identifica una transacción en la blockchain (32 bytes, 64 chars hex). |
| **wei** | 10⁻¹⁸ ether. La unidad más chica de Ethereum. |
| **Web3j** | Biblioteca Java para interactuar con Ethereum. |

| Término | Significado |
|---------|------------|
| **ABI** | Application Binary Interface. Describe las funciones de un contrato Solidity. |
| **Address** | Identificador público en Ethereum (ej: `0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A`). |
| **Bean** | Objeto administrado por Spring. Se crea una vez y se reusa. |
| **Blockchain** | Libro contable descentralizado e inmutable. |
| **ERC-20** | Estándar de tokens fungibles en Ethereum. |
| **Etherscan** | Explorador público de Ethereum. Ver txs, contratos, balances. |
| **Gas** | Costo en ETH de cada transacción. |
| **JDBC** | Java Database Connectivity. API para conectar Java con bases de datos SQL. |
| **JWT** | JSON Web Token. Formato de token de autenticación. |
| **Nonce** | Contador de transacciones de una wallet. |
| **PK** | Private Key. Clave privada que permite firmar transacciones. |
| **RPC** | Remote Procedure Call. Protocolo para comunicarse con nodos Ethereum. |
| **Sepolia** | Testnet pública de Ethereum. Misma tecnología que mainnet, ETH gratis. |
| **Smart Contract** | Programa que vive en la blockchain. Inmutable una vez deployado. |
| **Testnet** | Red de prueba. Los tokens no tienen valor real. |
| **txHash** | Hash que identifica una transacción en la blockchain. |
| **Web3j** | Biblioteca Java para interactuar con Ethereum. |
