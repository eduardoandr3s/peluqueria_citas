# "Lalo Segovia" Hair Salon — Booking REST API

[![CI](https://github.com/eduardoandr3s/peluqueria_citas/actions/workflows/ci.yml/badge.svg)](https://github.com/eduardoandr3s/peluqueria_citas/actions/workflows/ci.yml)

🇬🇧 English | [🇪🇸 Español](README.es.md)

Backend for a complete appointment booking and management system for a hair salon. It is a REST API that handles service bookings, schedules, online payments and customer management, with separate flows for customers and administrators.

## Live Demo

| App | URL |
|-----|-----|
| Admin panel | https://peluqueria-citas-prod.web.app |
| Customer app (web build) | https://peluqueria-citas-app.web.app |
| API + Swagger UI | https://peluqueria-citas-zbxb.onrender.com/swagger-ui.html |

> The API runs on Render's free tier and **sleeps after 15 minutes of inactivity**: the first request may take ~30-60 seconds. Stripe runs in **test mode** — use card `4242 4242 4242 4242` with any future date and CVC.

## Tech Stack

* **Java 21 (Temurin LTS)**
* **Spring Boot 4.0.3** (main framework)
* **PostgreSQL** (relational database)
* **Flyway** (database migrations, V1-V10)
* **Spring Data JPA / Hibernate** (ORM)
* **Supabase Storage** (object storage for images, over its REST API — no S3 SDK). Optional: without credentials the app falls back to local disk
* **Spring Security + JWT** (stateless authentication and role-based authorization)
* **BCrypt** (one-way password hashing)
* **Stripe API** (online payments: PaymentIntents, webhooks, refunds)
* **Spring Mail** (transactional emails and appointment reminders)
* **Bucket4j** (rate limiting)
* **springdoc-openapi** (Swagger UI documentation)
* **Maven** · **Lombok** · **Docker Compose** (local dev environment)
* **JUnit 5 + Mockito** (unit tests) · **Testcontainers** (integration tests against a real PostgreSQL)
* **GitHub Actions** (CI: full test suite on every push and pull request)

## Features

* **Domain-based architecture:** code is organized by business module (`usuario/`, `cita/`, `servicio/`, `pago/`, `peluquero/`, `calendario/`, `estadistica/`, `notificacion/`, `auth/`, `security/`). Each module contains its entity, controller, service, repository and DTOs.
* **Constructor injection:** dependencies are injected through constructors with `final` fields (no `@Autowired`), following Spring best practices for immutability and testability.
* **JWT authentication with roles:** login/registration with JWT access tokens (30 min) plus **rotating refresh tokens** (30 days). Two roles: `USER` (customers) and `ADMIN`. On every request the API also checks that the account is still active and that the token's `tokenVersion` matches the database: changing the password or the role **revokes** previously issued tokens (role and active status are always read from the database, never from the token).
* **Password reset:** one-time tokens sent by email, with expiration and **per-IP rate limiting** (Bucket4j). The endpoint always returns 200 to prevent user enumeration.
* **Online payments with Stripe:** PaymentIntent creation, **signed webhooks** (signature verified with the official SDK), idempotent event processing, manual payments (cash/transfer) and refunds. A successful payment auto-confirms the appointment.
* **Multi-barber support:** barber CRUD and an **optional barber** per appointment. Schedule conflicts are checked per barber ("unassigned" blocks the whole slot), and availability can be queried for a specific barber.
* **Availability endpoint:** `/api/citas/disponibilidad` computes free 30-minute slots for a service on a date — optionally for a specific barber — taking existing appointments and business hours into account.
* **Schedule conflict validation:** overlapping appointments are rejected, using the service duration to compute each time range.
* **Business-hours validation:** appointments can only be booked Monday to Saturday, 9:00-20:00, never in the past. Hours and closed weekdays are **configurable** via properties (`peluqueria.horario.apertura` / `peluqueria.horario.cierre` / `peluqueria.horario.dias-cerrados`).
* **Closed days (holidays and one-off closures):** an ADMIN can block a specific date with an optional reason (`/api/dias-bloqueados`). A blocked day returns no slots and rejects bookings and reschedules with the reason in the message. `GET /api/citas/dias-cerrados` returns every closed day in a range — the fixed closed weekdays (Sunday) and the blocked dates, unified — so clients can render them as **unselectable** instead of letting the customer pick a day with no available times. Blocking a day that still has live appointments is rejected (409) rather than silently cancelling them.
* **Image upload (catalog photos and avatars):** a single storage port (`AlmacenFicheros`) with two implementations — **Supabase Storage** over its REST API and local disk — so the project runs with no cloud account. Uploads are validated by **magic bytes** (the first bytes of the file), never by `Content-Type` or filename, since both are set by whoever uploads; the object key is generated server-side with a UUID, so a name like `../../etc/passwd.jpg` never reaches the store. Replacing a photo **deletes the previous object** instead of leaving orphans behind. The service catalog uses a **public** bucket and avatars a **private** one read through **short-lived signed URLs**, because a profile photo is personal data. Limits: 2 MB per file (**413** if the request exceeds it, **400** if the content is not a valid JPEG/PNG/WebP) and **502** if the store does not respond.
* **The database stores the object key, not the URL** (`servicios.imagen_clave`, `usuarios.avatar_clave`). The URL is built on read, which is why switching bucket or provider — or turning a bucket private, as avatars did — is a configuration change and not a data migration.
* **Business statistics:** `GET /api/estadisticas` (ADMIN only) returns appointments by status, revenue broken down by payment method (refunds excluded, computed by payment date), top services and new customers. Defaults to the **last 30 days** when no date range is given.
* **Email notifications:** event-driven emails (registration, booking, modification, cancellation, payment confirmation, password changes) decoupled from business logic via Spring application events (`@TransactionalEventListener(AFTER_COMMIT)`), plus a **24-hour appointment reminder** sent by a scheduler (runs hourly, injectable `Clock` for testability, `recordatorio_enviado` flag guarantees a single send).
* **Ownership control:** a `USER` can only see, modify or delete their own appointments and data; an `ADMIN` can access everything. Unauthorized access returns `403 Forbidden`.
* **DTO pattern:** every entity has separate DTOs for creation, partial update and response. Sensitive data is never exposed.
* **Pagination and sorting:** appointment and user listings are paginated (`page`, `size`, `sort`) and return a Spring Data `Page`.
* **Soft delete + reactivation:** users, services and barbers are never physically deleted, only deactivated. Deactivated users can be listed (`?incluirInactivos=true`) and reactivated (`PATCH /api/usuarios/{id}/activar`).
* **User search:** `GET /api/usuarios?search=` filters by name or email (contains, case-insensitive) in the database, combinable with `incluirInactivos` and pagination.
* **Global exception handling:** `@RestControllerAdvice` with specific handlers for validation (400), not found (404), access denied (403), conflicts (409) and a generic handler (500) that never leaks internal details. Includes SLF4J logging.
* **OpenAPI / Swagger UI documentation:** auto-generated with springdoc-openapi, available at `/swagger-ui.html` and `/v3/api-docs`.
* **Configuration profiles:** separate `dev` and `prod` environments. Schema is managed with **Flyway migrations** (`src/main/resources/db/migration/`). Under the `prod` profile the app **refuses to start** without storage credentials rather than falling back to local disk: on an ephemeral container that failure is silent — uploads succeed and vanish on the next deploy.
* **Test suite (245 tests):** 229 unit tests covering the business logic without Spring context or database, plus 16 integration tests with **Testcontainers** (real PostgreSQL in Docker) covering authentication, ownership rules, statistics, payments and the full Stripe webhook flow with real signature verification.

## Project Structure

```
com.segovia.peluqueria/
├── almacen/        # File storage: port + Supabase/disk adapters, magic-byte validation
├── auth/           # Login, registration, refresh tokens, password reset (rate-limited)
├── cita/           # Appointments: booking, conflicts, availability slots, business hours
├── config/         # Cross-cutting config (async events, scheduling)
├── estadistica/    # Business statistics for the admin dashboard (ADMIN only)
├── exception/      # Global exception handling and shared exceptions
├── notificacion/   # Domain events, email notifications and the 24h reminder scheduler
├── pago/           # Payments: Stripe PaymentIntents, webhooks, manual payments, refunds
├── peluquero/      # Barbers: CRUD and per-barber availability
├── security/       # SecurityConfig, JWT service and filter, CORS
├── servicio/       # Service catalog
└── usuario/        # Users, roles, soft delete, search
```

Each business module follows the same layout: JPA entity, controller, service, repository and a `dto/` package.

## Tests

**245 tests** run in CI on every push (GitHub Actions).

### Unit tests (229)

They cover all business logic without Spring context or database (a few seconds):

| Class | Tests | Coverage |
|-------|-------|----------|
| CitaServiceTest | 48 | Booking, business hours, closed days, conflicts, CRUD, ownership, availability, pagination, barber validation, auto-confirmation on payment |
| UsuarioServiceTest | 38 | CRUD, duplicate email, hashing, soft delete, ownership, reactivation, pagination, search, avatar upload/removal |
| PagoServiceTest | 25 | PaymentIntents, webhooks, manual payment, refunds, polling, concurrency |
| CalendarioServiceTest | 17 | Closed weekdays, blocking/unblocking dates, past dates, duplicates, days with live appointments, closed-day ranges |
| ServicioServiceTest | 16 | CRUD, soft delete, catalog photo upload/replacement/removal |
| ValidadorImagenTest | 10 | Magic-byte validation: real JPEG/PNG/WebP, lying `Content-Type`, lying extension, oversized and empty files, server-generated key |
| JwtServiceTest | 9 | Token generation/extraction/validation, signatures, tokenVersion |
| AuthControllerTest | 8 | Login, registration, invalid credentials |
| RefreshTokenServiceTest | 8 | Rotation, revocation, expiration |
| JwtAuthenticationFilterTest | 7 | Filter with/without token, invalid/expired token, deactivated account, tokenVersion |
| PasswordResetServiceTest | 7 | Request, reset, expiration, anti-enumeration |
| PeluqueroServiceTest | 7 | Barber CRUD, soft delete |
| SupabaseStorageAlmacenTest | 7 | Storage REST calls with `MockRestServiceServer`: upload, delete, URL signing, and that keys with a folder are not escaped |
| AlmacenConfigTest | 5 | Adapter selection per profile: refuses to start under `prod` without credentials, falls back to disk in `dev` |
| RecordatorioCitaSchedulerTest | 5 | 24h reminder: sends once, skips cancelled/already-notified, injectable Clock |
| CustomUserDetailsServiceTest | 4 | User loading, roles, status |
| HorarioPropertiesTest | 4 | Business-hours property binding, including the closed-weekdays list |
| EstadisticasServiceTest | 3 | Aggregations, revenue breakdown, refund exclusion |
| PeluqueriaApplicationTests | 1 | Spring context loads (only runs when `DB_USERNAME` is set) |

```bash
# Unit tests only (no Docker needed)
./mvnw test -Dtest='!*IntegrationTest'
```

### Integration tests (16, Testcontainers)

They boot the full application against a **real PostgreSQL** started in Docker (`@ServiceConnection`), with Flyway migrations applied:

* **EstadisticasIntegrationTest** (5) — statistics over real data: default 30-day range, revenue by payment method, refunds excluded.
* **WebhookIntegrationTest** (4) — end-to-end Stripe webhook: a signed `payment_intent.succeeded` event is verified with the **real Stripe SDK signature check**, the payment becomes `PAGADO` and the appointment is confirmed; duplicated events are processed only once (idempotency); invalid signatures get 400.
* **PagosIntegrationTest** (4) — payment listing for the dashboard: pagination, range and status filters, ADMIN only.
* **OwnershipIntegrationTest** (2) — a user cannot read (GET) or edit (PUT) someone else's appointment (403); `/api/usuarios/me` never exposes the password.
* **AuthIntegrationTest** (1) — full register/login flow over HTTP.

> Tests never book "tomorrow": a helper picks the **next Monday**, so a run on a Saturday cannot land on a closed day and fail for reasons unrelated to what is being tested.

```bash
# Full suite, integration tests included (requires Docker running)
./mvnw test
```

## API Endpoints

### Authentication (public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/registro` | Register a new user |
| POST | `/api/auth/login` | Log in (returns JWT + refresh token) |
| POST | `/api/auth/recuperar` | Request a password-reset link (always 200, anti-enumeration) |
| POST | `/api/auth/reset` | Reset the password with the emailed token (single use, expires) |
| POST | `/api/auth/refresh` | Rotate the refresh token (returns new access + refresh token) |
| POST | `/api/auth/logout` | Revoke the refresh token |

> Password-reset endpoints are rate-limited per IP (Bucket4j): 5 requests every 15 minutes by default; exceeding it returns 429. Configurable via `RESET_EXPIRACION_MINUTOS`, `RATELIMIT_RESET_CAPACIDAD` and `RATELIMIT_RESET_VENTANA_MINUTOS`. The email link points to `FRONTEND_URL` + `/reset?token=...`.

### Services
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/servicios` | Public | List active services |
| GET | `/api/servicios/{id}` | Public | Get a service by ID |
| POST | `/api/servicios` | ADMIN | Create a service |
| PUT | `/api/servicios/{id}` | ADMIN | Update a service |
| DELETE | `/api/servicios/{id}` | ADMIN | Deactivate a service (soft delete) |
| POST | `/api/servicios/{id}/imagen` | ADMIN | Upload or replace the catalog photo (`multipart/form-data`, field `imagen`). Returns the service with its new URL |
| DELETE | `/api/servicios/{id}/imagen` | ADMIN | Remove the catalog photo |

### Users
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/usuarios` | ADMIN | List users (paginated). `?incluirInactivos=true` includes deactivated ones. `?search=` filters by name or email |
| GET | `/api/usuarios/{id}` | Own/ADMIN | Get a user by ID |
| POST | `/api/usuarios` | ADMIN | Create a user |
| PUT | `/api/usuarios/{id}` | Own/ADMIN | Update a user |
| PATCH | `/api/usuarios/{id}/rol` | ADMIN | Change a user's role (with last-ADMIN anti-lockout guard) |
| PATCH | `/api/usuarios/{id}/activar` | ADMIN | Reactivate a deactivated user |
| DELETE | `/api/usuarios/{id}` | ADMIN | Deactivate a user (soft delete) |
| POST | `/api/usuarios/{id}/avatar` | Own/ADMIN | Upload or replace the profile photo (`multipart/form-data`, field `imagen`) |
| DELETE | `/api/usuarios/{id}/avatar` | Own/ADMIN | Remove the profile photo |

> Avatar endpoints are only `authenticated()` in `SecurityConfig`, not ADMIN-only: ownership is checked in the service, which is the layer that knows whose id it is. The signed URL is issued on `/me`, on `GET /{id}` and right after uploading — never in the user listing, so browsing users costs no signing round-trips. If the store is unreachable, those responses return the user **without a photo** and log a warning instead of failing with 502; only the upload endpoint propagates the error.

### Appointments
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/citas` | USER/ADMIN | List appointments (paginated). A USER only sees their own, an ADMIN sees all |
| GET | `/api/citas/disponibilidad` | USER/ADMIN | Free slots for `?fecha=YYYY-MM-DD&idServicio=N`. Optional `&peluqueroId=N` for a specific barber. Empty on closed days |
| GET | `/api/citas/dias-cerrados` | USER/ADMIN | Closed days (closed weekdays + blocked dates) with their reason. Optional `?desde=&hasta=`; defaults to the next 3 months, 12-month range cap |
| GET | `/api/citas/{id}` | Own/ADMIN | Get an appointment by ID |
| POST | `/api/citas` | USER/ADMIN | Book an appointment (a USER only for themselves), optionally with a barber |
| PUT | `/api/citas/{id}` | Own/ADMIN | Update an appointment |
| DELETE | `/api/citas/{id}` | Own/ADMIN | Delete an appointment |

### Barbers
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/peluqueros` | Authenticated | List active barbers |
| GET | `/api/peluqueros/{id}` | Authenticated | Get a barber by ID |
| POST | `/api/peluqueros` | ADMIN | Create a barber |
| PUT | `/api/peluqueros/{id}` | ADMIN | Update a barber |
| DELETE | `/api/peluqueros/{id}` | ADMIN | Deactivate a barber (soft delete) |

### Closed days
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/dias-bloqueados` | Authenticated | List blocked days from today onwards |
| POST | `/api/dias-bloqueados` | ADMIN | Block a date (`fecha` + optional `motivo`). 409 if already blocked or if the day has live appointments |
| DELETE | `/api/dias-bloqueados/{id}` | ADMIN | Unblock a date |

### Payments
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/pagos/crear-intent` | USER/ADMIN | Create a Stripe PaymentIntent for an appointment |
| POST | `/api/pagos/webhook` | Public | Stripe webhook (signature-verified, idempotent) |
| POST | `/api/pagos/manual` | ADMIN | Register a cash or bank-transfer payment |
| POST | `/api/pagos/{citaId}/reembolsar` | ADMIN | Refund a payment (Stripe or manual) |
| GET | `/api/pagos` | ADMIN | List payments (paginated). Optional `?desde=&hasta=&estado=&metodo=`; the range is inclusive on both ends and filters by payment date, falling back to creation date |
| GET | `/api/pagos/cita/{citaId}` | Own/ADMIN | Get the payment of an appointment |

> The listing filters on `COALESCE(fechaPago, fechaCreacion)`, so `?estado=PAGADO` over a range returns exactly the payments that add up to the revenue `/api/estadisticas` reports for that same period — which is what makes the dashboard's revenue bars drillable.

### Statistics
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/estadisticas` | ADMIN | Appointments by status, revenue by payment method, top services and new customers. `?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` optional; defaults to the last 30 days |

### Documentation (public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Interactive Swagger UI |
| GET | `/v3/api-docs` | OpenAPI specification (JSON) |

## Data Model

Schema is managed with **Flyway** (migrations `V1` to `V10` in `src/main/resources/db/migration/`):

* **`usuarios`** — customers and administrators: name, unique email, phone, hashed password, role, active flag, `token_version` and `avatar_clave`.
* **`servicios`** — salon catalog (haircuts, coloring...): description, duration in minutes, price, active flag and `imagen_clave`.
* **`peluqueros`** — barbers/stylists, with soft delete. An appointment may optionally be assigned to one.
* **`citas`** — links a `usuario` with a `servicio` (and optionally a `peluquero`) at a specific time. Status enum (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) and a `recordatorio_enviado` flag for the 24h reminder.
* **`dias_bloqueados`** — dates the salon does not open (holidays, one-off closures), with an optional reason. Fixed closed weekdays are not stored here: they come from configuration.
* **`pagos`** — payments linked 1:1 to an appointment. Card (Stripe), cash or transfer. Status: `PENDIENTE`, `PAGADO`, `REEMBOLSADO`, `CANCELADO`.
* **`stripe_evento`** — processed Stripe event IDs, guaranteeing webhook idempotency.
* **`password_reset_token`** — single-use password reset tokens with expiration.
* **`refresh_token`** — persistent refresh tokens for session rotation.

> `imagen_clave` and `avatar_clave` hold the **object key** (e.g. `12/uuid.jpg`), not a URL. The URL is built when reading, so moving bucket or provider — or turning a bucket private, which is exactly what avatars do compared to catalog photos — is a configuration change and not a data migration. For avatars it matters twice over: the URL is signed and expires, so persisting it would make no sense.

## Getting Started

### Option A — Docker Compose (fastest)

With Docker installed, a single command starts PostgreSQL and the API:

```bash
git clone https://github.com/eduardoandr3s/peluqueria_citas.git
cd peluqueria_citas
docker compose up --build
```

The API is available at `http://localhost:8080` (Swagger UI at `/swagger-ui.html`). Flyway applies all migrations automatically. Email sending is disabled (`MAIL_ENABLED=false`) and Stripe keys are not needed unless you test payments.

### Option B — Local JDK + PostgreSQL

1. **Prerequisites:** JDK 21 and a PostgreSQL instance on port `5432`.

2. **Create the database:**
    ```sql
    CREATE DATABASE peluqueria_db;
    ```

3. **Set environment variables** (in your system or IDE):
    * `DB_USERNAME` / `DB_PASSWORD`: PostgreSQL credentials.
    * `JWT_SECRET`: secret key for signing JWTs (at least 32 characters).
    * `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` *(optional)*: Stripe keys (test mode) to use payments.
    * `MAIL_USERNAME` / `MAIL_PASSWORD` *(optional)*: SMTP credentials for emails; set `MAIL_ENABLED=false` to disable email sending in dev.
    * `BUSINESS_EMAIL`: business email address for notifications.
    * `FRONTEND_URL`: frontend URL used in email links (default `http://localhost:4200`).
    * `CORS_ALLOWED_ORIGINS` *(prod profile only)*: comma-separated allowed origins. The `dev` profile already allows `http://localhost:4200`.
    * `SUPABASE_URL` / `SUPABASE_SERVICE_KEY` *(optional)*: object storage for images. **Without them the app writes to local disk and starts fine**, so you can clone and run this repo without an account on any external service. The service key bypasses row-level security, so it lives only on the server — never in a frontend, never in a commit. Under the `prod` profile these are **required**: the app refuses to start without them.

    *(Business hours can be adjusted with `peluqueria.horario.apertura` and `peluqueria.horario.cierre`; default 09:00-20:00.)*

4. **Run the application:**
    ```bash
    ./mvnw spring-boot:run
    ```

5. **Create an ADMIN user (optional):** register a user, then promote it directly in PostgreSQL:
    ```sql
    UPDATE usuarios SET rol = 'ADMIN' WHERE email = 'your-email@example.com';
    ```

## Deployment

* **API:** Render (Docker, multi-stage `Dockerfile` in this repo). Every push to `main` triggers a redeploy.
* **Database:** Supabase (managed PostgreSQL, connected through its IPv4 session pooler).
* **Image storage:** Supabase Storage, with two buckets to create — **`servicios`** (public read) and **`avatares`** (private, read through signed URLs). Binaries never live next to the application: Render's disk is ephemeral and every push to `main` wipes it.
* **Email:** transactional emails are sent through a provider's SMTP relay on port **2525** (Render's Free tier blocks outbound SMTP ports 25/465/587).
* **Frontends:** Firebase Hosting (see below).
* **CI:** GitHub Actions runs the full suite — unit + Testcontainers integration tests — on every push and pull request.

## Frontend

The project includes two frontend applications in a separate repository:

* **Admin panel** ([Angular 21](https://angular.dev) zoneless + Tailwind v4), with appointment/user/service/barber management, payments and a statistics dashboard.
* **Customer mobile app** ([Ionic 8](https://ionicframework.com) + Angular + Capacitor), with booking, barber selection, Stripe payment and biometric login.

Both live in the [peluqueria_citas_frontend](https://github.com/eduardoandr3s/peluqueria_citas_frontend) monorepo (npm workspaces), sharing models and services through the `packages/core` library.

---
*Developed by Eduardo Andres Segovia Roman.*
