# GUION DE EXPOSICIÓN — IDEAFY
### Cómo presentarlo, qué decir, cómo responder todo

> **Cómo usar este documento:** Leélo antes de la expo. Cada sección dice qué mostrar en pantalla y qué decir. Cuando el profe pregunte algo, buscá la pregunta en negrita y leé la respuesta.

---

# PARTE 1 — INTRODUCCIÓN AL SISTEMA

## Qué es IDEAFY y por qué existe

*Arrancamos mostrando el catálogo público en `/explorar`.*

IDEAFY es una plataforma de crowdfunding tokenizado. Eso significa que cuando alguien invierte en un proyecto, no solo pone plata: recibe tokens que representan su participación real en ese proyecto. Es como comprar acciones de una empresa, pero mucho más accesible y transparente.

El problema que resuelve es real. Si hoy querés financiar un proyecto, tus opciones son un banco (que te pide garantías), inversores privados (muy difícil de acceder) o plataformas de crowdfunding como Kickstarter (que no te dan ninguna participación real y si algo sale mal, perdiste todo sin derecho a nada).

IDEAFY hace tres cosas que las otras plataformas no hacen:
- Primero, cuando invertís, recibís tokens que son tuya participación verificable y no se los puede quedar nadie.
- Segundo, si el proyecto no llega al mínimo requerido, la plataforma te devuelve el dinero automáticamente. No necesitás confiar en que alguien lo haga — lo hace el sistema solo.
- Tercero, podés vender tu participación en el marketplace antes de que el proyecto termine. No quedás atrapado.

**¿Por qué usamos blockchain para esto?**

La blockchain nos da tres cosas que ninguna base de datos centralizada puede garantizar: transparencia (cualquier persona puede verificar quién tiene qué y qué transacciones ocurrieron), automatización sin intermediarios (el reembolso no depende de que alguien apriete un botón, lo ejecuta el contrato inteligente), e inmutabilidad (una vez que una inversión se registra, nadie puede modificarla ni borrarla).

---

# PARTE 2 — LA ARQUITECTURA DEL SISTEMA

## Los tres pilares

*Mostrar un diagrama en la pizarra o slide mientras explican esto.*

El sistema tiene tres grandes capas que trabajan juntas:

**Pilar 1 — El Frontend** es lo que el usuario ve. Está construido en React. Tiene páginas para el catálogo público, para gestionar proyectos, para invertir, para ver tu billetera, para el marketplace y para la gobernanza. Vive en el puerto 3000.

**Pilar 2 — El Backend** es donde vive la lógica de negocio. Hay dos servicios. El User Service en el puerto 8080 maneja todo lo relacionado con usuarios: registro, login, roles y permisos. El Project Service en el puerto 8081 maneja proyectos, inversiones, dividendos, marketplace y gobernanza. Entre los dos hay un API Gateway en el puerto 8000 que actúa como portero.

**Pilar 3 — La Blockchain** son los contratos inteligentes. Tenemos ocho contratos desplegados en Base Sepolia, que es una red de prueba de Ethereum. Ahí viven las reglas del juego que nadie puede cambiar.

## Cómo se hablan estas capas

Cuando el usuario hace algo en el frontend, el frontend manda un pedido HTTP al Gateway. El Gateway para todo y pregunta: ¿quién es este usuario? Llama al User Service con el token JWT que el usuario tiene, el User Service responde con el ID y los roles del usuario, y el Gateway inyecta esa información en el pedido antes de mandarlo al Project Service. El Project Service nunca le cree al frontend directamente — siempre confía en el Gateway.

Cuando el Project Service necesita hacer algo en la blockchain, usa una librería llamada Web3j que habla directamente con la red Sepolia. Para leer datos usa `eth_call`, que es gratis. Para escribir datos firma una transacción con la clave privada del backend y la manda con `eth_sendRawTransaction`.

**¿Por qué el frontend no habla directamente con la blockchain?**

Porque queremos una experiencia de usuario normal. Si el usuario tuviera que aprobar cada operación en MetaMask, la experiencia sería horrible. El backend actúa como intermediario, firma las transacciones, paga el gas de la red y le devuelve al frontend el resultado en un JSON limpio.

## El API Gateway: el portero del sistema

**¿Por qué hay un gateway y no el frontend habla directo con cada servicio?**

Sin gateway, el frontend tendría que saber la URL del User Service y del Project Service por separado. Tendría que validar el JWT dos veces en dos servicios distintos. Si cambiamos el puerto de algún servicio, habría que cambiar el frontend.

Con el Gateway, el frontend habla con una sola URL. La autenticación se valida en un solo lugar. Los servicios internos ni siquiera son accesibles desde afuera. Y el Gateway inyecta el header `X-User-Id` con el ID real del usuario, que el frontend no puede falsificar porque el Gateway lo sobreescribe siempre.

---

# PARTE 3 — QUÉ VIVE EN LA BLOCKCHAIN Y QUÉ NO

**El profe casi seguro pregunta esto. Es la separación más importante del sistema.**

## Lo que SÍ vive en la blockchain

Lo que vive en la blockchain es lo que necesita ser confiable sin depender de nadie.

Los **balances de tokens** viven en la blockchain. Nadie puede decir que tenés 0 tokens si el contrato dice que tenés 1000. El saldo de $IDEA de cada usuario está en el contrato `IdeaToken`. El saldo de sub-tokens de cada proyecto está en los contratos `SubToken`.

Las **transacciones de inversión** quedan grabadas en blockchain como eventos inmutables. Cuando alguien invierte, el contrato emite un evento que queda para siempre en la historia de la red.

Los **reembolsos** también se ejecutan en blockchain. Cuando un proyecto falla, el contrato es quien mueve los tokens de vuelta a las wallets de los inversores.

Los **dividendos** se gestionan en blockchain para garantizar que el cálculo sea correcto y que nadie pueda alterar cuánto le corresponde a cada holder.

Las **propuestas de gobernanza y los votos** viven en blockchain para que sean inmutables. No se puede votar dos veces ni borrar una propuesta.

## Lo que NO vive en la blockchain

Lo que no vive en blockchain es lo que cambia frecuentemente o no necesita consenso distribuido.

El **título y descripción** de un proyecto viven en la tabla `projects` de PostgreSQL. Guardar texto largo en blockchain es extremadamente caro.

El **estado del proyecto** (si está en PREPARACION, FINANCIAMIENTO, etc.) vive en la base de datos. Cambia muchas veces durante la vida de un proyecto y cada cambio en blockchain costaría entre 50 centavos y varios dólares en la red real.

Las **órdenes pendientes del marketplace** viven en la tabla `order_book`. Se crean, se cancelan, se modifican. Si todo eso fuera on-chain, el gas sería inmanejable.

El **historial de precios dinámicos** se calcula en el backend, no en la blockchain.

Los **datos del usuario** como su email o contraseña jamás van a blockchain, que es una red pública. Viven en el User Service.

---

# PARTE 4 — LA MONEDA DEL SISTEMA: $IDEA

## Qué es el token $IDEA

*Mostrar la sidebar del frontend donde se ve el saldo de $IDEA conectado a MetaMask.*

$IDEA es la moneda interna de IDEAFY. Es un token ERC-20, que es el estándar más usado en Ethereum. Para usar la plataforma necesitás $IDEA, así como para entrar a un parque de diversiones necesitás fichas.

El contrato se llama `IdeaToken.sol` y vive en la blockchain de Base Sepolia.

**¿Por qué crear una moneda propia y no usar USDC o ETH directamente?**

Porque nos permite capturar valor. Cada transacción con $IDEA tiene reglas que nosotros definimos. La principal es que en cada transferencia se quema el 1% automáticamente.

## La quema automática del 1%

Esto está definido en el contrato `IdeaToken.sol` con la constante `BURN_BPS = 100`. Cien puntos básicos es igual al 1%.

Cuando alguien transfiere 1000 $IDEA, el destinatario recibe 990 $IDEA y 10 $IDEA se destruyen permanentemente. No van a ninguna wallet — se eliminan del supply total.

**¿Para qué sirve quemar tokens?**

Más actividad en la plataforma significa más transferencias de $IDEA, que significa más quemas, que significa que hay menos $IDEA en circulación, que hace que cada $IDEA valga más. Esto beneficia a los holders de largo plazo. La plataforma, que tiene tokens en su tesorería, también se beneficia de esto.

**¿Ese porcentaje del 1% está fijo? ¿Se puede cambiar?**

No se puede cambiar. La constante `BURN_BPS` en el contrato usa la palabra clave `constant` de Solidity, lo que significa que está grabada en el bytecode del contrato para siempre. Ni siquiera el dueño del contrato puede cambiarla.

## El supply total: 100 millones de $IDEA

El contrato tiene un límite máximo de cien millones de $IDEA que no se puede superar nunca. No existe ninguna función en el contrato para mintear más. Cuando se deployó el contrato, se crearon los cien millones y se acreditaron a la wallet del backend.

---

# PARTE 5 — EL MONTO SUPERCARGADO: LA TESORERÍA

**Esta es la pregunta que el profe le hizo a otro grupo. Tenemos que tenerla muy clara.**

## Por qué hay una wallet con 100 millones de $IDEA

Cuando se deployó el contrato `IdeaToken`, la primera línea del constructor hace esto: mintea todos los tokens y los manda a la wallet que deployó el contrato. En Solidity esa wallet se llama `msg.sender`, que en nuestro caso es la wallet del backend de la plataforma.

Esa wallet con cien millones de $IDEA es la **tesorería de la plataforma**.

**¿Por qué somos nosotros quienes la tienen?**

Porque somos la plataforma. Es como cuando un banco central emite moneda: primero la crea y después la distribuye al sistema. Nosotros creamos los cien millones de $IDEA y los distribuimos a los usuarios de estas tres formas:

Primero, cuando un usuario nuevo quiere empezar a invertir, necesita adquirir $IDEA. La plataforma se los vende desde la tesorería a cambio de USDC (que son dólares digitales) usando el pool de intercambio IdeaSwap.

Segundo, para que ese pool de intercambio funcione, la tesorería deposita $IDEA ahí. Esa liquidez inicial es lo que permite que los usuarios compren $IDEA.

Tercero, para el ambiente de pruebas en Sepolia, la plataforma transfiere $IDEA directamente a los usuarios de prueba para que puedan experimentar sin gastar dinero real. Por eso ven montos grandes en las wallets de testing — es plata de prueba asignada a mano.

---

# PARTE 6 — LOS 8 CONTRATOS INTELIGENTES

## Qué es un contrato inteligente

Un contrato inteligente es un programa que vive en la blockchain. Una vez que se publica en la red, su código es inmutable y se ejecuta automáticamente cuando alguien lo llama. No hay un servidor que "corra" el contrato — la red entera lo ejecuta y todos los participantes verifican que el resultado sea correcto.

Todos nuestros contratos están en la carpeta `blockchain/contracts/`.

## Contrato 1 — IdeaToken: la moneda de la plataforma

Es el token $IDEA. Gestiona el supply total, aplica la quema del 1% en cada transferencia, y usa el sistema de roles de OpenZeppelin para controlar quién puede hacer qué.

## Contrato 2 — SubToken: el token por proyecto

Cada proyecto que entra en financiamiento tiene su propio token. El SubToken es el modelo de ese token. Está construido con el patrón UUPS que permite actualizarlo si encontramos un bug, sin perder los balances de los inversores.

**¿Por qué upgradeable?** Si hay cincuenta proyectos con sub-tokens y encontramos un error en el código, sin upgradeabilidad tendríamos que migrar cincuenta contratos y perder todos los balances. Con UUPS, cambiamos la lógica en un solo contrato y todos los proxies la usan automáticamente.

## Contrato 3 — IdeafyFactory: la fábrica de tokens

Cuando un proyecto entra en financiamiento, el backend le pide a este contrato que cree un nuevo sub-token para ese proyecto. La factory lleva un registro de qué token corresponde a qué proyecto. Solo el backend (que tiene el rol `CREATOR_ROLE`) puede crear nuevos tokens.

## Contrato 4 — OfferingContract: la venta primaria

Este es el corazón del crowdfunding. Cuando alguien invierte, los $IDEA no van directamente al creador del proyecto — van a este contrato y quedan ahí custodiados. Solo cuando el proyecto alcanza el monto mínimo requerido (soft cap), el dinero se libera al creador. Si no llega, el contrato devuelve todo.

Tiene dos límites: el soft cap que es el mínimo para que el proyecto sea exitoso, y el hard cap que es el máximo que puede recaudar.

## Contrato 5 — DividendDistributor: el reparto de ganancias

Cuando un proyecto genera ganancias y el admin quiere distribuirlas a los inversores, deposita $IDEA en este contrato. El contrato usa un patrón acumulador para calcular cuánto le corresponde a cada holder sin tener que hacer una transferencia por cada usuario — lo que sería carísimo en gas.

## Contrato 6 — IdeaSwap: el exchange de $IDEA por USDC

Es como un mini Uniswap. Permite convertir USDC a $IDEA y viceversa. El precio no lo fija nadie — lo determina la relación entre las reservas del pool. Si hay más $IDEA que USDC en el pool, cada $IDEA vale menos; si hay más USDC, cada $IDEA vale más.

## Contrato 7 — IdeaMarketplace: el mercado secundario

Permite que los inversores vendan sus sub-tokens a otros usuarios. Cobra una comisión del 2.5% en cada operación. Esa plata queda en el contrato y el admin la retira cuando quiere.

## Contrato 8 — IdeaGovernance: las votaciones

Los holders de $IDEA pueden votar sobre decisiones de la plataforma. Para proponer algo necesitás tener al menos mil $IDEA. Para que una propuesta se apruebe, tiene que votar el 10% del supply total y ganar la mayoría. El período de votación dura siete días.

---

# PARTE 7 — LOS USUARIOS Y POR QUÉ CADA UNO PUEDE HACER LO QUE HACE

**El profe siempre pregunta: "¿Por qué ESTE usuario puede hacer esto?" Acá está la respuesta estructurada para cada caso.**

## Cómo funciona el sistema de permisos

Cuando el usuario se loguea, el backend le devuelve un JWT (un token firmado) que contiene sus roles y permisos. Ese JWT viaja en el header `Authorization` de cada pedido. El Gateway lo valida y extrae el rol. El controller en el backend verifica que el rol tenga el permiso necesario. Si no lo tiene, el sistema devuelve un error 403 Forbidden.

Los roles están definidos en el User Service. Los permisos están asignados en las migraciones `V2__add_project_permissions.sql` y `V9__fix_admin_project_permissions.sql`.

## El Usuario Inversor

Tiene los permisos `project:read` e `project:invest`.

Puede ver el catálogo, explorar proyectos, invertir, ver su historial de inversiones, usar el marketplace para comprar o vender sub-tokens, y reclamar dividendos.

No puede crear proyectos porque no tiene el permiso `project:update`. No puede distribuir dividendos porque no tiene el rol ADMIN.

**¿Por qué el inversor puede comprar en el marketplace pero no crear proyectos?** Porque comprar en el marketplace es ejercer un derecho como holder de tokens. Crear un proyecto es un acto que desencadena cosas caras: un contrato se publica en blockchain. Eso requiere un rol específico.

## El Usuario Creador

Tiene los permisos del inversor más `project:update`.

Puede todo lo anterior y además crear proyectos y editarlos mientras están en estado PREPARACION.

**¿Por qué solo puede editar en PREPARACION?** Porque cuando un proyecto entra en FINANCIAMIENTO ya hay inversores que tomaron decisiones con esa información. Cambiar el monto requerido o el plazo después de que alguien invirtió sería una forma de fraude. El contrato que valida las inversiones fue registrado con esos parámetros — cambiarlos en la base de datos sin cambiarlos en blockchain generaría inconsistencias.

## El Usuario Admin

Puede todo lo anterior y además distribuir dividendos, marcar proyectos como destacados, ver las estadísticas globales del dashboard, retirar los fees acumulados en los contratos, y gestionar usuarios y roles.

**¿Por qué solo el admin distribuye dividendos?** Porque antes de distribuir alguien tiene que verificar que el proyecto efectivamente generó esas ganancias. El admin actúa como auditor. Si el creador pudiera distribuir libremente, podría declarar ganancias inexistentes o directamente no distribuir nada.

## La wallet del backend (la cuenta tesorería)

Es una cuenta de Ethereum cuya clave privada está guardada en el archivo `.env` del servidor. Es quien firmó los contratos cuando se deployaron, entonces tiene el rol de administrador en todos ellos. Es quien tiene los cien millones de $IDEA iniciales. Es quien paga el gas de las transacciones cuando los usuarios invierten.

---

# PARTE 8 — CÓMO GANA PLATA LA PLATAFORMA

**Esto es economía pura. El profe pregunta esto siempre.**

## Fuente 1: Comisión del marketplace (2.5%)

Cada vez que alguien compra sub-tokens en el marketplace, el contrato `IdeaMarketplace` cobra el 2.5% del total de la operación.

Ejemplo concreto: Alice vende 100 sub-tokens a 2 $IDEA cada uno. El total es 200 $IDEA. El contrato cobra 5 $IDEA de comisión. Alice recibe 195. Los 5 $IDEA quedan en el contrato.

El porcentaje está definido en el contrato como `feeBps = 250`. Doscientos cincuenta puntos básicos es igual al 2.5%. Se puede cambiar con la función `updateFee()` pero solo el admin puede hacerlo, y hay un límite máximo del 10%.

**¿Cómo retira el admin esa plata?** Llama a la función `collectFees()` del contrato, que transfiere todos los $IDEA acumulados a la wallet del admin.

**¿Cómo sabe el admin cuánto acumuló?** Puede leer el balance del contrato marketplace directamente en blockchain, o ver los eventos `TradeExecuted` que registran cada operación con el monto.

## Fuente 2: Comisión del swap (1%)

Cada vez que alguien convierte USDC a $IDEA o viceversa usando el IdeaSwap, el contrato cobra el 1% de la operación.

El porcentaje está definido como `feeBps = 100` en el contrato `IdeaSwap.sol`. También lo puede cambiar el admin con `updateFee()`, con tope del 10%.

## Fuente 3: Boost de proyectos

Los creadores pueden pagar para que su proyecto aparezca destacado en el catálogo. La tabla `projects` tiene las columnas `es_destacado` y `fecha_boost`. En el frontend, los proyectos destacados aparecen primero con un badge de estrella dorada.

## Fuente 4: Apreciación de la tesorería

La quema del 1% en cada transferencia reduce el supply total con el tiempo. La tesorería tiene 50 millones de $IDEA. Si el supply total baja de 100 millones a 80 millones, esos 50 millones representan ahora el 62.5% del supply en vez del 50%. El valor relativo de la tesorería crece sin hacer nada.

## Tabla resumen

| Fuente | Cuánto | Dónde se define | Dónde se acumula |
|--------|--------|----------------|-----------------|
| Fee marketplace | 2.5% de cada venta | `IdeaMarketplace.sol` `feeBps = 250` | En el contrato marketplace |
| Fee swap | 1% de cada cambio | `IdeaSwap.sol` `feeBps = 100` | En el contrato swap |
| Boost proyectos | Tarifa fija en $IDEA | `BoostService.java` | Wallet del admin |
| Apreciación tesorería | Deflación continua | `IdeaToken.sol` `BURN_BPS = 100` | Valor de la wallet backend |

---

# PARTE 9 — PANTALLA POR PANTALLA, BOTÓN POR BOTÓN

## Pantalla 1 — Login (`/`)

*Mostrar la página de login.*

El usuario ve dos campos: email y contraseña. También tiene la opción de entrar con Google.

Cuando aprieta **"Entrar"**, el frontend manda `POST /auth/login` al User Service con el email y la contraseña. El User Service verifica la contraseña contra el hash almacenado en la base de datos (nunca guarda la contraseña en texto plano). Si es correcta, devuelve un JWT con el ID del usuario, sus roles y sus permisos. El frontend guarda ese JWT en localStorage y redirige al dashboard.

**¿Por qué hay opción de Google?** Porque el User Service soporta OAuth2. Cuando el usuario clickea en Google, el frontend redirige a `http://localhost:8080/oauth2/authorization/google`. Google autentica al usuario y redirige de vuelta con un código. El User Service intercambia ese código por la información del usuario y emite el JWT.

**¿Qué pasa si la contraseña es incorrecta?** El User Service devuelve 401. El frontend muestra el mensaje de error. El JWT no se genera y el usuario se queda en la pantalla de login.

**¿Por qué no se guarda la contraseña en texto plano?** Seguridad básica. Si la base de datos se filtra, las contraseñas no se exponen. Se guarda un hash criptográfico con bcrypt.

---

## Pantalla 2 — Registro (`/registro`)

El usuario ingresa nombre, email y contraseña (mínimo 8 caracteres).

Cuando aprieta **"Crear cuenta"**, el frontend manda `POST /auth/register` al User Service. El User Service verifica que el email no exista, hashea la contraseña y crea el usuario. Le asigna el rol por defecto (generalmente INVESTOR). Devuelve una confirmación, no un JWT — el usuario tiene que ir a hacer login.

**¿Por qué no loguea automáticamente después del registro?** Es una decisión de diseño de seguridad. El registro confirma la identidad pero no la autenticidad de la sesión actual.

---

## Pantalla 3 — Dashboard (`/dashboard`)

*Mostrar el dashboard.*

Esta página carga datos de dos fuentes:
- Las estadísticas globales vienen de `GET /api/dashboard/stats` en el Project Service
- Los proyectos recientes vienen de `GET /api/projects` en el Project Service

El dashboard muestra:
- Para todos: cuántos proyectos activos hay, cuánto capital está en financiamiento, cuántos inversores hay, cuántos usuarios hay en total.
- Para el admin también: un gráfico de torta con proyectos por estado, un gráfico de barras con los top 5 proyectos por monto recaudado, y el estado de los módulos del sistema.
- Para el creador: un botón prominente de "Nuevo proyecto" que lleva a `/proyectos/crear`.

**¿Por qué el admin ve más información que el creador?** Porque el controller verifica `isAdmin` antes de cargar las estadísticas globales. Un creador que intente llamar a `GET /api/dashboard/stats` directamente recibiría 403 Forbidden.

El botón **"Evaluar estados"** solo lo ve el admin. Cuando lo aprieta, llama a `POST /api/projects/evaluate-states`, que dispara manualmente el proceso de cierre de proyectos vencidos. Normalmente esto lo hace el scheduler automáticamente, pero el admin puede forzarlo.

En la sidebar, si el usuario tiene MetaMask conectado, ve su balance de $IDEA y de ETH en tiempo real. El frontend lee eso directamente del contrato `IdeaToken` usando la librería wagmi de Web3, sin pasar por el backend. Es una lectura directa de blockchain desde el navegador.

---

## Pantalla 4 — Catálogo de Proyectos (`/proyectos` y `/explorar`)

*Mostrar el catálogo.*

Hay dos versiones del catálogo: `/explorar` es pública (no necesita login) y `/proyectos` es la versión autenticada dentro del dashboard.

El catálogo carga con `GET /api/projects/catalog`. Tiene filtros por estado y ordenamiento por monto. Los proyectos destacados (con `es_destacado = true` en la base de datos) aparecen primero con un badge dorado.

**¿Qué es la barra de progreso en la card?** Muestra `monto_recaudado / monto_requerido`. Esos dos valores vienen de la tabla `projects` en la base de datos. Se actualiza cada vez que alguien invierte.

**¿Por qué el catálogo es visible sin login?** Para que potenciales inversores puedan explorar sin necesidad de crearse una cuenta primero. La inversión sí requiere login.

Cada card tiene el estado del proyecto, el monto requerido, cuántos días quedan, y una barra de progreso. Al hacer click en la card va al detalle del proyecto.

---

## Pantalla 5 — Crear/Editar Proyecto (`/proyectos/crear`)

*Mostrar el formulario de creación.*

**¿Quién puede ver esta página?** Solo usuarios con el permiso `project:update`, que en nuestro sistema es el rol CREADOR y el ADMIN. Si un inversor intenta navegar a esa URL manualmente, el router lo redirige porque no tiene permiso.

El formulario tiene:
- **Título**: el nombre del proyecto, máximo 200 caracteres.
- **Descripción**: qué es el proyecto, máximo 2000 caracteres.
- **Monto requerido**: cuántos $IDEA necesita para financiarse.
- **Plazo**: hasta cuándo se puede invertir.
- **Gobernanza comunidad**: si se activa esto, los inversores pueden votar sobre decisiones del proyecto.
- **Cupo máximo de tokens**: cuántos sub-tokens va a emitir.
- **Valor nominal del token**: precio base de cada sub-token en $IDEA.
- **Símbolo**: el ticker del sub-token, máximo 5 caracteres.

Cuando el creador aprieta **"Crear"**, el frontend manda `POST /api/projects` al Gateway. El Gateway valida el JWT, extrae el ID del creador, y lo manda al Project Service. El ProjectService inserta el proyecto en la tabla `projects` con estado `PREPARACION`. Le devuelve el proyecto creado y el frontend redirige al detalle del proyecto.

El estado inicial es siempre `PREPARACION`. El proyecto no es visible para los inversores todavía.

---

## Pantalla 6 — Detalle del Proyecto (`/proyectos/:id`)

*Mostrar el detalle de un proyecto en financiamiento. Esta es la pantalla más importante.*

Esta pantalla tiene varios bloques y varios botones que hacen cosas distintas según quién es el usuario.

### Lo que todos ven

La pantalla muestra el título, descripción, estado actual, barra de progreso con el porcentaje financiado, cuántos días quedan para el plazo, el precio actual del sub-token, y las métricas del proyecto.

El precio del sub-token NO es fijo. Se calcula en el backend usando una fórmula que tiene en cuenta la demanda (cuántos tokens se vendieron) y el estado del proyecto (más avanzado = más caro). Veremos esa fórmula en el flujo de inversión.

### Botones del creador

El creador ve el botón **"Publicar"** mientras el proyecto está en PREPARACION. Cuando lo aprieta, el sistema cambia el estado a FINANCIAMIENTO y lanza el sub-token en blockchain. Explicamos este flujo completo en la sección de flujos.

Si el proyecto está en financiamiento, el creador ve el botón **"Cerrar proyecto"**. Este es para cancelarlo manualmente si quiere.

### Botones del admin

El admin ve el botón **"Boost"** y **"Quitar boost"** para destacar o quitar del destacado el proyecto en el catálogo.

También ve el botón **"Evaluar estados"** para forzar el proceso de cierre de proyectos vencidos.

### El botón de inversión

El botón **"Invertir"** aparece para usuarios autenticados con el permiso de inversión, cuando el proyecto está en estado FINANCIAMIENTO. El flujo completo de este botón lo explicamos en la sección de flujo de inversión.

### El banner de proyecto fallido

Si el proyecto está en estado RECHAZADO, la pantalla muestra el componente `ProjectFailedBanner` que explica que el proyecto no llegó al monto y que las inversiones fueron reembolsadas.

### El link al txHash

El componente `TxHashLink` muestra los hashes de transacciones. Cada uno es un link clickeable que lleva directamente a Etherscan donde se puede verificar la transacción en blockchain. Esta es la transparencia en acción: cualquiera puede verificar que la transacción ocurrió realmente.

---

## Pantalla 7 — Inversiones (`/inversiones`)

*Mostrar el historial de inversiones.*

Esta pantalla carga con `GET /api/investments/history`. Muestra todas las inversiones del usuario autenticado con su estado actual.

Los estados posibles de una inversión son:
- **PENDIENTE**: la inversión se registró pero la transacción blockchain no confirmó todavía.
- **CONFIRMADA**: la inversión está activa y los sub-tokens fueron acreditados.
- **REEMBOLSADA**: el proyecto falló y los $IDEA fueron devueltos.
- **RECHAZADA**: la inversión fue rechazada por alguna validación.

Cuando el proyecto está en estado RECHAZADO, la inversión tiene el botón **"Solicitar reembolso"**. Cuando el usuario lo aprieta, el backend llama al contrato `OfferingContract` para ejecutar el reembolso on-chain.

Cada inversión muestra el txHash con el link a Etherscan para que el usuario pueda verificar la transacción.

---

## Pantalla 8 — Billetera (`/billetera`)

*Mostrar la billetera.*

Esta pantalla muestra el saldo de $IDEA del usuario y su portfolio de sub-tokens (qué tokens tiene de qué proyectos y cuántos).

El saldo de $IDEA se lee de la columna `saldo_idea` en la tabla `users` de la base de datos. También se puede ver leyendo directamente del contrato `IdeaToken` en blockchain.

El portfolio viene de la tabla `portfolio_activos` que relaciona usuario, sub-token y cantidad.

---

# PARTE 10 — LOS FLUJOS COMPLETOS

## Flujo 1 — Registrarse e iniciar sesión

El usuario entra a la pantalla de login. Escribe su email y contraseña y aprieta "Entrar". El frontend manda `POST /auth/login` al User Service. El User Service verifica la contraseña y genera un JWT. El frontend guarda el JWT en localStorage y redirige al dashboard. A partir de ahora, cada pedido que haga el frontend va con el JWT en el header `Authorization`. El Gateway verifica ese JWT en cada pedido y extrae el rol del usuario.

---

## Flujo 2 — Crear y publicar un proyecto

**Quién lo hace**: el usuario con rol CREADOR.

El creador navega a `/proyectos/crear`. Completa el formulario con el título, descripción, monto requerido, plazo y las características del token (cupo, valor nominal, símbolo). Aprieta "Crear".

El frontend manda `POST /api/projects`. El Gateway valida el JWT y extrae el ID del creador. El ProjectController recibe el pedido. El ProjectService inserta en la tabla `projects` con estado `PREPARACION`. El proyecto no es visible para inversores todavía.

El creador es redirigido al detalle de su proyecto. Ahí ve el estado "Preparación" y el botón "Publicar".

Cuando aprieta **"Publicar"**, el frontend manda `PATCH /api/projects/{id}/status?status=FINANCIAMIENTO`.

El backend hace dos cosas en este momento:

**Primero**, actualiza el estado en la base de datos: cambia `estado = 'FINANCIAMIENTO'` en la tabla `projects`.

**Segundo**, y esto es lo importante, llama al contrato `IdeafyFactory` en blockchain para crear el sub-token del proyecto. El backend le pasa el ID del proyecto, el símbolo que eligió el creador, el cupo máximo de tokens y el valor nominal. El contrato de la factory crea un nuevo proxy de SubToken con esos parámetros, emite el evento `ProjectLaunched` con la dirección del nuevo token, y el backend lee esa dirección del evento y la guarda en la columna `smart_contract_address` de la tabla `projects`.

A partir de ese momento, el proyecto aparece en el catálogo público y los inversores pueden invertir.

**¿Por qué el sub-token se crea cuando se publica y no cuando se crea el proyecto?**
Porque publicar un contrato en blockchain cuesta gas. Si el creador nunca llegara a publicar el proyecto, habremos gastado gas sin resultado. El costo se paga solo cuando hay certeza de que el proyecto va al mercado.

---

## Flujo 3 — Invertir en un proyecto

**Este es el flujo más importante y el que más preguntas genera.**

**Quién lo hace**: cualquier usuario autenticado con permiso `project:invest`.

El inversor navega al detalle de un proyecto en FINANCIAMIENTO. Ve el precio actual del sub-token y el cupo disponible. Escribe cuántos $IDEA quiere invertir en el input. En tiempo real, el frontend le muestra cuántos sub-tokens va a recibir.

**¿Cómo calcula cuántos tokens va a recibir?**

Mientras el usuario escribe, el frontend llama a `POST /api/investments/validate` con el monto ingresado. El backend responde con el precio actual, el cupo disponible y la cantidad de sub-tokens a recibir. Esta llamada es de solo lectura, no hace ninguna transacción.

**¿El precio del sub-token está fijo?**

No. El precio se calcula en tiempo real en `DynamicPricingService.java` usando una fórmula que combina dos factores:

El **factor de demanda** sube el precio según cuántos tokens se vendieron. Si se vendió el 50% del cupo, el precio sube un 25%.

El **factor de rendimiento** sube el precio según en qué etapa está el proyecto. En FINANCIAMIENTO el factor es entre 0.15 y 0.35 según cuánto avanzó. En EJECUCION es 0.50. En FINALIZADO es 0.75. Esto es porque a mayor avance del proyecto, menor riesgo, entonces los tokens valen más.

La fórmula es: precio final = precio base × (1 + factor demanda) × (1 + factor rendimiento).

Estos factores están definidos en `DynamicPricingService.java` en el backend, no en los contratos.

Cuando el inversor aprieta **"Invertir"**, el frontend manda `POST /api/investments` con el monto.

El backend hace estas cosas en orden:

**Validación**: verifica que el proyecto existe y está en FINANCIAMIENTO, que el plazo no venció, que hay cupo disponible (`monto_recaudado + nuevo_monto <= monto_requerido`), y que el usuario tiene saldo suficiente en su columna `saldo_idea`.

**Transacción blockchain**: el backend llama al contrato `OfferingContract` con la función `invest()`. El contrato verifica que no se supere el hard cap, transfiere los $IDEA del inversor al contrato (quedan custodiados ahí, no van al creador todavía), calcula cuántos tokens le corresponden y emite el evento `InvestmentMade` que queda grabado en blockchain para siempre.

**Actualización en base de datos**: en una transacción SQL atómica (todo o nada), el backend descuenta los $IDEA del saldo del usuario en la tabla `users`, actualiza el `monto_recaudado` del proyecto en la tabla `projects`, guarda la inversión en la tabla `investments` con estado CONFIRMADA, y actualiza el portfolio del usuario en `portfolio_activos`.

**Respuesta**: le devuelve al frontend el detalle de la inversión con el txHash que puede verificar en Etherscan.

**¿Quién paga el gas de la transacción?**

La wallet del backend. El inversor no necesita ETH ni MetaMask para invertir. El backend firma la transacción y paga el gas. El costo de ese gas se recupera a través de las comisiones de la plataforma.

**¿Qué pasa si se llega al 100% del financiamiento?**

Después de actualizar la base de datos, el backend verifica si `monto_recaudado >= monto_requerido`. Si es así, llama a `OfferingContract.finalize()` en blockchain, que verifica que se superó el soft cap y libera los fondos al creador. En la base de datos actualiza el estado del proyecto a EJECUCION.

---

## Flujo 4 — Qué pasa si el proyecto no llega al monto en el plazo

**Este flujo es completamente automático. El profe pregunta esto sí o sí.**

Hay un componente en el backend llamado `InvestmentScheduler.java` que corre automáticamente cada hora. Este componente busca en la tabla `projects` todos los proyectos que están en estado FINANCIAMIENTO y cuyo `plazo` ya pasó.

Para cada uno de esos proyectos, el scheduler llama a `processExpiredProjects()` en `InvestmentService.java`.

Ese método hace lo siguiente:

Primero, actualiza el estado del proyecto a RECHAZADO en la base de datos.

Segundo, llama a `OfferingContract.finalize()` en blockchain. El contrato verifica que `totalInvested < softCap` (que no llegó al mínimo), y marca el offering como fallido. A partir de ahí, los inversores pueden llamar a la función `refund()` del contrato.

Tercero, para cada inversión del proyecto, el backend llama a la función `refund()` del contrato `OfferingContract`. El contrato transfiere los $IDEA custodiados de vuelta a las wallets de los inversores. El contrato emite el evento `RefundMade` que queda grabado en blockchain como prueba del reembolso.

Cuarto, en la base de datos, actualiza el estado de cada inversión a REEMBOLSADA, restaura el saldo de $IDEA de cada usuario, y elimina los sub-tokens del portfolio porque nunca fueron emitidos definitivamente.

**¿Qué ve el inversor?**

Entra a `/inversiones` y ve su inversión con estado REEMBOLSADA. Su saldo de $IDEA volvió al valor anterior. En el detalle del proyecto aparece el banner de proyecto fallido. El txHash del reembolso está visible y se puede verificar en Etherscan.

---

## Flujo 5 — Reclamar sub-tokens después de un financiamiento exitoso

Cuando el proyecto alcanzó el 100% y el admin llamó a `finalize()` marcándolo como exitoso, los inversores tienen que reclamar sus sub-tokens. Esto es un paso manual porque en blockchain, mandar tokens a mil wallets cuesta gas mil veces. Si el inversor reclama él mismo, paga su propio gas.

El inversor va al detalle del proyecto y ve el botón "Reclamar tokens". Cuando lo aprieta, el backend llama a `OfferingContract.claimTokens()`. El contrato verifica que el offering fue exitoso, calcula cuántos tokens le corresponden según cuánto invirtió, y se los transfiere desde la factory.

---

## Flujo 6 — Distribuir dividendos

**Quién lo hace**: el admin, después de verificar que el proyecto generó ganancias reales.

El admin va al detalle del proyecto y llama al endpoint `POST /api/dividendos/proyecto/{id}?monto=5000`. Solo funciona para proyectos en estado EJECUCION o FINALIZADO.

El backend llama a `DividendDistributor.distribute()` en blockchain. El contrato recibe los 5000 $IDEA, consulta el supply total de sub-tokens del proyecto y actualiza un acumulador global que dice cuántos $IDEA le corresponden por cada sub-token.

El backend registra el reparto en la tabla `dividendos`.

Cada inversor puede ir a `/inversiones` o al detalle del proyecto y ver cuántos $IDEA puede reclamar. Cuando aprieta "Reclamar dividendos", el backend llama a `DividendDistributor.claim()` y el contrato calcula cuánto le corresponde según sus sub-tokens y cuándo fue su último reclamo, y le transfiere los $IDEA.

**¿Qué pasa si el inversor no reclama inmediatamente y hay más repartos después?**

El contrato usa un patrón acumulador que no pierde nada. El inversor puede reclamar todos los repartos acumulados juntos en una sola transacción. No pierde dividendos por esperar.

---

## Flujo 7 — Vender sub-tokens en el marketplace

**Quién lo hace**: cualquier usuario que tenga sub-tokens.

El usuario va a `/marketplace`. Clickea "Publicar venta". Elige qué sub-tokens vende, cuántos y a qué precio.

El frontend manda `POST /api/marketplace/listings`. El backend verifica en la tabla `portfolio_activos` que el usuario tenga esa cantidad de sub-tokens. Publica la orden en la tabla `order_book` con estado ACTIVA.

En blockchain, el backend llama a `IdeaMarketplace.listTokens()`. El contrato transfiere los sub-tokens del vendedor al contrato (quedan bloqueados ahí mientras la orden está activa). Si el vendedor cancela, los sub-tokens vuelven a su wallet.

Cuando un comprador quiere esos tokens, el backend llama a `IdeaMarketplace.buyTokens()`. El contrato ejecuta el trade: transfiere los $IDEA del comprador al contrato, cobra el 2.5% de comisión, manda el 97.5% al vendedor, y manda los sub-tokens al comprador. Todo en una sola transacción atómica en blockchain — o todo pasa o nada pasa.

---

## Flujo 8 — Votar en gobernanza

Un holder de $IDEA que tiene al menos 1000 va a `/gobernanza`. Propone algo. El frontend manda `POST /api/governance/proposals`.

El backend verifica que la wallet tiene al menos 1000 $IDEA leyendo el contrato `IdeaToken`. Llama a `IdeaGovernance.createProposal()`. El contrato crea la propuesta con un período de votación de 7 días y emite el evento `ProposalCreated`.

El backend guarda la propuesta en la tabla `proposals` con el ID on-chain.

Otros holders van a la sección de gobernanza, ven la propuesta y votan. Cada voto tiene un peso igual a los $IDEA que tiene el votante. El backend llama a `IdeaGovernance.vote()`.

Después de 7 días, si votó el 10% del supply y ganaron los votos a favor, el admin puede ejecutar la propuesta llamando a `IdeaGovernance.executeProposal()`.

---

# PARTE 11 — LA BASE DE DATOS EN DETALLE

La base de datos es PostgreSQL alojada en Supabase. Las migraciones están gestionadas con Flyway, que ejecuta cada archivo SQL numerado en orden al arrancar el backend.

## Las tablas más importantes

**`projects`**: el núcleo. Guarda todos los proyectos con sus estados, montos, plazos, smart contract address y datos de boost. Tiene un soft delete con la columna `deleted_at`.

**`subtokens`**: información de los tokens por proyecto: nombre, símbolo, cupo máximo, cupo restante, precio base, precio actual y dirección del contrato en blockchain.

**`portfolio_activos`**: relaciona usuario y sub-token con la cantidad que tiene. Se actualiza en cada inversión y en cada transferencia en el marketplace.

**`investments`**: historial completo de cada inversión: quién invirtió, cuánto, cuántos tokens recibió, el txHash de la transacción, y el estado (PENDIENTE, CONFIRMADA, REEMBOLSADA).

**`order_book`**: órdenes de compra/venta del marketplace. Tiene estado ACTIVA, COMPLETADA, CANCELADA. Guarda el on_chain_id que relaciona la orden con el contrato blockchain.

**`dividendos`** y **`reclamos_dividendos`**: historial de distribuciones y de quién reclamó qué.

**`proposals`**: propuestas de gobernanza con sus votos y estado.

**`blockchain_eventos`**: tabla de seguimiento de eventos de blockchain. El listener del backend guarda acá los eventos que procesa para no reprocesarlos.

**`blockchain_sync`**: guarda en qué bloque quedó el último procesamiento de eventos. Sirve para saber desde dónde continuar si el backend se reinicia.

---

# PARTE 12 — LOS EVENTOS DE BLOCKCHAIN

**El profe preguntó sobre esto a otro grupo. Hay que tenerlo claro.**

Un evento en Solidity es un registro que el contrato emite cuando pasa algo importante. Es como un log. Se guarda en la blockchain para siempre, cualquiera puede leerlos, y cuestan mucho menos gas que guardar datos en el storage del contrato.

Cuando el backend hace una transacción, el contrato puede emitir uno o varios eventos. Por ejemplo, cuando alguien invierte, el contrato `OfferingContract` emite el evento `InvestmentMade(proyectoId, investor, ideaAmount, tokenAmount)`. Ese evento queda grabado en la cadena de bloques de forma permanente.

El backend tiene el servicio `InvestmentEventService.java` que corre cada 30 segundos y revisa si hay eventos nuevos en la blockchain desde el último bloque que procesó. Si encuentra el evento `InvestmentMade`, lo procesa y actualiza la base de datos. Esto sirve como sistema de reconciliación: si el backend tuvo un problema y no registró una inversión en la base de datos pero sí se ejecutó on-chain, el listener lo detecta y lo corrige.

---

# PARTE 13 — PREGUNTAS DEL PROFE Y LAS RESPUESTAS EXACTAS

## "¿Por qué este usuario puede hacer esto?"

Porque tiene el rol que tiene el permiso necesario. El rol viene en el JWT. El Gateway lo valida y lo inyecta como header. El controller lo verifica. Si no tiene el permiso, devuelve 403. Los permisos están asignados en las migraciones de base de datos del User Service.

## "¿Dónde está definido ese porcentaje?"

- Quema del 1%: en el contrato `IdeaToken.sol`, constante `BURN_BPS = 100`. No se puede cambiar.
- Fee del marketplace 2.5%: en el contrato `IdeaMarketplace.sol`, variable `feeBps = 250`. El admin puede cambiarlo hasta un máximo de 10%.
- Fee del swap 1%: en el contrato `IdeaSwap.sol`, variable `feeBps = 100`. El admin puede cambiarlo hasta un máximo de 10%.
- Factores del pricing dinámico: en el backend, en `DynamicPricingService.java`. No están en blockchain.
- Quórum de gobernanza 10%: en el contrato `IdeaGovernance.sol`, constante `QUORUM_BPS = 1000`. No se puede cambiar.

## "¿Cómo ganan plata?"

Comisión del 2.5% en el marketplace, cobrada en el contrato `IdeaMarketplace` y retirada con `collectFees()`. Comisión del 1% en el swap. Boost de proyectos. Apreciación de la tesorería por deflación del $IDEA.

## "¿Qué pasa si el proyecto no llega al monto?"

El scheduler `InvestmentScheduler.java` detecta el proyecto vencido, lo marca como RECHAZADO en la base de datos, llama a `finalize()` en el contrato `OfferingContract` para marcarlo como fallido, y ejecuta `refund()` para cada inversor devolviendo sus $IDEA. Todo queda registrado en blockchain.

## "¿Esos porcentajes que se muestran en pantalla están fijos?"

Los del pricing dinámico se calculan en tiempo real en el backend. Los de las comisiones están en los contratos. Los factores que definen la curva de precios (0.5 de factor demanda máximo, 0.75 de factor rendimiento para FINALIZADO) están hardcodeados en `DynamicPricingService.java`.

## "¿Cómo sé yo como owner cuánto estoy ganando?"

Leyendo el balance de $IDEA del contrato marketplace con `idea.balanceOf(marketplaceAddress)`. Filtrando los eventos `TradeExecuted` en blockchain para ver el volumen histórico. Viendo el dashboard del admin que agrega las estadísticas de la base de datos.

## "¿El monto supercargado de la tesorería, quién sos vos?"

Somos la plataforma. Cuando deployamos `IdeaToken.sol`, el constructor mintea todos los tokens a la wallet del deployer, que es la wallet del backend. Esa wallet es la tesorería. Los tokens se distribuyen de ahí a los usuarios mediante ventas en el swap, faucets de testing, o provisión de liquidez.

## "¿Cómo se comunica el frontend con la blockchain?"

El frontend no habla directamente con la blockchain para las operaciones que requieren firmar transacciones. Todo pasa por el backend. El frontend manda un pedido REST, el backend firma la transacción con la clave privada del servidor, la manda a blockchain, espera la confirmación y le devuelve el resultado al frontend. La excepción es la lectura del saldo de $IDEA en la sidebar, que sí se lee directamente desde el navegador usando wagmi.

## "¿Qué es un punto básico (BPS)?"

Es una unidad de medida financiera. Un punto básico es el 0.01%. Lo usamos porque Solidity no tiene decimales. En vez de guardar 2.5%, guardamos 250. Para calcular: monto × 250 / 10000 = 2.5% del monto.

## "¿Qué pasa si la blockchain está caída?"

El backend tiene un modo fallback. Si la transacción blockchain falla, registra la inversión como PENDIENTE en la base de datos para reintentar. El listener `InvestmentEventService.java` reconcilia periódicamente lo que está en la base de datos con lo que está en blockchain.

## "¿Por qué dos tablas de BD tienen el mismo dato que está en blockchain?"

Porque la blockchain es lenta para consultar. Un listado de inversiones que necesite hacer cien lecturas de blockchain tardaría minutos. Con la base de datos, tarda milisegundos. La blockchain es la fuente de verdad para lo crítico (balances, transacciones). La base de datos es la fuente de verdad para lo operativo (historial, búsquedas, reportes). Si hay discrepancia, el listener de eventos la detecta y la corrige.

---

# RESUMEN PARA EL DÍA DE LA EXPO

## Antes de empezar, tener claro esto

Hay tres niveles de respuesta para cualquier pregunta:

**¿Dónde está en la interfaz?** → nombrar la página (`/proyectos`, `/inversiones`, `/billetera`, etc.) y el elemento visual.

**¿Qué hace el backend?** → nombrar el endpoint y el servicio que lo maneja.

**¿Qué toca blockchain o la base de datos?** → nombrar el contrato o la tabla.

## La secuencia de la demo sugerida

Empezar por el catálogo público para mostrar qué ve alguien que no tiene cuenta.

Loguearse con una cuenta de inversor. Mostrar el dashboard con las métricas.

Mostrar el detalle de un proyecto en financiamiento. Explicar el precio dinámico.

Hacer una inversión. Mostrar el txHash resultante y abrirlo en Etherscan para demostrar que la transacción está en blockchain.

Mostrar el historial de inversiones con el estado CONFIRMADA.

Mostrar la billetera con el saldo de $IDEA y el portfolio.

Loguear como admin. Mostrar el dashboard con las estadísticas globales.

Mostrar el proceso de distribución de dividendos.

Mostrar el marketplace con una orden activa.

Si hay tiempo: mostrar qué pasa con un proyecto rechazado.

## Las cinco cosas que el profe siempre pregunta

**Uno**: por qué este usuario puede hacer esto → roles y permisos en el JWT, validados por el Gateway, verificados en el controller.

**Dos**: dónde está definido ese porcentaje → en el contrato Solidity si es financiero, en el backend si es lógica de negocio.

**Tres**: cómo ganan plata → fees del marketplace (2.5%) y del swap (1%), boost, apreciación de tesorería.

**Cuatro**: qué pasa si el proyecto no llega → InvestmentScheduler detecta, marca como RECHAZADO, ejecuta refund() en blockchain, devuelve $IDEA.

**Cinco**: qué vive en blockchain → los balances de tokens, las transacciones, los eventos. Lo que necesita ser confiable sin intermediarios.



----------------------------

Versión 2 — Demanda + Rendimiento del proyecto

### La fórmula

```
precio = precioBase × (1 + demandaRelativa × factorVolatilidad) × (1 + factorRendimiento)
```

Son dos multiplicaciones encadenadas. La primera ajusta por cuánto se vendió. La segunda ajusta por el riesgo del proyecto.

---

### Las dos partes separadas

#### Parte 1 — Factor demanda (igual que la versión anterior)

```
demandaRelativa = tokens vendidos / tokens totales
componenteDemanda = demandaRelativa × factorVolatilidad
```

El `factorVolatilidad` es un valor guardado en la tabla `subtokens`, por defecto `0.50`. Define qué tan sensible es el precio a la demanda. Si es 0.50, cuando se vende el 100% de los tokens el precio sube un 50%.

#### Parte 2 — Factor rendimiento (lo nuevo de esta versión)

El backend consulta la tabla `projects` y lee el `estado` y el porcentaje financiado. Con eso calcula un factor que representa el riesgo del proyecto:

| Estado | Cálculo | Resultado |
|--------|---------|-----------|
| PREPARACION | fijo | **0.00** |
| FINANCIAMIENTO | `0.15 + (progreso × 0.20)` | **entre 0.15 y 0.35** |
| EJECUCION | fijo | **0.50** |
| FINALIZADO | fijo | **0.75** |
| cualquier otro | fijo | **0.00** |

Lo interesante es el FINANCIAMIENTO: no es un valor fijo, sino que **crece linealmente** según cuánto recaudó el proyecto.

```java
// Código real del commit
BigDecimal progreso = montoRecaudado.divide(montoRequerido, 4, RoundingMode.HALF_UP);

return new BigDecimal("0.15").add(
    progreso.multiply(new BigDecimal("0.20"))
).min(new BigDecimal("0.35"));   // máximo 0.35
```

Esto significa:
- Recaudó el **0%** → `0.15 + (0.00 × 0.20) = 0.15`
- Recaudó el **50%** → `0.15 + (0.50 × 0.20) = 0.25`
- Recaudó el **100%** → `0.15 + (1.00 × 0.20) = 0.35`

El `.min(0.35)` es el techo: nunca supera 0.35 mientras está en financiamiento.

---

### Por qué tiene sentido conceptualmente

El factor de rendimiento modela el **riesgo**. A mayor riesgo, el token vale menos (o sea el inversor paga menos). A menor riesgo, el token vale más.

- **PREPARACION (0.00)**: el proyecto no tiene nada concreto, riesgo máximo. La fórmula de rendimiento no agrega nada al precio, solo opera el factor demanda.
- **FINANCIAMIENTO (0.15 a 0.35)**: el proyecto existe pero no está seguro. El precio sube a medida que más gente apuesta por él. Si casi llegó al monto, hay más confianza y el precio es más caro para los que entran tarde.
- **EJECUCION (0.50)**: el proyecto ya se financió y está en desarrollo. El riesgo bajó mucho, el token vale más.
- **FINALIZADO (0.75)**: el proyecto terminó. Riesgo mínimo, precio más alto del ciclo.

La idea de fondo es: **los que invierten temprano pagan menos porque asumen más riesgo. Los que invierten tarde pagan más porque el riesgo ya está confirmado.**

---

### Diferencia clave con la Versión 3 actual

En la V2, durante FINANCIAMIENTO **sí aplica la fórmula completa** con el factor entre 0.15 y 0.35. En la V3 actual, eso desapareció y durante FINANCIAMIENTO el precio es directamente `precioBase`, sin ningún ajuste.

---

### Tres ejemplos numéricos concretos

**Proyecto: precioBase = $10, suministro = 10.000 tokens, factorVolatilidad = 0.50**

#### Ejemplo A — FINANCIAMIENTO al 20% recaudado, 1.000 tokens vendidos

```
demandaRelativa    = 1.000 / 10.000 = 0.10
componenteDemanda  = 0.10 × 0.50   = 0.05
factorRendimiento  = 0.15 + (0.20 × 0.20) = 0.19

precio = 10 × (1 + 0.05) × (1 + 0.19)
       = 10 × 1.05 × 1.19
       = $12.50
```

#### Ejemplo B — FINANCIAMIENTO al 90% recaudado, 5.000 tokens vendidos

```
demandaRelativa    = 5.000 / 10.000 = 0.50
componenteDemanda  = 0.50 × 0.50   = 0.25
factorRendimiento  = 0.15 + (0.90 × 0.20) = 0.33

precio = 10 × (1 + 0.25) × (1 + 0.33)
       = 10 × 1.25 × 1.33
       = $16.63
```

#### Ejemplo C — EJECUCION, 5.000 tokens vendidos

```
demandaRelativa    = 5.000 / 10.000 = 0.50
componenteDemanda  = 0.50 × 0.50   = 0.25
factorRendimiento  = 0.50 (fijo en EJECUCION)

precio = 10 × (1 + 0.25) × (1 + 0.50)
       = 10 × 1.25 × 1.50
       = $18.75
```

#### Tabla comparativa de los tres

| Escenario | Precio | Por qué |
|-----------|--------|---------|
| Financiamiento 20% recaudado | $12.50 | Poco riesgo resuelto, poca demanda |
| Financiamiento 90% recaudado | $16.63 | Casi seguro que se financia, más demanda |
| Ejecución 50% vendido | $18.75 | Proyecto en marcha, riesgo resuelto |

---

### Lo que NO tenía esta versión (diferencias con la actual)

- **No tenía precio mínimo.** Si la demanda caía a cero, el precio podía quedar muy cerca del base o incluso exactamente en él, sin protección.
- **No tenía factor de sobreoferta.** Si 3.000 personas ponían tokens a la venta en el marketplace, eso no afectaba el precio calculado.
- **No distinguía entre "calcular precio para mostrar" y "precio real durante financiamiento".** La fórmula corría igual para todos los estados.