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
* **Spring Boot 4.0.3** (framework principal)
* **PostgreSQL** (base de datos relacional)
* **Flyway** (migraciones de esquema, V1-V7)
* **Spring Data JPA / Hibernate** (ORM)
* **Spring Security + JWT** (autenticación stateless y autorización por roles)
* **BCrypt** (hash unidireccional de contraseñas)
* **Stripe API** (pagos online: PaymentIntents, webhooks, reembolsos)
* **Spring Mail** (correos transaccionales y recordatorios de cita)
* **Bucket4j** (rate limiting)
* **springdoc-openapi** (documentación Swagger UI)
* **Maven** · **Lombok** · **Docker Compose** (entorno de desarrollo local)
* **JUnit 5 + Mockito** (tests unitarios) · **Testcontainers** (tests de integración contra un PostgreSQL real)
* **GitHub Actions** (CI: suite completa en cada push y pull request)

## Características

* **Arquitectura por dominio:** el código se organiza por módulo de negocio (`usuario/`, `cita/`, `servicio/`, `pago/`, `peluquero/`, `estadistica/`, `notificacion/`, `auth/`, `security/`). Cada módulo agrupa su entidad, controller, service, repository y DTOs.
* **Constructor injection:** inyección de dependencias vía constructor con campos `final` (sin `@Autowired`), siguiendo las buenas prácticas de Spring para inmutabilidad y testabilidad.
* **Autenticación JWT con roles:** login/registro con access tokens JWT (30 min) más **refresh tokens con rotación** (30 días). Dos roles: `USER` (clientes) y `ADMIN`. En cada request se valida además que la cuenta siga activa y que el `tokenVersion` del token coincida con el de la BD: cambiar la contraseña o el rol **revoca** los tokens emitidos antes (el rol y el estado activo se leen siempre de la BD, nunca del token).
* **Recuperación de contraseña:** tokens de un solo uso enviados por correo, con expiración y **rate limiting por IP** (Bucket4j). El endpoint responde siempre 200 para evitar enumeración de usuarios.
* **Pagos online con Stripe:** creación de PaymentIntents, **webhooks firmados** (firma verificada con el SDK oficial), procesamiento idempotente de eventos, pagos manuales (efectivo/transferencia) y reembolsos. Un pago confirmado auto-confirma la cita.
* **Multi-peluquero:** CRUD de peluqueros y **peluquero opcional** por cita. Los conflictos de horario se comprueban por peluquero ("sin asignar" bloquea el hueco completo) y la disponibilidad se puede consultar para un peluquero concreto.
* **Endpoint de disponibilidad:** `/api/citas/disponibilidad` calcula los huecos libres de 30 minutos para un servicio en una fecha — opcionalmente para un peluquero concreto — descontando citas existentes y respetando el horario laboral.
* **Validación de conflictos de horario:** se rechazan citas que se solapen, usando la duración del servicio para calcular cada rango.
* **Validación de horario laboral:** las citas solo se pueden agendar de lunes a sábado de 9:00 a 20:00, y nunca en el pasado. El horario es **configurable** vía properties (`peluqueria.horario.apertura` / `peluqueria.horario.cierre`).
* **Estadísticas de negocio:** `GET /api/estadisticas` (solo ADMIN) devuelve citas por estado, ingresos desglosados por método de pago (excluyendo reembolsos, calculados por fecha de pago), servicios más demandados y clientes nuevos. Por defecto usa los **últimos 30 días** si no se indica rango.
* **Notificaciones por correo:** emails dirigidos por eventos (registro, cita agendada, modificada, anulada, pago confirmado, cambios de contraseña) desacoplados de la lógica de negocio mediante eventos de Spring (`@TransactionalEventListener(AFTER_COMMIT)`), más un **recordatorio de cita 24h antes** enviado por un scheduler (corre cada 15 minutos, `Clock` inyectable para testabilidad, el flag `recordatorio_enviado` garantiza un único envío).
* **Control de propiedad (ownership):** un `USER` solo puede ver, modificar o eliminar sus propias citas y sus propios datos; un `ADMIN` puede acceder a todo. Los accesos no autorizados devuelven `403 Forbidden`.
* **Patrón DTO:** cada entidad tiene DTOs separados para creación, actualización parcial y respuesta. Nunca se expone información sensible.
* **Paginación y ordenación:** los listados de citas y usuarios están paginados (`page`, `size`, `sort`) y devuelven un `Page` de Spring Data.
* **Soft delete + reactivación:** usuarios, servicios y peluqueros no se eliminan físicamente, se desactivan. Los usuarios desactivados pueden listarse (`?incluirInactivos=true`) y reactivarse (`PATCH /api/usuarios/{id}/activar`).
* **Búsqueda de usuarios:** `GET /api/usuarios?search=` filtra por nombre o email (contains, case-insensitive) en la BD, combinable con `incluirInactivos` y la paginación.
* **Manejo global de excepciones:** `@RestControllerAdvice` con handlers específicos para validación (400), no encontrado (404), acceso denegado (403), conflictos (409) y un handler genérico (500) que no expone detalles internos. Incluye logging con SLF4J.
* **Documentación OpenAPI / Swagger UI:** generada automáticamente con springdoc-openapi, disponible en `/swagger-ui.html` y `/v3/api-docs`.
* **Perfiles de configuración:** entornos `dev` y `prod` separados. El esquema se gestiona con **migraciones Flyway** (`src/main/resources/db/migration/`).
* **Suite de tests (167 tests):** 157 tests unitarios que cubren la lógica de negocio sin Spring context ni base de datos, más 10 tests de integración con **Testcontainers** (PostgreSQL real en Docker) que cubren autenticación, reglas de ownership, estadísticas y el flujo completo del webhook de Stripe con verificación de firma real.

## Estructura del proyecto

```
com.segovia.peluqueria/
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

**167 tests** se ejecutan en CI en cada push (GitHub Actions).

### Tests unitarios (157)

Cubren toda la lógica de negocio sin Spring context ni base de datos (pocos segundos):

| Clase | Tests | Cobertura |
|-------|-------|-----------|
| CitaServiceTest | 40 | Agendar, horario laboral, conflictos, CRUD, ownership, disponibilidad, paginación, validación de peluquero, auto-confirmación al pagar |
| UsuarioServiceTest | 26 | CRUD, email duplicado, hashing, soft delete, ownership, reactivar, paginación, búsqueda |
| PagoServiceTest | 23 | PaymentIntents, webhooks, pago manual, reembolsos, polling, concurrencia |
| JwtServiceTest | 9 | Generar/extraer/validar tokens, firmas, tokenVersion |
| ServicioServiceTest | 9 | CRUD, soft delete |
| AuthControllerTest | 8 | Login, registro, credenciales inválidas |
| RefreshTokenServiceTest | 8 | Rotación, revocación, expiración |
| JwtAuthenticationFilterTest | 7 | Filtro con/sin token, token inválido/expirado, cuenta desactivada, tokenVersion |
| PasswordResetServiceTest | 7 | Solicitud, restablecimiento, expiración, anti-enumeración |
| PeluqueroServiceTest | 7 | CRUD de peluqueros, soft delete |
| RecordatorioCitaSchedulerTest | 5 | Recordatorio 24h: envío único, ignora anuladas/ya notificadas, Clock inyectable |
| CustomUserDetailsServiceTest | 4 | Carga de usuario, roles, estado |
| EstadisticasServiceTest | 3 | Agregaciones, desglose de ingresos, exclusión de reembolsos |

```bash
# Solo tests unitarios (no requiere Docker)
./mvnw test -Dtest='!*IntegrationTest'
```

### Tests de integración (10, Testcontainers)

Arrancan la aplicación completa contra un **PostgreSQL real** levantado en Docker (`@ServiceConnection`), con las migraciones Flyway aplicadas:

* **AuthIntegrationTest** — flujo completo de registro/login por HTTP.
* **OwnershipIntegrationTest** — un usuario no puede leer (GET) ni editar (PUT) la cita de otro (403); `/api/usuarios/me` nunca expone la contraseña.
* **WebhookIntegrationTest** — webhook de Stripe end-to-end: un evento `payment_intent.succeeded` firmado se verifica con la **comprobación de firma real del SDK de Stripe**, el pago pasa a `PAGADO` y la cita se confirma; los eventos duplicados se procesan una sola vez (idempotencia); las firmas inválidas reciben 400.
* **EstadisticasIntegrationTest** — estadísticas sobre datos reales: rango por defecto de 30 días, ingresos por método de pago, reembolsos excluidos.

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

### Citas
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/citas` | USER/ADMIN | Listar citas (paginado). Un USER solo ve las suyas, un ADMIN todas |
| GET | `/api/citas/disponibilidad` | USER/ADMIN | Slots libres para `?fecha=YYYY-MM-DD&idServicio=N`. Opcional `&peluqueroId=N` para un peluquero concreto |
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

### Pagos
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| POST | `/api/pagos/crear-intent` | USER/ADMIN | Crear Stripe PaymentIntent para una cita |
| POST | `/api/pagos/webhook` | Público | Webhook de Stripe (firma verificada, idempotente) |
| POST | `/api/pagos/manual` | ADMIN | Registrar pago en efectivo o transferencia |
| POST | `/api/pagos/{citaId}/reembolsar` | ADMIN | Reembolsar un pago (Stripe o manual) |
| GET | `/api/pagos/cita/{citaId}` | Propio/ADMIN | Consultar el pago de una cita |

### Estadísticas
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/estadisticas` | ADMIN | Citas por estado, ingresos por método de pago, top servicios y clientes nuevos. `?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` opcional; por defecto los últimos 30 días |

### Documentación (público)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Interfaz Swagger UI interactiva |
| GET | `/v3/api-docs` | Especificación OpenAPI (JSON) |

## Modelo de datos

El esquema se gestiona con **Flyway** (migraciones `V1` a `V7` en `src/main/resources/db/migration/`):

* **`usuarios`** — clientes y administradores: nombre, email único, teléfono, contraseña hasheada, rol, flag de activo y `token_version`.
* **`servicios`** — catálogo de la peluquería (cortes, tintes...): descripción, duración en minutos, precio, flag de activo.
* **`peluqueros`** — peluqueros/estilistas, con soft delete. Una cita puede asignarse opcionalmente a uno.
* **`citas`** — vincula un `usuario` con un `servicio` (y opcionalmente un `peluquero`) en una fecha/hora concreta. Enum de estado (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) y flag `recordatorio_enviado` para el recordatorio 24h.
* **`pagos`** — pagos vinculados 1:1 a una cita. Tarjeta (Stripe), efectivo o transferencia. Estados: `PENDIENTE`, `PAGADO`, `REEMBOLSADO`, `CANCELADO`.
* **`stripe_evento`** — IDs de eventos de Stripe ya procesados, garantiza la idempotencia del webhook.
* **`password_reset_token`** — tokens de un solo uso para restablecer contraseña, con expiración.
* **`refresh_token`** — refresh tokens persistentes para rotación de sesión.

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
* **Frontends:** Firebase Hosting (ver abajo).
* **CI:** GitHub Actions ejecuta la suite completa — tests unitarios + integración con Testcontainers — en cada push y pull request.

## Frontend

El proyecto incluye dos aplicaciones frontend en un repositorio separado:

* **Panel de administración** ([Angular 21](https://angular.dev) zoneless + Tailwind v4), con gestión de citas/usuarios/servicios/peluqueros, pagos y dashboard de estadísticas.
* **App móvil para clientes** ([Ionic 8](https://ionicframework.com) + Angular + Capacitor), con reserva de citas, selección de peluquero, pago con Stripe y login biométrico.

Ambos viven en el monorepo [peluqueria_citas_frontend](https://github.com/eduardoandr3s/peluqueria_citas_frontend) (npm workspaces), compartiendo modelos y servicios en la librería `packages/core`.

---
*Desarrollado por Eduardo Andrés Segovia Román.*
