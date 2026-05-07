# API Systeam - Modulo de Gestion de Proyectos

## Tabla de Contenidos
1. [Informacion General](#informacion-general)
2. [Autenticacion](#autenticacion)
3. [Endpoints de Proyectos](#endpoints-de-proyectos)
4. [Modelo de Datos](#modelo-de-datos)
5. [Estados del Proyecto](#estados-del-proyecto)
6. [Códigos de Error](#codigos-de-error)
7. [Ejemplos de Uso](#ejemplos-de-uso)

---

## Informacion General

**Base URL:** `http://localhost:8081`

**Tipo de autenticacion:** JWT (JSON Web Token) - compartido con modulo de usuarios

**Encabezados requeridos:** Para endpoints protegidos, incluir el token JWT en el header:
```
Authorization: Bearer <token_jwt>
```

---

## Autenticacion

La autenticacion se gestiona a traves del modulo de usuarios (`http://localhost:8080/auth/login`). El token JWT obtenido es valido para este modulo.

---

## Endpoints de Proyectos

### 1. Crear proyecto

Crea un nuevo proyecto en estado "PREPARACION".

- **Metodo:** `POST`
- **Endpoint:** `/api/projects`
- **Requiere autenticacion:** Si
- **Permisos necesarios:** `project:create`

#### Request Body
```json
{
  "title": "string (requerido, max 200 caracteres)",
  "description": "string (requerido, max 5000 caracteres)",
  "objective": "string (requerido, max 3000 caracteres)",
  "requiredAmount": "number (requerido, mayor a 0)",
  "cantidadDeTokens": "number (opcional)",
  "valorNominal": "number (opcional)"
}
```

#### Response (201 Created)
```json
{
  "id": 1,
  "title": "App de gestion inteligente",
  "description": "Desarrollo de una aplicacion para...",
  "objective": "Crear una solucion innovadora...",
  "requiredAmount": 10000.00,
  "currentAmount": 0.00,
  "status": "PREPARACION",
  "creatorId": 1,
  "cantidadDeTokens": 1000,
  "valorNominal": 10.00,
  "smartContractAddress": null,
  "financingStartDate": null,
  "financingEndDate": null,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00",
  "financingProgress": 0.00,
  "smartContractInfo": null
}
```

---

### 2. Actualizar proyecto

Actualiza los datos de un proyecto en estado "PREPARACION".

- **Metodo:** `PUT`
- **Endpoint:** `/api/projects/{id}`
- **Requiere autenticacion:** Si
- **Permisos necesarios:** `project:update`

#### Path Parameters
| Parametro | Tipo | Descripcion |
|-----------|------|-------------|
| id | Long | ID del proyecto |

#### Request Body
```json
{
  "title": "string (opcional)",
  "description": "string (opcional)",
  "objective": "string (opcional)",
  "requiredAmount": "number (opcional, mayor a 0)",
  "cantidadDeTokens": "number (opcional)",
  "valorNominal": "number (opcional)"
}
```

#### Response (200 OK)
```json
{
  "id": 1,
  "title": "App de gestion inteligente v2",
  "description": "Descripcion actualizada...",
  "objective": "Objetivo actualizado...",
  "requiredAmount": 15000.00,
  "currentAmount": 0.00,
  "status": "PREPARACION",
  "creatorId": 1,
  "cantidadDeTokens": 1500,
  "valorNominal": 10.00,
  "smartContractAddress": null,
  "financingStartDate": null,
  "financingEndDate": null,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T11:00:00",
  "financingProgress": 0.00,
  "smartContractInfo": null
}
```

---

### 3. Obtener proyecto por ID

Obtiene los datos completos de un proyecto.

- **Metodo:** `GET`
- **Endpoint:** `/api/projects/{id}`
- **Requiere autenticacion:** No (publico)

#### Path Parameters
| Parametro | Tipo | Descripcion |
|-----------|------|-------------|
| id | Long | ID del proyecto |

#### Response (200 OK)
```json
{
  "id": 1,
  "title": "App de gestion inteligente",
  "description": "Desarrollo de una aplicacion...",
  "objective": "Crear una solucion...",
  "requiredAmount": 10000.00,
  "currentAmount": 5000.00,
  "status": "FINANCIAMIENTO",
  "creatorId": 1,
  "cantidadDeTokens": 1000,
  "valorNominal": 10.00,
  "smartContractAddress": "0x1234...abcd",
  "financingStartDate": "2024-01-16T10:00:00",
  "financingEndDate": "2024-02-15T10:00:00",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-20T14:00:00",
  "financingProgress": 50.00,
  "smartContractInfo": {
    "address": "0x1234...abcd",
    "totalSubTokens": 1000,
    "distribution": "50% invertido, 50% disponible"
  }
}
```

---

### 4. Listar todos los proyectos

Lista todos los proyectos con paginacion.

- **Metodo:** `GET`
- **Endpoint:** `/api/projects?page=0&size=10`
- **Requiere autenticacion:** No (publico)

#### Query Parameters
| Parametro | Tipo | Default | Descripcion |
|-----------|------|---------|-------------|
| page | int | 0 | Numero de pagina (0-indexed) |
| size | int | 10 | Cantidad de elementos por pagina |

#### Response (200 OK)
```json
{
  "content": [
    {
      "id": 1,
      "title": "App de gestion inteligente",
      "description": "...",
      "status": "FINANCIAMIENTO",
      "requiredAmount": 10000.00,
      "currentAmount": 5000.00,
      "financingProgress": 50.00,
      "creatorId": 1,
      "createdAt": "2024-01-15T10:30:00"
    }
  ],
  "totalElements": 25,
  "totalPages": 3,
  "size": 10,
  "number": 0
}
```

---

### 5. Catalogo publico con filtros

Lista proyectos activos con filtros de busqueda.

- **Metodo:** `GET`
- **Endpoint:** `/api/projects/catalog?status=FINANCIAMIENTO&search=app&page=0&size=10`
- **Requiere autenticacion:** No (publico)

#### Query Parameters
| Parametro | Tipo | Default | Descripcion |
|-----------|------|---------|-------------|
| status | string | null | Filtrar por estado (PREPARACION, FINANCIAMIENTO, EJECUCION) |
| search | string | null | Buscar por titulo o descripcion |
| page | int | 0 | Numero de pagina |
| size | int | 10 | Elementos por pagina |

---

### 6. Mis proyectos

Lista los proyectos creados por el usuario autenticado.

- **Metodo:** `GET`
- **Endpoint:** `/api/projects/my-projects?page=0&size=10`
- **Requiere autenticacion:** Si

---

### 7. Progreso de financiamiento

Obtiene el progreso de financiamiento de un proyecto.

- **Metodo:** `GET`
- **Endpoint:** `/api/projects/{id}/financing-progress`
- **Requiere autenticacion:** No (publico)

#### Response (200 OK)
```json
{
  "projectId": 1,
  "requiredAmount": 10000.00,
  "currentAmount": 7500.00,
  "progressPercentage": 75.00,
  "status": "FINANCIAMIENTO",
  "isComplete": false
}
```

---

### 8. Informacion de Smart Contract

Obtiene la informacion del smart contract de un proyecto.

- **Metodo:** `GET`
- **Endpoint:** `/api/projects/{id}/smart-contract`
- **Requiere autenticacion:** No (publico)

#### Response (200 OK)
```json
{
  "address": "0x1234...abcd",
  "totalSubTokens": 1000,
  "distribution": "50% invertido, 50% disponible",
  "note": "Servicio de lectura de smart contract en desarrollo"
}
```

---

### 9. Invertir en proyecto

Realiza una inversion en un proyecto en estado "FINANCIAMIENTO".

- **Metodo:** `POST`
- **Endpoint:** `/api/projects/{id}/invest?amount=500`
- **Requiere autenticacion:** Si
- **Permisos necesarios:** `project:invest`

#### Query Parameters
| Parametro | Tipo | Descripcion |
|-----------|------|-------------|
| amount | number | Monto a invertir (mayor a 0) |

---

### 10. Actualizar estado del proyecto

Cambia el estado de un proyecto (transiciones validas).

- **Metodo:** `PATCH`
- **Endpoint:** `/api/projects/{id}/status?status=FINANCIAMIENTO`
- **Requiere autenticacion:** Si
- **Permisos necesarios:** `project:update`

#### Transiciones validas:
- PREPARACION -> FINANCIAMIENTO
- FINANCIAMIENTO -> EJECUCION (cuando se alcanza el 100%)
- FINANCIAMIENTO -> FINALIZADO (cuando vence el plazo)
- EJECUCION -> FINALIZADO

---

### 11. Evaluar y actualizar estados automaticamente

Evalua todos los proyectos en financiamiento y actualiza sus estados segun monto alcanzado o fecha de vencimiento.

- **Metodo:** `POST`
- **Endpoint:** `/api/projects/evaluate-states`
- **Requiere autenticacion:** Si

---

## Modelo de Datos

### Project
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Long | Identificador unico |
| title | String | Titulo del proyecto |
| description | String | Descripcion detallada |
| objective | String | Objetivo del proyecto |
| requiredAmount | BigDecimal | Monto total requerido |
| currentAmount | BigDecimal | Monto recaudado actualmente |
| status | Enum | Estado actual del proyecto |
| creatorId | Long | ID del creador (FK a users) |
| cantidadDeTokens | Long | Cantidad total de tokens |
| valorNominal | BigDecimal | Valor de cada token |
| smartContractAddress | String | Direccion del contrato inteligente |
| financingStartDate | LocalDateTime | Fecha inicio financiamiento |
| financingEndDate | LocalDateTime | Fecha fin financiamiento |
| createdAt | LocalDateTime | Fecha de creacion |
| updatedAt | LocalDateTime | Ultima actualizacion |

---

## Estados del Proyecto

| Estado | Descripcion | Acciones permitidas |
|--------|-------------|---------------------|
| PREPARACION | Proyecto en creacion | Editar, publicar |
| FINANCIAMIENTO | Recibiendo inversiones | Invertir, ver progreso |
| EJECUCION | En ejecucion | Ver estado, progreso |
| FINALIZADO | Cerrado | Solo lectura |

---

## Codigos de Error

| Codigo | Descripcion |
|--------|-------------|
| 200 | OK - Solicitud exitosa |
| 201 | Created - Recurso creado exitosamente |
| 204 | No Content - Solicitud exitosa sin contenido |
| 400 | Bad Request - Error de validacion |
| 401 | Unauthorized - No autenticado o token invalido |
| 403 | Forbidden - No tiene permisos suficientes |
| 404 | Not Found - Proyecto no encontrado |
| 409 | Conflict - Conflicto de datos o estado invalido |
| 500 | Internal Server Error - Error del servidor |

---

## Ejemplos de Uso

### Flujo tipico de un proyecto

1. **Crear proyecto** (CREATOR)
```javascript
const token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...';

fetch('http://localhost:8081/api/projects', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: 'Mi Proyecto Innovador',
    description: 'Un proyecto para revolucionar...',
    objective: 'Lograr X para Y...',
    requiredAmount: 10000,
    cantidadDeTokens: 1000,
    valorNominal: 10
  })
})
.then(res => res.json())
.then(data => console.log(data));
```

2. **Editar proyecto** (solo en PREPARACION)
```javascript
fetch('http://localhost:8081/api/projects/1', {
  method: 'PUT',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: 'Mi Proyecto Innovador v2',
    requiredAmount: 15000
  })
})
```

3. **Publicar / pasar a financiamiento**
```javascript
fetch('http://localhost:8081/api/projects/1/status?status=FINANCIAMIENTO', {
  method: 'PATCH',
  headers: { 'Authorization': `Bearer ${token}` }
})
```

4. **Ver catalogo publico**
```javascript
fetch('http://localhost:8081/api/projects/catalog?status=FINANCIAMIENTO&page=0&size=10')
  .then(res => res.json())
  .then(data => console.log(data));
```

5. **Invertir en un proyecto** (INVESTOR)
```javascript
fetch('http://localhost:8081/api/projects/1/invest?amount=500', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}` }
})
```

6. **Ver progreso de financiamiento**
```javascript
fetch('http://localhost:8081/api/projects/1/financing-progress')
  .then(res => res.json())
  .then(data => console.log(`Progreso: ${data.progressPercentage}%`));
```

---

## Notas Importantes

1. **El token JWT es compartido** con el modulo de usuarios. Obtenelo en `http://localhost:8080/auth/login`.

2. **Validacion matematica de tokens:** Si se proporcionan `cantidadDeTokens` y `valorNominal`, su producto debe coincidir exactamente con `requiredAmount`.

3. **Solo proyectos en PREPARACION** pueden ser editados.

4. **La paginacion es 0-indexed** - la primera pagina es `page=0`.

5. **Los estados siguen un flujo estricto:** PREPARACION -> FINANCIAMIENTO -> EJECUCION -> FINALIZADO.

6. **Los endpoints GET de proyectos son publicos** para permitir la exploracion del catalogo sin autenticacion.
