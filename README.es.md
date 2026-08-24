# Peluquería "Lalo Segovia" — API REST de citas

[![CI](https://github.com/eduardoandr3s/peluqueria_citas/actions/workflows/ci.yml/badge.svg)](https://github.com/eduardoandr3s/peluqueria_citas/actions/workflows/ci.yml)

[🇬🇧 English](README.md) | 🇪🇸 Español

Backend de un sistema integral de gestión de citas para una peluquería. Es una API REST que gestiona reservas de servicios, horarios, pagos online y administración de clientes, con flujos separados para clientes y administradores.

## Demo en producción

| Aplicación | URL |
|-----------|-----|
| Panel de administración | https://peluqueria-citas-prod.web.app |
| App de clientes (versión web) | https://peluqueria-citas-app.web.app |
| API + Swagger UI | https://peluqueria-citas-zbxb.onrender.com/swagger-ui.html |

> La API corre en el tier gratuito de Render y **se duerme tras 15 minutos de inactividad**: la primera petición puede tardar ~30-60 segundos. Stripe está en **modo test** — usa la tarjeta `4242 4242 4242 4242` con cualquier fecha futura y CVC.

## Tecnologías

* **Java 21 (Temurin LTS)**
* **Spring Boot 4.1.1** (framework principal)
* **PostgreSQL** (base de datos relacional)
* **Flyway** (migraciones de esquema, V1-V10)
* **Spring Data JPA / Hibernate** (ORM)
* **Supabase Storage** (almacenamiento de objetos para imágenes, por su API REST — sin SDK de S3). Opcional: sin credenciales se usa el disco local
* **openhtmltopdf + Thymeleaf** (recibos de pago en PDF renderizados desde una plantilla HTML, reutilizando el motor que ya usan los correos)
* **Spring Security + JWT** (autenticación stateless y autorización por roles)
* **BCrypt** (hash unidireccional de contraseñas)
* **Stripe API** (pagos online: PaymentIntents, webhooks, reembolsos)
* **Spring Mail** (correos transaccionales y recordatorios de cita)
* **Bucket4j** (rate limiting)
* **Spring AI + Gemini** (asistente conversacional con tool calling sobre los services reales)
* **springdoc-openapi** (documentación Swagger UI)
* **Maven** · **Lombok** · **Docker Compose** (entorno de desarrollo local)
* **JUnit 5 + Mockito** (tests unitarios) · **Testcontainers** (tests de integración contra un PostgreSQL real)
* **GitHub Actions** (CI: suite completa en cada push y pull request)

## Características

* **Arquitectura por dominio:** el código se organiza por módulo de negocio (`usuario/`, `cita/`, `servicio/`, `pago/`, `peluquero/`, `calendario/`, `estadistica/`, `notificacion/`, `asistente/`, `auth/`, `security/`). Cada módulo agrupa su entidad, controller, service, repository y DTOs.
* **Constructor injection:** inyección de dependencias vía constructor con campos `final` (sin `@Autowired`), siguiendo las buenas prácticas de Spring para inmutabilidad y testabilidad.
* **Autenticación JWT con roles:** login/registro con access tokens JWT (30 min) más **refresh tokens con rotación** (30 días). Dos roles: `USER` (clientes) y `ADMIN`. En cada request se valida además que la cuenta siga activa y que el `tokenVersion` del token coincida con el de la BD: cambiar la contraseña o el rol **revoca** los tokens emitidos antes (el rol y el estado activo se leen siempre de la BD, nunca del token).
* **Recuperación de contraseña:** tokens de un solo uso enviados por correo, con expiración y **rate limiting por IP** (Bucket4j). El endpoint responde siempre 200 para evitar enumeración de usuarios.
* **Pagos online con Stripe:** creación de PaymentIntents, **webhooks firmados** (firma verificada con el SDK oficial), procesamiento idempotente de eventos, pagos manuales (efectivo/transferencia) y reembolsos. Un pago confirmado auto-confirma la cita.
* **Multi-peluquero:** CRUD de peluqueros y **peluquero opcional** por cita. Los conflictos de horario se comprueban por peluquero ("sin asignar" bloquea el hueco completo) y la disponibilidad se puede consultar para un peluquero concreto.
* **Endpoint de disponibilidad:** `/api/citas/disponibilidad` calcula los huecos libres de 30 minutos para un servicio en una fecha — opcionalmente para un peluquero concreto — descontando citas existentes y respetando el horario laboral.
* **Validación de conflictos de horario:** se rechazan citas que se solapen, usando la duración del servicio para calcular cada rango.
* **Validación de horario laboral:** las citas solo se pueden agendar de lunes a sábado de 9:00 a 20:00, y nunca en el pasado. El horario y los días de la semana cerrados son **configurables** vía properties (`peluqueria.horario.apertura` / `peluqueria.horario.cierre` / `peluqueria.horario.dias-cerrados`).
* **Días cerrados (festivos y cierres puntuales):** un ADMIN puede bloquear una fecha concreta con un motivo opcional (`/api/dias-bloqueados`). Un día bloqueado no devuelve horas libres y rechaza agendar y reprogramar indicando el motivo. `GET /api/citas/dias-cerrados` devuelve todos los días cerrados de un rango —los días de la semana fijos (domingo) y las fechas bloqueadas, unificados— para que los clientes los pinten **no seleccionables** en vez de dejar elegir un día sin horas disponibles. Bloquear un día que aún tiene citas vivas se rechaza (409) en vez de anularlas por sorpresa.
* **Subida de imágenes (fotos de catálogo y avatares):** un único puerto de almacén (`AlmacenFicheros`) con dos implementaciones —**Supabase Storage** por su API REST y disco local—, así que el proyecto arranca sin cuenta en ningún servicio. Lo que se sube se valida por **magic bytes** (los primeros bytes del fichero), nunca por el `Content-Type` ni el nombre, porque los dos los pone quien sube; la clave del objeto la genera el servidor con un UUID, así que un nombre tipo `../../etc/passwd.jpg` no llega al almacén. Sustituir una foto **borra el objeto anterior** en vez de dejar huérfanos comiendo cuota. El catálogo de servicios usa un bucket **público** y los avatares uno **privado** que se lee con **URL firmada de vida corta**, porque una foto de perfil es un dato personal. Límites: 2 MB por fichero (**413** si la petición se pasa, **400** si el contenido no es un JPEG/PNG/WebP válido) y **502** si el almacén no responde.
* **En la base de datos va la clave del objeto, no la URL** (`servicios.imagen_clave`, `usuarios.avatar_clave`). La URL se construye al leer, y por eso cambiar de bucket o de proveedor —o pasar un bucket a privado, que es justo lo que hacen los avatares— es configuración y no una migración de datos.
* **Asistente conversacional (Spring AI + Gemini):** `POST /api/asistente` responde en lenguaje natural sobre servicios, precios, horario, festivos y huecos libres. No improvisa: cada dato sale de una **herramienta** (*tool calling*) que llama al service real, y hasta la fecha de hoy se la damos nosotros, porque un modelo que supone en qué día vive resuelve «mañana» inventándose una fecha. Cuatro decisiones que son el diseño:
    * **Solo lectura, y por eso puede ser público.** Ninguna herramienta escribe, y ninguna devuelve datos de clientes. Eso hace que una inyección de prompt no tenga nada que romper y que al proveedor no le llegue ni un nombre ni un teléfono — que es justo lo que permite usar un tier gratuito cuyos términos reservan el derecho a entrenar con los prompts. Si el asistente creciera hacia agendar citas, esa premisa se cae y habría que cambiar de proveedor antes que de código.
    * **Apagado por defecto, y es un interruptor de verdad.** `spring.ai.model.chat` vale `none` si no se configura, así que sin API key el dominio entero no se registra y la aplicación arranca igual: el asistente es un extra, no una dependencia. Esto no es decorativo — la autoconfiguración de Spring AI solo comprueba que la clase esté en el classpath, no que haya credenciales, así que sin ese `none` la aplicación **no arranca** en cualquier entorno sin key, tests de integración incluidos.
    * **El límite es el gasto, no el abuso.** Un endpoint público que consume cuota de un tier gratuito se agota en minutos si alguien encuentra la URL, así que va limitado por IP con el `RateLimitFilter` que ya existía. Los cupos son **independientes por ruta**: quemar el del asistente no puede dejar a nadie sin poder recuperar su contraseña.
    * **Cada token se paga en todos los turnos siguientes.** El historial se reenvía completo en cada mensaje, así que las herramientas devuelven records mínimos en vez de los DTOs de la aplicación: la URL de la foto de un servicio no ayuda a decir cuánto cuesta y costaría dinero en cada turno. Por lo mismo hay tope de historial, de respuesta y de rango de fechas, y la respuesta incluye el consumo de tokens, porque en un tier gratuito el límite es la cuota y sin medirlo no se ve venir.
* **Recibo de pago en PDF:** `GET /api/pagos/{id}/recibo` renderiza un justificante de una página desde una plantilla Thymeleaf, **generado al vuelo y sin almacenarse** — siempre se puede reconstruir desde la base de datos, así que guardarlo solo añadiría cuota y ciclo de vida que gestionar. Solo para pagos **cobrados o reembolsados**: emitir un recibo por dinero que no ha entrado afirmaría algo falso, así que cualquier otro estado responde **409**. El documento dice con claridad que es un justificante de pago y **no una factura**, porque no lleva datos fiscales.
* **Estadísticas de negocio:** `GET /api/estadisticas` (solo ADMIN) devuelve citas por estado, ingresos desglosados por método de pago (excluyendo reembolsos, calculados por fecha de pago), servicios más demandados y clientes nuevos. Por defecto usa los **últimos 30 días** si no se indica rango.
* **Notificaciones por correo:** emails dirigidos por eventos (registro, cita agendada, modificada, anulada, pago confirmado, cambios de contraseña) desacoplados de la lógica de negocio mediante eventos de Spring (`@TransactionalEventListener(AFTER_COMMIT)`), más un **recordatorio de cita 24h antes** enviado por un scheduler (corre cada hora, `Clock` inyectable para testabilidad, el flag `recordatorio_enviado` garantiza un único envío).
* **Control de propiedad (ownership):** un `USER` solo puede ver, modificar o eliminar sus propias citas y sus propios datos; un `ADMIN` puede acceder a todo. Los accesos no autorizados devuelven `403 Forbidden`.
* **Patrón DTO:** cada entidad tiene DTOs separados para creación, actualización parcial y respuesta. Nunca se expone información sensible.
* **Paginación y ordenación:** los listados de citas y usuarios están paginados (`page`, `size`, `sort`) y devuelven un `Page` de Spring Data.
* **Soft delete + reactivación:** usuarios, servicios y peluqueros no se eliminan físicamente, se desactivan. Los usuarios desactivados pueden listarse (`?incluirInactivos=true`) y reactivarse (`PATCH /api/usuarios/{id}/activar`).
* **Búsqueda de usuarios:** `GET /api/usuarios?search=` filtra por nombre o email (contains, case-insensitive) en la BD, combinable con `incluirInactivos` y la paginación.
* **Manejo global de excepciones:** `@RestControllerAdvice` con handlers específicos para validación (400), no encontrado (404), acceso denegado (403), conflictos (409) y un handler genérico (500) que no expone detalles internos. Incluye logging con SLF4J.
* **Documentación OpenAPI / Swagger UI:** generada automáticamente con springdoc-openapi, disponible en `/swagger-ui.html` y `/v3/api-docs`.
* **Perfiles de configuración:** entornos `dev` y `prod` separados. El esquema se gestiona con **migraciones Flyway** (`src/main/resources/db/migration/`). Con el perfil `prod` la aplicación **se niega a arrancar** sin credenciales de almacén en vez de caer al disco local: en un contenedor efímero ese fallo es silencioso —las subidas funcionan y desaparecen en el siguiente despliegue—.
* **Suite de tests (295 tests):** 274 tests unitarios que cubren la lógica de negocio sin Spring context ni base de datos, más 21 tests de integración con **Testcontainers** (PostgreSQL real en Docker) que cubren autenticación, reglas de ownership, estadísticas, pagos y el flujo completo del webhook de Stripe con verificación de firma real.

## Estructura del proyecto

```
com.segovia.peluqueria/
├── almacen/        # Almacén de ficheros: puerto + adaptadores Supabase/disco, validación por magic bytes
├── auth/           # Login, registro, refresh tokens, reset de contraseña (con rate limit)
├── cita/           # Citas: agendado, conflictos, slots de disponibilidad, horario laboral
├── config/         # Configuración transversal (eventos asíncronos, scheduling)
├── estadistica/    # Estadísticas de negocio para el dashboard de admin (solo ADMIN)
├── exception/      # Manejo global de excepciones y excepciones compartidas
├── notificacion/   # Eventos de dominio, correos y scheduler del recordatorio 24h
├── pago/           # Pagos: Stripe PaymentIntents, webhooks, pagos manuales, reembolsos
├── peluquero/      # Peluqueros: CRUD y disponibilidad por peluquero
├── security/       # SecurityConfig, servicio y filtro JWT, CORS
├── servicio/       # Catálogo de servicios
└── usuario/        # Usuarios, roles, soft delete, búsqueda
```

Todos los módulos de negocio siguen el mismo esquema: entidad JPA, controller, service, repository y un paquete `dto/`.

## Tests

**295 tests** se ejecutan en CI en cada push (GitHub Actions).

### Tests unitarios (246)

Cubren toda la lógica de negocio sin Spring context ni base de datos (pocos segundos):

| Clase | Tests | Cobertura |
|-------|-------|-----------|
| CitaServiceTest | 48 | Agendar, horario laboral, días cerrados, conflictos, CRUD, ownership, disponibilidad, paginación, validación de peluquero, auto-confirmación al pagar |
| UsuarioServiceTest | 38 | CRUD, email duplicado, hashing, soft delete, ownership, reactivar, paginación, búsqueda, subir/borrar avatar |
| PagoServiceTest | 32 | PaymentIntents, webhooks, pago manual, reembolsos, polling, concurrencia, quién puede pedir un recibo y en qué estados |
| CalendarioServiceTest | 17 | Días de la semana cerrados, bloquear/desbloquear fechas, fecha pasada, duplicados, días con citas vivas, rangos de días cerrados |
| AsistenteHerramientasTest | 11 | Las herramientas del asistente: delegación en los services reales, que solo viaje al modelo lo que necesita (ni URLs de imágenes ni descripciones), tope del rango de días cerrados, fecha inválida traducida a un mensaje que el modelo puede corregir, y que «hoy» salga del `Clock` y no de una suposición |
| AsistenteServiceTest | 9 | Traducción del historial al rol correcto, lectura del consumo de tokens, y detección de cuota agotada frente a cualquier otro fallo (incluida una causa cíclica, que colgaría el recorrido) |
| RateLimitFilterTest | 8 | Cupos **independientes por ruta**: quemar el del asistente no deja sin intentos la recuperación de contraseña. Además cupo por IP, `X-Forwarded-For` tras el proxy, y que no toque rutas ni métodos ajenos |
| ServicioServiceTest | 16 | CRUD, soft delete, subir/sustituir/borrar la foto de catálogo |
| ReciboPdfGeneradorTest | 10 | Renderiza el **PDF de verdad** con la plantilla de producción y le vuelve a extraer el texto con PDFBox: datos del pago y de la cita, aviso de reembolso, la advertencia de que no es una factura, formato de decimales fijo |
| ValidadorImagenTest | 10 | Validación por magic bytes: JPEG/PNG/WebP reales, `Content-Type` que miente, extensión que miente, fichero vacío y fuera de tamaño, clave generada por el servidor |
| JwtServiceTest | 9 | Generar/extraer/validar tokens, firmas, tokenVersion |
| AuthControllerTest | 8 | Login, registro, credenciales inválidas |
| RefreshTokenServiceTest | 8 | Rotación, revocación, expiración |
| JwtAuthenticationFilterTest | 7 | Filtro con/sin token, token inválido/expirado, cuenta desactivada, tokenVersion |
| PasswordResetServiceTest | 7 | Solicitud, restablecimiento, expiración, anti-enumeración |
| PeluqueroServiceTest | 7 | CRUD de peluqueros, soft delete |
| SupabaseStorageAlmacenTest | 7 | Llamadas REST al almacén con `MockRestServiceServer`: subir, borrar, firmar URL, y que las claves con carpeta no se escapan |
| AlmacenConfigTest | 5 | Elección de adaptador según el perfil: con `prod` y sin credenciales no arranca, en `dev` cae al disco |
| RecordatorioCitaSchedulerTest | 5 | Recordatorio 24h: envío único, ignora anuladas/ya notificadas, Clock inyectable |
| CustomUserDetailsServiceTest | 4 | Carga de usuario, roles, estado |
| HorarioPropertiesTest | 4 | Binding de las properties de horario, incluida la lista de días de la semana cerrados |
| EstadisticasServiceTest | 3 | Agregaciones, desglose de ingresos, exclusión de reembolsos |
| PeluqueriaApplicationTests | 1 | El contexto de Spring carga (solo se ejecuta si `DB_USERNAME` está definida) |

```bash
# Solo tests unitarios (no requiere Docker)
./mvnw test -Dtest='!*IntegrationTest'
```

### Tests de integración (21, Testcontainers)

Arrancan la aplicación completa contra un **PostgreSQL real** levantado en Docker (`@ServiceConnection`), con las migraciones Flyway aplicadas:

* **EstadisticasIntegrationTest** (5) — estadísticas sobre datos reales: rango por defecto de 30 días, ingresos por método de pago, reembolsos excluidos.
* **WebhookIntegrationTest** (3) — webhook de Stripe end-to-end: un evento `payment_intent.succeeded` firmado se verifica con la **comprobación de firma real del SDK de Stripe**, el pago pasa a `PAGADO` y la cita se confirma; los eventos duplicados se procesan una sola vez (idempotencia); las firmas inválidas reciben 400.
* **PagosIntegrationTest** (10) — listado de pagos del panel (paginación, filtros de rango y estado, solo ADMIN) y el recibo en PDF por HTTP: el dueño recibe un PDF de verdad como adjunto, un ADMIN el de cualquiera, otro cliente 403 y un pago no cobrado 409.
* **OwnershipIntegrationTest** (2) — un usuario no puede leer (GET) ni editar (PUT) la cita de otro (403); `/api/usuarios/me` nunca expone la contraseña.
* **AuthIntegrationTest** (1) — flujo completo de registro/login por HTTP.

> Ningún test agenda «mañana»: un helper busca el **próximo lunes**, así que una ejecución en sábado no puede caer en un día cerrado y fallar por algo que no es lo que se está probando.

```bash
# Suite completa, tests de integración incluidos (requiere Docker corriendo)
./mvnw test
```

## Endpoints de la API

### Autenticación (público)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/auth/registro` | Registrar nuevo usuario |
| POST | `/api/auth/login` | Iniciar sesión (devuelve JWT + refresh token) |
| POST | `/api/auth/recuperar` | Solicitar enlace de recuperación de contraseña (responde 200 siempre, anti-enumeración) |
| POST | `/api/auth/reset` | Restablecer la contraseña con el token recibido por correo (un solo uso, caduca) |
| POST | `/api/auth/refresh` | Rotar refresh token (devuelve nuevo access + refresh token) |
| POST | `/api/auth/logout` | Revocar el refresh token |

> Los endpoints de recuperación están limitados por IP (Bucket4j): por defecto 5 peticiones cada 15 minutos; al superarlo responden 429. Configurable con `RESET_EXPIRACION_MINUTOS`, `RATELIMIT_RESET_CAPACIDAD` y `RATELIMIT_RESET_VENTANA_MINUTOS`. El enlace del correo apunta a `FRONTEND_URL` + `/reset?token=...`.

### Servicios
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/servicios` | Público | Listar servicios activos |
| GET | `/api/servicios/{id}` | Público | Obtener servicio por ID |
| POST | `/api/servicios` | ADMIN | Crear servicio |
| PUT | `/api/servicios/{id}` | ADMIN | Actualizar servicio |
| DELETE | `/api/servicios/{id}` | ADMIN | Desactivar servicio (soft delete) |
| POST | `/api/servicios/{id}/imagen` | ADMIN | Subir o sustituir la foto de catálogo (`multipart/form-data`, campo `imagen`). Devuelve el servicio ya con la URL nueva |
| DELETE | `/api/servicios/{id}/imagen` | ADMIN | Borrar la foto de catálogo |

### Usuarios
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/usuarios` | ADMIN | Listar usuarios (paginado). `?incluirInactivos=true` incluye desactivados. `?search=` filtra por nombre o email |
| GET | `/api/usuarios/{id}` | Propio/ADMIN | Obtener usuario por ID |
| POST | `/api/usuarios` | ADMIN | Crear usuario |
| PUT | `/api/usuarios/{id}` | Propio/ADMIN | Actualizar usuario |
| PATCH | `/api/usuarios/{id}/rol` | ADMIN | Cambiar rol (con guard anti-lockout del último ADMIN) |
| PATCH | `/api/usuarios/{id}/activar` | ADMIN | Reactivar un usuario desactivado |
| DELETE | `/api/usuarios/{id}` | ADMIN | Desactivar usuario (soft delete) |
| POST | `/api/usuarios/{id}/avatar` | Propio/ADMIN | Subir o sustituir la foto de perfil (`multipart/form-data`, campo `imagen`) |
| DELETE | `/api/usuarios/{id}/avatar` | Propio/ADMIN | Borrar la foto de perfil |

> Los endpoints de avatar solo piden `authenticated()` en `SecurityConfig`, no ADMIN: la propiedad la comprueba el service, que es la capa que sabe de quién es el id. La URL firmada se emite en `/me`, en `GET /{id}` y justo después de subir —nunca en el listado de usuarios—, así que recorrer usuarios no cuesta ninguna firma. Si el almacén no responde, esas respuestas devuelven el usuario **sin foto** y dejan un WARN en vez de fallar con 502; solo la subida propaga el error.

### Citas
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/citas` | USER/ADMIN | Listar citas (paginado). Un USER solo ve las suyas, un ADMIN todas |
| GET | `/api/citas/disponibilidad` | USER/ADMIN | Slots libres para `?fecha=YYYY-MM-DD&idServicio=N`. Opcional `&peluqueroId=N` para un peluquero concreto. Vacío en los días cerrados |
| GET | `/api/citas/dias-cerrados` | USER/ADMIN | Días cerrados (días de la semana cerrados + fechas bloqueadas) con su motivo. Opcional `?desde=&hasta=`; por defecto los 3 próximos meses, con tope de rango de 12 meses |
| GET | `/api/citas/{id}` | Propio/ADMIN | Obtener cita por ID |
| POST | `/api/citas` | USER/ADMIN | Agendar cita (un USER solo para sí mismo), opcionalmente con peluquero |
| PUT | `/api/citas/{id}` | Propio/ADMIN | Actualizar cita |
| DELETE | `/api/citas/{id}` | Propio/ADMIN | Eliminar cita |

### Peluqueros
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/peluqueros` | Autenticado | Listar peluqueros activos |
| GET | `/api/peluqueros/{id}` | Autenticado | Obtener peluquero por ID |
| POST | `/api/peluqueros` | ADMIN | Crear peluquero |
| PUT | `/api/peluqueros/{id}` | ADMIN | Actualizar peluquero |
| DELETE | `/api/peluqueros/{id}` | ADMIN | Desactivar peluquero (soft delete) |

### Días cerrados
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/dias-bloqueados` | Autenticado | Listar los días bloqueados de hoy en adelante |
| POST | `/api/dias-bloqueados` | ADMIN | Bloquear una fecha (`fecha` + `motivo` opcional). 409 si ya estaba bloqueada o si ese día tiene citas vivas |
| DELETE | `/api/dias-bloqueados/{id}` | ADMIN | Desbloquear una fecha |

### Pagos
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| POST | `/api/pagos/crear-intent` | USER/ADMIN | Crear Stripe PaymentIntent para una cita |
| POST | `/api/pagos/webhook` | Público | Webhook de Stripe (firma verificada, idempotente) |
| POST | `/api/pagos/manual` | ADMIN | Registrar pago en efectivo o transferencia |
| POST | `/api/pagos/{citaId}/reembolsar` | ADMIN | Reembolsar un pago (Stripe o manual) |
| GET | `/api/pagos` | ADMIN | Listar pagos (paginado). Opcional `?desde=&hasta=&estado=&metodo=`; el rango incluye los dos extremos y filtra por fecha de pago, con la de creación como respaldo |
| GET | `/api/pagos/{id}/recibo` | Propio/ADMIN | Descargar el recibo en PDF (`attachment`). **El id es del pago, no de la cita.** 409 si el pago no está `PAGADO` ni `REEMBOLSADO` |
| GET | `/api/pagos/cita/{citaId}` | Propio/ADMIN | Consultar el pago de una cita |

> El listado filtra por `COALESCE(fechaPago, fechaCreacion)`, de modo que `?estado=PAGADO` sobre un rango devuelve exactamente los pagos que suman los ingresos que `/api/estadisticas` reporta para ese mismo periodo — que es lo que permite desglosar las barras de ingresos del dashboard.

### Estadísticas
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/estadisticas` | ADMIN | Citas por estado, ingresos por método de pago, top servicios y clientes nuevos. `?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` opcional; por defecto los últimos 30 días |

### Asistente (público)
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| POST | `/api/asistente` | Público | Pregunta en lenguaje natural sobre servicios, precios, horario, días cerrados y huecos libres. El cuerpo lleva `mensaje` y el `historial` de la conversación (máx. 10 turnos). Devuelve la respuesta y el consumo de tokens del turno. Limitado a 10 peticiones/hora por IP; 503 si el proveedor falla o la cuota está agotada; 404 si el asistente está apagado |

### Documentación (público)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Interfaz Swagger UI interactiva |
| GET | `/v3/api-docs` | Especificación OpenAPI (JSON) |

## Modelo de datos

El esquema se gestiona con **Flyway** (migraciones `V1` a `V10` en `src/main/resources/db/migration/`):

* **`usuarios`** — clientes y administradores: nombre, email único, teléfono, contraseña hasheada, rol, flag de activo, `token_version` y `avatar_clave`.
* **`servicios`** — catálogo de la peluquería (cortes, tintes...): descripción, duración en minutos, precio, flag de activo e `imagen_clave`.
* **`peluqueros`** — peluqueros/estilistas, con soft delete. Una cita puede asignarse opcionalmente a uno.
* **`citas`** — vincula un `usuario` con un `servicio` (y opcionalmente un `peluquero`) en una fecha/hora concreta. Enum de estado (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) y flag `recordatorio_enviado` para el recordatorio 24h.
* **`dias_bloqueados`** — fechas en las que la peluquería no abre (festivos, cierres puntuales), con motivo opcional. Los días de la semana cerrados de forma fija no se guardan aquí: salen de la configuración.
* **`pagos`** — pagos vinculados 1:1 a una cita. Tarjeta (Stripe), efectivo o transferencia. Estados: `PENDIENTE`, `PAGADO`, `REEMBOLSADO`, `CANCELADO`.
* **`stripe_evento`** — IDs de eventos de Stripe ya procesados, garantiza la idempotencia del webhook.
* **`password_reset_token`** — tokens de un solo uso para restablecer contraseña, con expiración.
* **`refresh_token`** — refresh tokens persistentes para rotación de sesión.

> `imagen_clave` y `avatar_clave` guardan la **clave del objeto** (p. ej. `12/uuid.jpg`), no una URL. La URL se construye al leer, así que cambiar de bucket o de proveedor —o pasar un bucket a privado, que es justo lo que hacen los avatares frente a las fotos de catálogo— es configuración y no una migración de datos. En los avatares importa doble: la URL va firmada y caduca, así que persistirla no tendría sentido.

## Puesta en marcha

### Opción A — Docker Compose (la más rápida)

Con Docker instalado, un solo comando levanta PostgreSQL y la API:

```bash
git clone https://github.com/eduardoandr3s/peluqueria_citas.git
cd peluqueria_citas
docker compose up --build
```

La API queda disponible en `http://localhost:8080` (Swagger UI en `/swagger-ui.html`). Flyway aplica todas las migraciones automáticamente. El envío de correo va desactivado (`MAIL_ENABLED=false`) y no hacen falta claves de Stripe salvo que pruebes pagos.

### Opción B — JDK + PostgreSQL locales

1. **Prerrequisitos:** JDK 21 y un PostgreSQL en el puerto `5432`.

2. **Crear la base de datos:**
    ```sql
    CREATE DATABASE peluqueria_db;
    ```

3. **Configurar variables de entorno** (en tu sistema o IDE):
    * `DB_USERNAME` / `DB_PASSWORD`: credenciales de PostgreSQL.
    * `JWT_SECRET`: clave secreta para firmar los JWT (mínimo 32 caracteres).
    * `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` *(opcional)*: claves de Stripe (modo test) para usar pagos.
    * `MAIL_USERNAME` / `MAIL_PASSWORD` *(opcional)*: credenciales SMTP para correos; con `MAIL_ENABLED=false` se desactiva el envío en dev.
    * `BUSINESS_EMAIL`: correo del negocio para notificaciones.
    * `FRONTEND_URL`: URL del frontend para los enlaces de los correos (default `http://localhost:4200`).
    * `CORS_ALLOWED_ORIGINS` *(solo perfil `prod`)*: orígenes permitidos separados por coma. El perfil `dev` ya permite `http://localhost:4200`.
    * `SUPABASE_URL` / `SUPABASE_SERVICE_KEY` *(opcionales)*: almacén de objetos para las imágenes. **Sin ellas se escribe en el disco local y la aplicación arranca igual**, así que este repo se puede clonar y ejecutar sin cuenta en ningún servicio externo. La service key salta las políticas de seguridad de fila, así que vive solo en el servidor: nunca en un frontend, nunca en un commit. Con el perfil `prod` son **obligatorias**: sin ellas la aplicación no arranca.
    * `ASISTENTE_MODELO` / `GEMINI_API_KEY` *(opcionales)*: encienden el asistente conversacional. Van **las dos juntas**: `ASISTENTE_MODELO=google-genai` y la API key de [Google AI Studio](https://aistudio.google.com/apikey). Sin ellas el asistente no se despliega y el resto del API funciona igual.

    *(El horario laboral se puede ajustar con `peluqueria.horario.apertura` y `peluqueria.horario.cierre`; por defecto 09:00-20:00.)*

4. **Ejecutar la aplicación:**
    ```bash
    ./mvnw spring-boot:run
    ```

5. **Crear un usuario ADMIN (opcional):** registra un usuario y promuévelo directamente en PostgreSQL:
    ```sql
    UPDATE usuarios SET rol = 'ADMIN' WHERE email = 'tu-email@ejemplo.com';
    ```

## Despliegue

* **API:** Render (Docker, `Dockerfile` multi-stage en este repo). Cada push a `main` redespliega.
* **Base de datos:** Supabase (PostgreSQL gestionado, conectado a través de su session pooler IPv4).
* **Almacén de imágenes:** Supabase Storage, con dos buckets que hay que crear — **`servicios`** (lectura pública) y **`avatares`** (privado, se lee con URL firmada). Los binarios nunca viven junto a la aplicación: el disco de Render es efímero y cada push a `main` lo borra.
* **Email:** los correos transaccionales salen por el relay SMTP de un proveedor en el puerto **2525** (el tier Free de Render bloquea los puertos SMTP salientes 25/465/587).
* **Frontends:** Firebase Hosting (ver abajo).
* **CI:** GitHub Actions ejecuta la suite completa — tests unitarios + integración con Testcontainers — en cada push y pull request.

## Frontend

El proyecto incluye dos aplicaciones frontend en un repositorio separado:

* **Panel de administración** ([Angular 21](https://angular.dev) zoneless + Tailwind v4), con gestión de citas/usuarios/servicios/peluqueros, pagos y dashboard de estadísticas.
* **App móvil para clientes** ([Ionic 8](https://ionicframework.com) + Angular + Capacitor), con reserva de citas, selección de peluquero, pago con Stripe y login biométrico.

Ambos viven en el monorepo [peluqueria_citas_frontend](https://github.com/eduardoandr3s/peluqueria_citas_frontend) (npm workspaces), compartiendo modelos y servicios en la librería `packages/core`.

---
*Desarrollado por Eduardo Andrés Segovia Román.*
