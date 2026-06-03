# API Systeam — Gestión de Proyectos e Inversiones

**Base URL:** `http://localhost:8081`  
**Auth URL:** `http://localhost:8080` (módulo de usuarios)  
**Autenticación:** JWT Bearer Token — `Authorization: Bearer <token>`

---

## Tabla de Contenidos

1. [Proyectos](#1-proyectos--apiprojects)
2. [Inversiones](#2-inversiones--apiinvestments)
3. [Dividendos](#3-dividendos--apidividendos)
4. [Wallet](#4-wallet--apiwallet)
5. [Dashboard](#5-dashboard--apidashboard)
6. [Estados y Roles](#6-estados-y-roles)
7. [Códigos de Error](#7-códigos-de-error)

---

## 1. Proyectos — `/api/projects`

### 1.1 Catálogo público con filtros

```
GET /api/projects/catalog
```

**Autenticación:** No requerida  
**Query params:**

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `estado` | string | — | Filtrar por estado (`FINANCIAMIENTO`, `EJECUCION`, etc.) |
| `search` | string | — | Buscar en título o descripción |
| `page` | int | 0 | Página (0-indexed) |
| `size` | int | 10 | Elementos por página |

**Response 200:**
```json
{
  "content": [
    {
      "id": 5,
      "titulo": "App de gestión inteligente",
      "descripcion": "Plataforma para...",
      "estado": "FINANCIAMIENTO",
      "montoRequerido": 103875.00,
      "montoRecaudado": 7060.00,
      "plazo": "2026-06-15T00:00:00",
      "creadorId": 3,
      "creadoEn": "2026-05-01T10:00:00"
    }
  ],
  "totalElements": 8,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### 1.2 Listar todos los proyectos (paginado)

```
GET /api/projects?page=0&size=10
```

**Autenticación:** Requerida — permiso `project:read`  
**Response 200:** Mismo formato que catálogo.

---

### 1.3 Obtener proyecto por ID

```
GET /api/projects/{id}
```

**Autenticación:** Requerida — permiso `project:read`  
**Response 200:**
```json
{
  "id": 5,
  "titulo": "App de gestión inteligente",
  "descripcion": "Plataforma para...",
  "estado": "FINANCIAMIENTO",
  "montoRequerido": 103875.00,
  "montoRecaudado": 7060.00,
  "plazo": "2026-06-15T00:00:00",
  "creadorId": 3,
  "smartContractAddress": null,
  "creadoEn": "2026-05-01T10:00:00",
  "actualizadoEn": "2026-05-20T14:30:00"
}
```

---

### 1.4 Mis proyectos

```
GET /api/projects/my-projects?page=0&size=10
```

**Autenticación:** Requerida  
**Response 200:** Mismo formato que catálogo, filtrado por el usuario autenticado.

---

### 1.5 Crear proyecto

```
POST /api/projects
```

**Autenticación:** Requerida — permiso `project:create`

**Request Body:**
```json
{
  "titulo": "Mi Proyecto Innovador",
  "descripcion": "Descripción detallada del proyecto...",
  "montoRequerido": 10000.00,
  "plazo": "2026-08-01T00:00:00",
  "gobernanzaComunidad": false,
  "cupoMaximoTokens": 1000,
  "valorNominalToken": 10.00
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `titulo` | string | ✅ | Máx. 200 caracteres |
| `descripcion` | string | ✅ | Máx. 8000 caracteres |
| `montoRequerido` | decimal | ✅ | Mayor a 0 |
| `plazo` | datetime | ❌ | Fecha límite de financiamiento |
| `gobernanzaComunidad` | boolean | ❌ | Si requiere votación comunitaria |
| `cupoMaximoTokens` | int | ❌ | Mínimo 1 |
| `valorNominalToken` | decimal | ❌ | Mayor a 0 |

**Response 201:**
```json
{
  "id": 12,
  "titulo": "Mi Proyecto Innovador",
  "descripcion": "Descripción detallada del proyecto...",
  "estado": "PREPARACION",
  "montoRequerido": 10000.00,
  "montoRecaudado": 0.00,
  "plazo": "2026-08-01T00:00:00",
  "creadorId": 7,
  "smartContractAddress": null,
  "creadoEn": "2026-05-23T12:00:00",
  "actualizadoEn": "2026-05-23T12:00:00"
}
```

---

### 1.6 Actualizar proyecto

```
PUT /api/projects/{id}
```

**Autenticación:** Requerida — permiso `project:update` (solo en estado `PREPARACION`)

**Request Body:** (todos los campos son opcionales)
```json
{
  "titulo": "Mi Proyecto v2",
  "descripcion": "Nueva descripción...",
  "montoRequerido": 15000.00,
  "plazo": "2026-09-01T00:00:00",
  "gobernanzaComunidad": true,
  "cupoMaximoTokens": 1500,
  "valorNominalToken": 10.00
}
```

**Response 200:** Objeto proyecto actualizado (mismo formato que 1.5).

---

### 1.7 Cambiar estado del proyecto

```
PATCH /api/projects/{id}/status?status=FINANCIAMIENTO
```

**Autenticación:** Requerida — rol `ADMIN` o permiso `project:update`

**Query param:** `status` — nuevo estado a asignar (en mayúsculas)

**Transiciones válidas:**
- `PREPARACION` → `FINANCIAMIENTO`
- `FINANCIAMIENTO` → `EJECUCION` (100% financiado)
- `FINANCIAMIENTO` → `RECHAZADO` (plazo vencido sin fondos)
- `EJECUCION` → `FINALIZADO`

**Response 200:** Objeto proyecto con el nuevo estado.

---

### 1.8 Boost de proyecto (marcar destacado)

```
POST /api/projects/{id}/boost
```

**Autenticación:** Requerida — permiso `project:update`  
**Response 200:** Sin cuerpo (vacío).

---

### 1.9 Evaluar estados de proyectos (scheduler manual)

```
POST /api/projects/evaluate-states
```

**Autenticación:** Requerida — rol `ADMIN`  
Evalúa todos los proyectos en `FINANCIAMIENTO` cuyo plazo venció y ejecuta devoluciones si no alcanzaron el monto.

**Response 204:** Sin cuerpo.

---

## 2. Inversiones — `/api/investments`

### 2.1 Validar inversión (pre-check)

```
POST /api/investments/validate
```

**Autenticación:** No requerida  
Verifica si la inversión es posible **sin ejecutarla**.

**Request Body:**
```json
{
  "proyectoId": 5,
  "montoIdea": 100.00
}
```

**Response 200:**
```json
{
  "valido": true,
  "mensaje": "Inversion valida",
  "cupoDisponible": 31955,
  "precioSubtoken": 3.25,
  "subTokensARecebir": 30
}
```

**Response cuando no es válido:**
```json
{
  "valido": false,
  "mensaje": "El monto es insuficiente para recibir al menos 1 sub-token",
  "cupoDisponible": 31955,
  "precioSubtoken": 3.25,
  "subTokensARecebir": 0
}
```

---

### 2.2 Crear inversión

```
POST /api/investments
```

**Autenticación:** Requerida — permiso `investment:create`

**Request Body:**
```json
{
  "proyectoId": 5,
  "montoIdea": 100.00,
  "txHash": "0xabc123...def456"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `proyectoId` | long | ✅ | ID del proyecto a invertir |
| `montoIdea` | decimal | ✅ | Mayor a 0 |
| `txHash` | string | ✅ | Hash de la transacción blockchain |

**Response 201:**
```json
{
  "id": 42,
  "usuarioId": 7,
  "proyectoId": 5,
  "proyectoTitulo": "App de gestión inteligente",
  "proyectoEstado": "FINANCIAMIENTO",
  "montoIdea": 100.00,
  "subTokensRecibidos": 30,
  "txHash": "0xabc123...def456",
  "estado": "CONFIRMADA",
  "createdAt": "2026-05-23T12:05:00",
  "updatedAt": "2026-05-23T12:05:00"
}
```

**Errores posibles:**
- `400` — Proyecto no en financiamiento / saldo insuficiente / cupo agotado / txHash duplicado

---

### 2.3 Obtener inversión por ID

```
GET /api/investments/{id}
```

**Autenticación:** Requerida — permiso `investment:read`

**Response 200:** Mismo formato que 2.2.

---

### 2.4 Historial de inversiones del usuario

```
GET /api/investments/history?page=0&size=10
```

**Autenticación:** Requerida — permiso `investment:read`

**Response 200:**
```json
{
  "content": [
    {
      "id": 42,
      "usuarioId": 7,
      "proyectoId": 5,
      "proyectoTitulo": "App de gestión inteligente",
      "proyectoEstado": "FINANCIAMIENTO",
      "montoIdea": 100.00,
      "subTokensRecibidos": 30,
      "txHash": "0xabc123...def456",
      "estado": "CONFIRMADA",
      "createdAt": "2026-05-23T12:05:00",
      "updatedAt": "2026-05-23T12:05:00"
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

## 3. Dividendos — `/api/dividendos`

### 3.1 Crear reparto de dividendos

```
POST /api/dividendos/proyecto/{proyectoId}?monto=5000
```

**Autenticación:** Requerida — rol `ADMIN`  
Solo para proyectos en estado `EJECUCION` o `FINALIZADO`.

**Query param:** `monto` — monto total a repartir (BigDecimal)

**Response 201:**
```json
{
  "id": 1,
  "mensaje": "Reparto de dividendos creado exitosamente"
}
```

**Errores posibles:**
- `400` — Proyecto no en EJECUCION/FINALIZADO o sin subtokens colocados

---

### 3.2 Listar repartos de un proyecto

```
GET /api/dividendos/proyecto/{proyectoId}
```

**Autenticación:** Requerida — permiso `investment:read`

**Response 200:**
```json
[
  {
    "id": 1,
    "proyectoId": 5,
    "montoTotal": 5000.00,
    "montoPorSubtoken": 0.1563,
    "fechaReparto": "2026-05-23T12:00:00",
    "createdAt": "2026-05-23T12:00:00"
  }
]
```

---

### 3.3 Reclamar dividendos

```
POST /api/dividendos/{dividendoId}/reclamar
```

**Autenticación:** Requerida — permiso `investment:read`  
El usuario recibe el monto proporcional a sus subtokens. El saldo se acredita en `saldo_idea`.

**Response 200:**
```json
{
  "mensaje": "Dividendos reclamados exitosamente"
}
```

**Errores posibles:**
- `400` — El usuario no tiene subtokens en ese proyecto o ya reclamó

---

### 3.4 Mis reclamos de dividendos

```
GET /api/dividendos/mis-reclamos
```

**Autenticación:** Requerida — permiso `investment:read`

**Response 200:**
```json
[
  {
    "id": 10,
    "dividendoId": 1,
    "proyectoId": 5,
    "subtokenId": 3,
    "cantidadSubtokens": 30,
    "montoRecibido": 4.69,
    "reclamadoEn": "2026-05-23T12:30:00",
    "montoTotal": 5000.00,
    "montoPorSubtoken": 0.1563
  }
]
```

---

## 4. Wallet — `/api/wallet`

### 4.1 Resumen de wallet del usuario

```
GET /api/wallet/summary
```

**Autenticación:** Requerida

**Response 200:**
```json
{
  "usuarioId": 7,
  "saldoIdea": 850.00,
  "portfolio": [
    {
      "subtokenId": 3,
      "proyectoId": 5,
      "proyectoTitulo": "App de gestión inteligente",
      "cantidad": 30,
      "precioActual": 3.25,
      "valorTotal": 97.50
    }
  ],
  "totalPortfolioValor": 97.50
}
```

---

## 5. Dashboard — `/api/dashboard`

### 5.1 Estadísticas globales

```
GET /api/dashboard/stats
```

**Autenticación:** Requerida — rol `ADMIN`

**Response 200:**
```json
{
  "totalProyectos": 12,
  "proyectosEnFinanciamiento": 4,
  "proyectosEnEjecucion": 3,
  "proyectosFinalizados": 5,
  "totalInversiones": 87,
  "montoTotalRecaudado": 142500.00,
  "totalUsuarios": 34
}
```

---

## 6. Estados y Roles

### Estados de un proyecto

| Estado | Descripción |
|--------|-------------|
| `PREPARACION` | Creado, editable, no visible para inversores |
| `FINANCIAMIENTO` | Recibiendo inversiones |
| `EJECUCION` | Financiamiento completado, en desarrollo |
| `FINALIZADO` | Proyecto terminado |
| `RECHAZADO` | Plazo vencido sin alcanzar el monto requerido — inversiones devueltas |

### Estados de una inversión

| Estado | Descripción |
|--------|-------------|
| `CONFIRMADA` | Inversión registrada y activa |
| `REEMBOLSADA` | Devuelta por proyecto rechazado |

### Roles y permisos relevantes

| Rol / Permiso | Acceso |
|---------------|--------|
| `ADMIN` | Crear repartos de dividendos, evaluar estados, dashboard stats |
| `project:create` | Crear proyectos |
| `project:update` | Editar, cambiar estado y hacer boost de proyectos |
| `project:read` | Ver proyectos (listar y detalle) |
| `investment:read` | Ver inversiones, historial, dividendos, wallet |
| `investment:create` | Crear inversiones |

---

## 7. Códigos de Error

| Código | Descripción |
|--------|-------------|
| `200` | OK |
| `201` | Creado exitosamente |
| `204` | Sin contenido (operación exitosa sin respuesta) |
| `400` | Error de validación en el body o error de negocio |
| `401` | No autenticado o token inválido |
| `403` | Sin permisos suficientes |
| `404` | Recurso no encontrado |
| `409` | Conflicto de estado o datos duplicados |
| `500` | Error interno del servidor |

### Formato de error de validación (400)

```json
{
  "titulo": "El titulo es obligatorio",
  "montoRequerido": "El monto requerido es obligatorio"
}
```

### Formato de error de negocio (400)

```json
{
  "error": "El proyecto no está en estado de financiamiento"
}
```

---

## Notas de integración

- El token JWT se obtiene en `http://localhost:8080/auth/login` y es válido para todos los módulos.
- El precio de los subtokens es **dinámico**: sube según oferta/demanda (a más vendidos, más caro) **y** según el rendimiento del proyecto (a más avanzado, más caro). Usar `/api/investments/validate` antes de confirmar para mostrar el precio actualizado.
- Las devoluciones ante proyecto rechazado son **automáticas**: el scheduler evalúa proyectos con plazo vencido y acredita el `saldo_idea` a cada inversor.
- El campo `saldo_idea` en el usuario es el balance de tokens IDEA (moneda interna). Se descuenta al invertir y se acredita al reclamar dividendos o recibir reembolsos.
- `POST /api/projects/evaluate-states` devuelve **204 No Content** (sin body) cuando se ejecuta exitosamente.
