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
* **Galería de trabajos:** fotos del trabajo de la peluquería con **orden manual**, en un bucket público (`galeria`) porque son material promocional. Se leen **sin cuenta**, igual que el catálogo, y solo un ADMIN sube, ordena y borra. Cada foto guarda **dos claves**, la imagen y una miniatura: la rejilla se pinta siempre con la miniatura y la grande se pide solo al abrir una foto, porque el límite del plan gratuito de Storage es el **tráfico** y una rejilla servida con las imágenes grandes lo multiplica por diez. La miniatura la genera el cliente y viaja en el mismo multipart —el servidor tiene 0,1 CPU en producción y el navegador escala gratis—, se valida por magic bytes igual que la grande y, si no llega, la respuesta cae a la imagen grande en vez de dejar un hueco. Borrar una foto borra **los dos objetos**, no solo la grande.
* **En la base de datos va la clave del objeto, no la URL** (`servicios.imagen_clave`, `usuarios.avatar_clave`). La URL se construye al leer, y por eso cambiar de bucket o de proveedor —o pasar un bucket a privado, que es justo lo que hacen los avatares— es configuración y no una migración de datos.
* **Asistente conversacional (Spring AI + Gemini):** `POST /api/asistente` responde en lenguaje natural sobre servicios, precios, horario, festivos y huecos libres. No improvisa: cada dato sale de una **herramienta** (*tool calling*) que llama al service real, y hasta la fecha de hoy se la damos nosotros, porque un modelo que supone en qué día vive resuelve «mañana» inventándose una fecha. Cuatro decisiones que son el diseño:
    * **Solo lectura, y por eso puede ser público.** Ninguna herramienta escribe, y ninguna devuelve datos de clientes. Eso hace que una inyección de prompt no tenga nada que romper y que al proveedor no le llegue ni un nombre ni un teléfono — que es justo lo que permite usar un tier gratuito cuyos términos reservan el derecho a entrenar con los prompts. Si el asistente creciera hacia agendar citas, esa premisa se cae y habría que cambiar de proveedor antes que de código.
    * **Apagado por defecto, y es un interruptor de verdad.** `spring.ai.model.chat` vale `none` si no se configura, así que sin API key el dominio entero no se registra y la aplicación arranca igual: el asistente es un extra, no una dependencia. Esto no es decorativo — la autoconfiguración de Spring AI solo comprueba que la clase esté en el classpath, no que haya credenciales, así que sin ese `none` la aplicación **no arranca** en cualquier entorno sin key, tests de integración incluidos.
    * **El límite es el gasto, no el abuso.** Un endpoint público que consume cuota de un tier gratuito se agota en minutos si alguien encuentra la URL, así que va limitado por IP con el `RateLimitFilter` que ya existía. Los cupos son **independientes por ruta**: quemar el del asistente no puede dejar a nadie sin poder recuperar su contraseña.
    * **Cada token se paga en todos los turnos siguientes.** El historial se reenvía completo en cada mensaje, así que las herramientas devuelven records mínimos en vez de los DTOs de la aplicación: la URL de la foto de un servicio no ayuda a decir cuánto cuesta y costaría dinero en cada turno. Por lo mismo hay tope de historial, de respuesta y de rango de fechas, y la respuesta incluye el consumo de tokens, porque en un tier gratuito el límite es la cuota y sin medirlo no se ve venir.
* **Recibo de pago en PDF:** `GET /api/pagos/{id}/recibo` renderiza un justificante de una página desde una plantilla Thymeleaf, **generado al vuelo y sin almacenarse** — siempre se puede reconstruir desde la base de datos, así que guardarlo solo añadiría cuota y ciclo de vida que gestionar. Solo para pagos **cobrados o reembolsados**: emitir un recibo por dinero que no ha entrado afirmaría algo falso, así que cualquier otro estado responde **409**. El documento dice con claridad que es un justificante de pago y **no una factura**, porque no lleva datos fiscales.
* **Estadísticas de negocio:** `GET /api/estadisticas` (solo ADMIN) devuelve citas por estado, ingresos desglosados por método de pago (excluyendo reembolsos, calculados por fecha de pago), servicios más demandados y clientes nuevos. Por defecto usa los **últimos 30 días** si no se indica rango.
* **Notificaciones por correo:** emails dirigidos por eventos (registro, cita agendada, modificada, anulada, pago confirmado, cambios de contraseña) desacoplados de la lógica de negocio mediante eventos de Spring (`@TransactionalEventListener(AFTER_COMMIT)`), más un **recordatorio de cita 24h antes** enviado por un scheduler (corre cada hora, `Clock` inyectable para testabilidad, el flag `recordatorio_enviado` garantiza un único envío).
* **Tres roles, con `PELUQUERO` en medio:** un `USER` es el cliente, un `ADMIN` lo puede todo, y un `PELUQUERO` ve **su agenda** (las citas asignadas a su ficha, no las de la casa), las cierra y consulta su propia producción. La ficha de `peluqueros` y la cuenta de `usuarios` se enlazan con una FK única y **opcional en los dos sentidos**: una ficha sin cuenta es un profesional por el que agenda el admin, y una cuenta con el rol y sin ficha simplemente no tiene agenda. No hay jerarquía implícita de Spring Security: cada regla de `SecurityConfig` dice explícitamente quién pasa, porque una jerarquía en un fichero y las reglas en otro es como se abre un endpoint sin que nadie lo note.
* **Cierre de cita y producción:** `PATCH /api/citas/{id}/cierre` deja la cita en `COMPLETADA`, `NO_ASISTIO` o `ANULADA` con observaciones y un `clienteContactado`, y sella quién la cerró y cuándo. Al completar **congela** en la cita el precio del servicio y el porcentaje de comisión, y esa copia es lo que sostiene el resto: sin ella, subir una tarifa en junio cambiaría la producción y las comisiones ya liquidadas de marzo. `GET /api/produccion/mia` suma **solo lo completado Y cobrado** —el dinero se cuenta cuando ha entrado, y el efectivo entra por el pago manual—, con el trabajo hecho y sin cobrar aparte para que no desaparezca de ninguna pantalla. La comisión es un porcentaje por peluquero con **excepciones por servicio**, porque un tinte no comisiona como un corte.
* **Permisos por rol configurables:** un administrador afina desde el panel qué puede hacer cada rol —hoy, que un peluquero cobre en efectivo sus propias citas (`PAGO_MANUAL_REGISTRAR`) y que mueva de fecha las de su agenda (`CITA_REPROGRAMAR`)—. La regla que lo sujeta todo es que **un permiso estrecha y nunca abre**: se consultan desde los services, después de la regla de rol de `SecurityConfig`, así que encender uno no concede nada que el rol ya prohibiera. El catálogo es un enum del código y la base de datos solo guarda el estado, con la ausencia de fila valiendo el valor por defecto: desplegar un permiso nuevo no cambia lo que puede hacer nadie hasta que se enciende a mano.
* **Control de propiedad (ownership):** un `USER` solo puede ver, modificar o eliminar sus propias citas y sus propios datos; un `PELUQUERO` llega además a las citas asignadas a su ficha, pero **no a las de un compañero**; un `ADMIN` puede acceder a todo. Los accesos no autorizados devuelven `403 Forbidden`.
* **Patrón DTO:** cada entidad tiene DTOs separados para creación, actualización parcial y respuesta. Nunca se expone información sensible.
* **Paginación y ordenación:** los listados de citas y usuarios están paginados (`page`, `size`, `sort`) y devuelven un `Page` de Spring Data.
* **Soft delete + reactivación:** usuarios, servicios y peluqueros no se eliminan físicamente, se desactivan. Los usuarios desactivados pueden listarse (`?incluirInactivos=true`) y reactivarse (`PATCH /api/usuarios/{id}/activar`).
* **Búsqueda de usuarios:** `GET /api/usuarios?search=` filtra por nombre o email (contains, case-insensitive) en la BD, combinable con `incluirInactivos` y la paginación.
* **Manejo global de excepciones:** `@RestControllerAdvice` con handlers específicos para validación (400), no encontrado (404), acceso denegado (403), conflictos (409) y un handler genérico (500) que no expone detalles internos. Incluye logging con SLF4J.
* **Documentación OpenAPI / Swagger UI:** generada automáticamente con springdoc-openapi, disponible en `/swagger-ui.html` y `/v3/api-docs`.
* **Perfiles de configuración:** entornos `dev` y `prod` separados. El esquema se gestiona con **migraciones Flyway** (`src/main/resources/db/migration/`). Con el perfil `prod` la aplicación **se niega a arrancar** sin credenciales de almacén en vez de caer al disco local: en un contenedor efímero ese fallo es silencioso —las subidas funcionan y desaparecen en el siguiente despliegue—.
* **Observabilidad (Actuator + Prometheus + Grafana):** `/actuator/prometheus` publica métricas de JVM, HTTP, pool de conexiones y **de negocio**: citas por estado y por servicio, pagos, altas, recordatorios, intentos de recuperación de contraseña y consumo de tokens del asistente. Los contadores de negocio se alimentan de los **eventos de dominio que ya existían para los correos**, así que no se modificó ningún service para medir, y cuentan en `AFTER_COMMIT`, porque una cita cuyo insert hizo rollback no es una cita. Tres decisiones son el diseño: solo se exponen `health` y `prometheus` y Spring Security cierra el resto con `denyAll`, porque `env`/`beans`/`configprops` volcarían la configuración entera con las claves de Stripe y de Gemini dentro; el endpoint de métricas se protege con un **token en cabecera** y no con un JWT, porque un scraper que corre cada 30 segundos no puede renovar uno que caduca; y el **indicador de correo no cuenta para el health**, porque Actuator lo activa solo por tener el starter de mail y un hipo del SMTP pondría el health global en `DOWN` — Render lee ese endpoint, así que un problema de correo reiniciaría en bucle un backend cuyas citas y pagos funcionan perfectamente.
* **Suite de tests (411 tests):** 356 tests unitarios que cubren la lógica de negocio sin Spring context ni base de datos, más 55 tests de integración con **Testcontainers** (PostgreSQL real en Docker) que cubren autenticación, reglas de ownership, estadísticas, pagos, producción y comisiones, el cierre de citas por rol y el flujo completo del webhook de Stripe con verificación de firma real.

## Estructura del proyecto

```
com.segovia.peluqueria/
├── almacen/        # Almacén de ficheros: puerto + adaptadores Supabase/disco, validación por magic bytes
├── auth/           # Login, registro, refresh tokens, reset de contraseña (con rate limit)
├── cita/           # Citas: agendado, conflictos, slots de disponibilidad, horario laboral
├── config/         # Configuración transversal (eventos asíncronos, scheduling)
├── estadistica/    # Estadísticas de negocio para el dashboard de admin (solo ADMIN)
├── exception/      # Manejo global de excepciones y excepciones compartidas
├── galeria/        # Galería de trabajos: fotos con orden manual y miniatura aparte
├── notificacion/   # Eventos de dominio, correos y scheduler del recordatorio 24h
├── pago/           # Pagos: Stripe PaymentIntents, webhooks, pagos manuales, reembolsos
├── peluquero/      # Peluqueros: ficha, cuenta vinculada, comisión y excepciones por servicio
├── permiso/        # Permisos por rol configurables desde el panel (estrechan, nunca abren)
├── produccion/     # Producción y comisión por peluquero: lo vendido, lo cobrado y lo pendiente
├── security/       # SecurityConfig, servicio y filtro JWT, CORS
├── servicio/       # Catálogo de servicios
└── usuario/        # Usuarios, roles, soft delete, búsqueda
```

Todos los módulos de negocio siguen el mismo esquema: entidad JPA, controller, service, repository y un paquete `dto/`.

## Tests

**411 tests** se ejecutan en CI en cada push (GitHub Actions).

### Tests unitarios (356)

Cubren toda la lógica de negocio sin Spring context ni base de datos (pocos segundos):

| Clase | Tests | Cobertura |
|-------|-------|-----------|
| CitaServiceTest | 67 | Agendar, horario laboral, días cerrados, conflictos, CRUD, ownership, disponibilidad, paginación, validación de peluquero, auto-confirmación al pagar, y el **cierre**: congelar precio y comisión al completar, no completar lo que no ha empezado, un cliente solo puede anular, un peluquero no reescribe un cierre hecho ni toca la agenda de otro, el PUT rechaza `COMPLETADA`, y el cliente no recibe observaciones ni comisión |
| UsuarioServiceTest | 40 | CRUD, email duplicado, hashing, soft delete, ownership, reactivar, paginación, búsqueda, subir/borrar avatar |
| PagoServiceTest | 37 | PaymentIntents, webhooks, pago manual, reembolsos, polling, concurrencia, quién puede pedir un recibo y en qué estados, y quién puede cobrar en efectivo: un peluquero necesita el permiso **y** que la cita sea de su agenda, mientras que un ADMIN no pasa por los permisos |
| CalendarioServiceTest | 17 | Días de la semana cerrados, bloquear/desbloquear fechas, fecha pasada, duplicados, días con citas vivas, rangos de días cerrados |
| AsistenteHerramientasTest | 11 | Las herramientas del asistente: delegación en los services reales, que solo viaje al modelo lo que necesita (ni URLs de imágenes ni descripciones), tope del rango de días cerrados, fecha inválida traducida a un mensaje que el modelo puede corregir, y que «hoy» salga del `Clock` y no de una suposición |
| AsistenteServiceTest | 9 | Traducción del historial al rol correcto, lectura del consumo de tokens, y detección de cuota agotada frente a cualquier otro fallo (incluida una causa cíclica, que colgaría el recorrido) |
| RateLimitFilterTest | 8 | Cupos **independientes por ruta**: quemar el del asistente no deja sin intentos la recuperación de contraseña. Además cupo por IP, `X-Forwarded-For` tras el proxy, y que no toque rutas ni métodos ajenos |
| ServicioServiceTest | 16 | CRUD, soft delete, subir/sustituir/borrar la foto de catálogo |
| GaleriaServiceTest | 17 | Galería: sube imagen y miniatura al bucket público, la clave la genera el servidor y separa tamaños, sin miniatura la respuesta cae a la grande, rechaza lo que no es imagen (también la miniatura), la foto nueva se coloca detrás de la última, y borrar quita **los dos** objetos |
| ReciboPdfGeneradorTest | 10 | Renderiza el **PDF de verdad** con la plantilla de producción y le vuelve a extraer el texto con PDFBox: datos del pago y de la cita, aviso de reembolso, la advertencia de que no es una factura, formato de decimales fijo |
| ValidadorImagenTest | 10 | Validación por magic bytes: JPEG/PNG/WebP reales, `Content-Type` que miente, extensión que miente, fichero vacío y fuera de tamaño, clave generada por el servidor |
| JwtServiceTest | 9 | Generar/extraer/validar tokens, firmas, tokenVersion |
| AuthControllerTest | 8 | Login, registro, credenciales inválidas |
| RefreshTokenServiceTest | 8 | Rotación, revocación, expiración |
| JwtAuthenticationFilterTest | 7 | Filtro con/sin token, token inválido/expirado, cuenta desactivada, tokenVersion |
| PasswordResetServiceTest | 7 | Solicitud, restablecimiento, expiración, anti-enumeración |
| PermisoServiceTest | 12 | Permisos por rol: un ADMIN los tiene todos sin mirar la tabla, sin fila guardada vale el valor por defecto del enum, una fila encendida para un rol al que el permiso **no** se le configura no concede nada, una fila de un permiso retirado del código se ignora en vez de reventar, la tabla se lee una sola vez (caché) y escribir la tira para que apagar un permiso surta efecto sin reiniciar |
| PeluqueroServiceTest | 19 | CRUD y soft delete, vínculo con la cuenta (rechaza un `USER`, una cuenta desactivada y una ya vinculada a otra ficha), desvincular, y la comisión aplicable: la excepción por servicio gana al porcentaje de la ficha |
| ProduccionServiceTest | 9 | Mapeo del resumen, los desgloses y lo pendiente de cobro, cuenta sin ficha vinculada, rango invertido y de más de dos años, y el redondeo a dos decimales de lo que devuelve Postgres |
| SupabaseStorageAlmacenTest | 7 | Llamadas REST al almacén con `MockRestServiceServer`: subir, borrar, firmar URL, y que las claves con carpeta no se escapan |
| MetricasNegocioListenerTest | 6 | Contadores de negocio: una métrica por concepto con `estado`/`servicio` como etiquetas, un servicio sin nombre que cae en un valor por defecto en vez de reventar, y que **ninguna métrica lleva el nombre ni el correo de un cliente** — sería un dato personal fuera de sitio y una serie temporal nueva por persona |
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

### Tests de integración (55, Testcontainers)

Arrancan la aplicación completa contra un **PostgreSQL real** levantado en Docker (`@ServiceConnection`), con las migraciones Flyway aplicadas:

* **EstadisticasIntegrationTest** (5) — estadísticas sobre datos reales: rango por defecto de 30 días, ingresos por método de pago, reembolsos excluidos.
* **WebhookIntegrationTest** (3) — webhook de Stripe end-to-end: un evento `payment_intent.succeeded` firmado se verifica con la **comprobación de firma real del SDK de Stripe**, el pago pasa a `PAGADO` y la cita se confirma; los eventos duplicados se procesan una sola vez (idempotencia); las firmas inválidas reciben 400.
* **PagosIntegrationTest** (10) — listado de pagos del panel (paginación, filtros de rango y estado, solo ADMIN) y el recibo en PDF por HTTP: el dueño recibe un PDF de verdad como adjunto, un ADMIN el de cualquiera, otro cliente 403 y un pago no cobrado 409.
* **GaleriaIntegrationTest** (2) — la asimetría de la galería por HTTP: el listado se lee **sin token**, y subir, ordenar y borrar responden 403 sin token y con un cliente, y funcionan con ADMIN. El segundo comprueba lo que no se puede ver en un test unitario del service: que una foto subida sin miniatura devuelve la grande en su lugar, y que un ejecutable renombrado a `.jpg` se queda en 400 tras pasar por el multipart de verdad.
* **OwnershipIntegrationTest** (2) — un usuario no puede leer (GET) ni editar (PUT) la cita de otro (403); `/api/usuarios/me` nunca expone la contraseña.
* **AsistenteApagadoIntegrationTest** (3) — con el asistente apagado (el valor por defecto) la aplicación **arranca igual** y su ruta responde **404 y no 500**, que es lo que permite al cliente distinguir «no está desplegado» de «ha fallado». El tercero fija el contrapunto: una ruta inexistente que **no** es pública responde 403, porque Spring Security corta antes de llegar al dispatcher y no confirma a un anónimo qué rutas existen.
* **MetricasIntegrationTest** (6) — quién puede leer qué de Actuator: `prometheus` responde 403 sin token, 403 con un token equivocado y 200 con el bueno; `env`, `beans`, `configprops` y `loggers` siguen cerrados **incluso con el token válido**; `health` es público, no cuenta nada de dentro y **aguanta un SMTP roto** (este cazó un fallo de verdad: el indicador de correo ponía el health en `DOWN`, y eso habría hecho que Render reiniciara el backend en bucle). El sexto publica un evento de dominio y encuentra `peluqueria_citas_total` en el scrape real, que es el único sitio donde se comprueban juntos el nombre de la métrica en el código y el que consulta el dashboard.
* **ProduccionIntegrationTest** (7) — producción y comisión contra Postgres de verdad, que es el único sitio donde se comprueba el SQL nativo (`DATE_TRUNC`, `TO_CHAR` y cuatro JOIN): suma solo lo completado **y cobrado** y deja fuera lo no asistido, lo anulado, lo de otro peluquero y lo completado sin cobrar (que sale como pendiente); el desglose por servicio y el mensual; **el importe congelado manda** —se sube la tarifa después de liquidar y la producción no se mueve—; la comparativa de la plantilla ordenada por importe; y quién no llega: un peluquero no ve la producción de otro ni la comparativa, un cliente no ve ni la suya, y una cuenta con el rol y sin ficha recibe 404. El séptimo fija el caso del dueño que además corta pelo: una cuenta **ADMIN vinculada a una ficha** ve su propia producción por `/produccion/mia` sin ningún sub-rol, porque el rol dice qué puede hacer y la ficha dice quién hace el trabajo.
* **CierreCitaIntegrationTest** (7) — el cierre por HTTP y lo que el rol `PELUQUERO` puede tocar. Va por HTTP a propósito: las reglas de quién llega a qué cita **no** están en `SecurityConfig` (`/api/citas/**` es «cualquiera autenticado») sino en el service, porque dependen de la ficha vinculada. Cierra su cita y se congela el importe; no puede cerrar ni ver la de un compañero (403); el cliente anula la suya pero no la da por realizada, y no recibe las notas internas; `COMPLETADA` por el PUT de siempre es 400; un cierre ya hecho solo lo corrige el ADMIN, y al corregirlo deja de sumar; el listado del peluquero es su agenda y no la de la casa; y `usuarios`, `estadisticas`, `pagos` y la gestión de peluqueros le siguen respondiendo 403.
* **PermisoIntegrationTest** (9) — los permisos configurables de punta a punta, que es donde se ve el reparto entre las dos capas: `SecurityConfig` deja llegar al service a quien *podría* tener el permiso y el service decide si de verdad lo tiene. Con el permiso apagado un peluquero recibe 403 al cobrar y el ADMIN cobra igual; encendido cobra la suya pero **no la de un compañero**; a un cliente no le sirve de nada que esté encendido, porque le para la regla de rol antes; apagarlo se lo quita otra vez (la caché no lo vuelve irrevocable); cada uno consulta los suyos y solo el ADMIN ve la matriz, que no ofrece casillas para ADMIN ni para cliente; y escribir una clave inexistente o un rol que no se configura responde 400 en vez de dejar una fila que nadie leería.
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

### Galería de trabajos
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/galeria` | Público | Listar las fotos, ya ordenadas |
| POST | `/api/galeria` | ADMIN | Subir una foto al final de la rejilla (`multipart/form-data`: `imagen`, `miniatura` opcional, `titulo` opcional) |
| PUT | `/api/galeria/{id}` | ADMIN | Cambiar el título o la posición |
| DELETE | `/api/galeria/{id}` | ADMIN | Borrar la foto y sus dos objetos del almacén |

> La miniatura es opcional en el endpoint y obligatoria en la práctica: sin ella el listado devuelve la imagen grande en `urlMiniatura`, que funciona pero multiplica el tráfico de la rejilla. Los clientes del repo la generan siempre.

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
| GET | `/api/citas` | USER/PELUQUERO/ADMIN | Listar citas (paginado). Un USER solo ve las suyas, un PELUQUERO su agenda, un ADMIN todas |
| GET | `/api/citas/disponibilidad` | USER/ADMIN | Slots libres para `?fecha=YYYY-MM-DD&idServicio=N`. Opcional `&peluqueroId=N` para un peluquero concreto. Vacío en los días cerrados |
| GET | `/api/citas/dias-cerrados` | USER/ADMIN | Días cerrados (días de la semana cerrados + fechas bloqueadas) con su motivo. Opcional `?desde=&hasta=`; por defecto los 3 próximos meses, con tope de rango de 12 meses |
| GET | `/api/citas/{id}` | Propio/ADMIN | Obtener cita por ID |
| POST | `/api/citas` | USER/ADMIN | Agendar cita (un USER solo para sí mismo), opcionalmente con peluquero |
| PUT | `/api/citas/{id}` | Propio/ADMIN | Actualizar cita |
| PATCH | `/api/citas/{id}/cierre` | Propio/PELUQUERO/ADMIN | Cerrar la cita: `estado` (`COMPLETADA`, `NO_ASISTIO` o `ANULADA`), `observaciones` y `clienteContactado`. Al completar congela el importe y la comisión; un USER solo puede `ANULADA`; un cierre ya hecho solo lo corrige un ADMIN |
| DELETE | `/api/citas/{id}` | Propio/ADMIN | Eliminar cita |

> `COMPLETADA` y `NO_ASISTIO` **no entran por el PUT** (responde 400): cerrar congela el importe y la comisión, y por el otro camino quedarían citas completadas sin precio congelado, que es justo el agujero que la producción no puede tener. Anular por el PUT sigue valiendo y deja el mismo rastro de quién y cuándo.

### Peluqueros
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/peluqueros` | Autenticado | Listar peluqueros activos |
| GET | `/api/peluqueros/{id}` | Autenticado | Obtener peluquero por ID |
| POST | `/api/peluqueros` | ADMIN | Crear peluquero |
| GET | `/api/peluqueros/gestion` | ADMIN | Fichas completas (activas e **inactivas**) con comisión y cuenta vinculada |
| PUT | `/api/peluqueros/{id}` | ADMIN | Actualizar peluquero: `nombre`, `activo`, `comisionPorcentaje`, `usuarioId` para vincular la cuenta o `desvincularUsuario` |
| GET | `/api/peluqueros/{id}/comisiones` | ADMIN | Excepciones de comisión por servicio |
| PUT | `/api/peluqueros/{id}/comisiones` | ADMIN | Reemplazar el conjunto entero de excepciones (lo que no se manda se borra) |
| DELETE | `/api/peluqueros/{id}` | ADMIN | Desactivar peluquero (soft delete) |

> La comisión y la cuenta vinculada **no** viajan en `PeluqueroResponseDTO`, que va anidado en cada cita y lo leen los clientes: lo que cobra un profesional sale solo por los endpoints de ADMIN. Vincular una cuenta con rol `USER` responde 400 en vez de cambiarle el rol por la espalda: sin el rol, el dueño de esa ficha no vería ni una cita.

### Producción y comisión
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/produccion/mia` | PELUQUERO/ADMIN | Producción del peluquero **de la cuenta autenticada** (el id no se pasa por parámetro, se resuelve desde la cuenta). `?desde=&hasta=`; por defecto el mes en curso, con tope de 24 meses |
| GET | `/api/produccion/peluquero/{id}` | ADMIN | La de cualquiera |
| GET | `/api/produccion` | ADMIN | Comparativa de toda la plantilla, ordenada por importe |

> Devuelve servicios realizados, importe vendido y comisión, más el desglose por servicio y por mes, y aparte `serviciosSinCobrar`/`importeSinCobrar`. Solo suma lo `COMPLETADA` **con el pago en `PAGADO`**; el efectivo entra registrando el pago manual, que es de ADMIN salvo que se le conceda a un peluquero por permiso.

### Permisos por rol
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/permisos` | ADMIN | Matriz rol × permiso, con la descripción de cada uno |
| PUT | `/api/permisos` | ADMIN | Aplicar cambios (`cambios[]` con `rol`, `clave` y `habilitado`). Se mandan solo las casillas que cambian, así dos administradores en pantallas distintas no se pisan |
| GET | `/api/permisos/mios` | Autenticado | Lo concedido a la cuenta que pregunta, para que el frontend no ofrezca acciones que acabarían en 403 |

> **Un permiso estrecha, nunca abre.** Se consultan desde los services y no desde `SecurityConfig`, así que el orden es siempre el mismo: primero la regla de rol de la ruta, después el permiso. Encender uno no concede nada que el rol ya prohibiera, y un ADMIN los tiene todos por rol y no aparece en la matriz. El catálogo lo define el enum `Permiso` del código y la tabla solo guarda el estado: la ausencia de fila significa «el valor por defecto», así que desplegar un permiso nuevo no cambia lo que puede hacer nadie hasta que se enciende a mano.

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
| POST | `/api/pagos/manual` | PELUQUERO*/ADMIN | Registrar pago en efectivo o transferencia. Un peluquero necesita el permiso `PAGO_MANUAL_REGISTRAR` y solo puede cobrar citas de su agenda |
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

### Observabilidad
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/actuator/health` | Público | Señal de vida para el health check de Render. Sin detalles, sin componentes, y el indicador de correo no cuenta para él |
| GET | `/actuator/prometheus` | Token | Métricas en formato Prometheus. Exige la cabecera `X-Metrics-Token` con el valor de `METRICAS_TOKEN`; sin la variable puesta, el endpoint está cerrado para todos |
| — | `/actuator/**` | Denegado | Todo lo demás es `denyAll`, incluidos `env` y `configprops` |

### Documentación (público)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Interfaz Swagger UI interactiva |
| GET | `/v3/api-docs` | Especificación OpenAPI (JSON) |

## Modelo de datos

El esquema se gestiona con **Flyway** (migraciones `V1` a `V11` en `src/main/resources/db/migration/`):

* **`usuarios`** — clientes y administradores: nombre, email único, teléfono, contraseña hasheada, rol, flag de activo, `token_version` y `avatar_clave`.
* **`servicios`** — catálogo de la peluquería (cortes, tintes...): descripción, duración en minutos, precio, flag de activo e `imagen_clave`.
* **`peluqueros`** — peluqueros/estilistas, con soft delete. Una cita puede asignarse opcionalmente a uno.
* **`citas`** — vincula un `usuario` con un `servicio` (y opcionalmente un `peluquero`) en una fecha/hora concreta. Enum de estado (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) y flag `recordatorio_enviado` para el recordatorio 24h.
* **`galeria_fotos`** — fotos de la galería de trabajos: `imagen_clave`, `miniatura_clave` (opcional), título, `orden` manual y fecha de subida.
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
    * `GEMINI_MODEL` *(opcional)*: id del modelo, por defecto `gemini-3.6-flash`. Conviene saber que existe, porque Google retira ids más rápido de lo que los documenta: `gemini-2.5-flash` responde **404 `no longer available to new users`** a una clave recién creada mientras la página de deprecaciones lo sigue dando por activo y sin fecha de cierre. El síntoma es un 503 en `/api/asistente`, y esta variable lo arregla sin volver a desplegar.
    * `METRICAS_TOKEN` *(opcional)*: token que exige `/actuator/prometheus` en la cabecera `X-Metrics-Token`. Sin él el endpoint queda cerrado para todos, que es el fallo que se quiere: olvidar la variable esconde las métricas en vez de publicarlas.

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
* **Almacén de imágenes:** Supabase Storage, con tres buckets que hay que crear — **`servicios`** y **`galeria`** (lectura pública) y **`avatares`** (privado, se lee con URL firmada). Los binarios nunca viven junto a la aplicación: el disco de Render es efímero y cada push a `main` lo borra.
* **Email:** los correos transaccionales salen por el relay SMTP de un proveedor en el puerto **2525** (el tier Free de Render bloquea los puertos SMTP salientes 25/465/587).
* **Frontends:** Firebase Hosting (ver abajo).
* **CI:** GitHub Actions ejecuta la suite completa — tests unitarios + integración con Testcontainers — en cada push y pull request.

## Observabilidad

Las métricas viven en un **Prometheus + Grafana local** que scrapea producción. No están desplegados en Render: su tier gratuito ya está al límite de sus 750 h/mes con el backend y el cronjob que lo mantiene despierto. La contrapartida es que Prometheus solo recoge datos mientras el stack está levantado, así que las gráficas tienen agujeros con la forma de las horas que el portátil estuvo apagado.

```
Render (producción)                     Tu máquina
┌──────────────────────┐  scrape +    ┌──────────────┐     ┌─────────┐
│ /actuator/prometheus │◀─── token ───│  Prometheus  │────▶│ Grafana │
└──────────────────────┘    30s       └──────────────┘     └─────────┘
```

```bash
# 1. En Render, poner METRICAS_TOKEN con un valor largo y aleatorio
# 2. El mismo valor aquí (sin salto de línea: la cabecera tiene que coincidir exacta)
echo -n 'EL_TOKEN' > observabilidad/prometheus/token
# 3. Levantar
cd observabilidad && docker compose up -d
# 4. http://localhost:3000 — usuario admin, contraseña admin
```

El dashboard (`observabilidad/grafana/dashboards/peluqueria.json`) se **provisiona desde el repositorio**, no vive en un volumen de Docker: sobrevive a un `docker compose down -v` y se puede revisar en un diff. El datasource también, así que no hay nada que configurar a mano después de arrancar.

Para mirarlo contra tu propio backend en vez de contra producción — útil porque en local las métricas de negocio se pueden provocar a voluntad y en producción hay que esperar a que un cliente lo haga:

```bash
cd observabilidad && docker compose -f compose.yaml -f compose.local.yaml up -d
```

Dos cosas que conviene saber antes de leer los paneles:

* **Un contador no existe hasta que ocurre.** Micrometer solo publica un contador después de su primer incremento, y eso es lo que evita que el endpoint sirva cientos de series a cero. Un panel de negocio vacío significa que todavía no ha pasado nada, no que esté roto.
* **Todos los paneles se comprobaron consulta por consulta contra producción.** Dos hubo que corregirlos al ver la salida real, porque los nombres de métrica de Spring AI no son los que sugiere su documentación: el medidor de tokens es un **contador** (`gen_ai_client_token_usage_total`, no un `_sum`) y el timer es `gen_ai_client_operation_seconds` **sin buckets**, y por eso el asistente no tiene percentiles y muestra media y peor caso.
* **Una pregunta de cliente son varias llamadas al modelo, no una.** Un solo «¿cuánto cuesta un corte?» dejó `gen_ai_client_operation_seconds_count = 4`: el modelo pide una herramienta, recibe el resultado y vuelve a llamar. Importa para la cuota, porque el tier gratuito limita *peticiones*: ~1500/día son unas **375 preguntas de cliente**, no 1500. Para eso está la serie «llamadas al modelo por hora».

## Frontend

El proyecto incluye dos aplicaciones frontend en un repositorio separado:

* **Panel de administración** ([Angular 21](https://angular.dev) zoneless + Tailwind v4), con gestión de citas/usuarios/servicios/peluqueros, pagos y dashboard de estadísticas.
* **App móvil para clientes** ([Ionic 8](https://ionicframework.com) + Angular + Capacitor), con reserva de citas, selección de peluquero, pago con Stripe y login biométrico.

Ambos viven en el monorepo [peluqueria_citas_frontend](https://github.com/eduardoandr3s/peluqueria_citas_frontend) (npm workspaces), compartiendo modelos y servicios en la librería `packages/core`.

---
*Desarrollado por Eduardo Andrés Segovia Román.*
