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
* **Spring Boot 4.1.1** (main framework)
* **PostgreSQL** (relational database)
* **Flyway** (database migrations, V1-V10)
* **Spring Data JPA / Hibernate** (ORM)
* **Supabase Storage** (object storage for images, over its REST API — no S3 SDK). Optional: without credentials the app falls back to local disk
* **openhtmltopdf + Thymeleaf** (PDF payment receipts rendered from an HTML template, reusing the engine the emails already use)
* **Spring Security + JWT** (stateless authentication and role-based authorization)
* **BCrypt** (one-way password hashing)
* **Stripe API** (online payments: PaymentIntents, webhooks, refunds)
* **Spring Mail** (transactional emails and appointment reminders)
* **Bucket4j** (rate limiting)
* **Spring AI + Gemini** (conversational assistant with tool calling over the real services)
* **springdoc-openapi** (Swagger UI documentation)
* **Maven** · **Lombok** · **Docker Compose** (local dev environment)
* **JUnit 5 + Mockito** (unit tests) · **Testcontainers** (integration tests against a real PostgreSQL)
* **GitHub Actions** (CI: full test suite on every push and pull request)

## Features

* **Domain-based architecture:** code is organized by business module (`usuario/`, `cita/`, `servicio/`, `pago/`, `peluquero/`, `calendario/`, `estadistica/`, `notificacion/`, `asistente/`, `auth/`, `security/`). Each module contains its entity, controller, service, repository and DTOs.
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
* **Work gallery:** photos of the salon's work with **manual ordering**, in a public bucket (`galeria`) because they are promotional material. They are read **without an account**, like the catalog, and only an ADMIN uploads, reorders and deletes. Each photo stores **two keys**, the image and a thumbnail: the grid always renders the thumbnail and the full-size image is fetched only when a photo is opened, because the free Storage plan's limit is **bandwidth** and a grid served with full-size images multiplies it by ten. The thumbnail is generated client-side and travels in the same multipart — the server has 0.1 CPU in production and the browser scales for free — is validated by magic bytes just like the big one and, if it is missing, the response falls back to the full-size image instead of leaving a gap. Deleting a photo deletes **both objects**, not just the big one.
* **The database stores the object key, not the URL** (`servicios.imagen_clave`, `usuarios.avatar_clave`). The URL is built on read, which is why switching bucket or provider — or turning a bucket private, as avatars did — is a configuration change and not a data migration.
* **Conversational assistant (Spring AI + Gemini):** `POST /api/asistente` answers in natural language about services, prices, opening hours, holidays and free slots. It does not improvise: every fact comes from a **tool** (*tool calling*) that calls the real service, and even today's date is handed to it, because a model left to guess what day it is resolves "tomorrow" by inventing a date. Four decisions are the design:
    * **Read-only, which is what lets it be public.** No tool writes, and no tool returns customer data. A prompt injection therefore has nothing to break, and not a single name or phone number reaches the provider — which is exactly what makes a free tier usable when its terms reserve the right to train on prompts. If the assistant grew towards booking appointments, that premise collapses and the provider would have to change before the code did.
    * **Off by default, and it is a real switch.** `spring.ai.model.chat` is `none` when unset, so without an API key the whole domain is not registered and the app starts anyway: the assistant is an extra, not a dependency. This is not decorative — Spring AI's autoconfiguration only checks that the class is on the classpath, not that credentials exist, so without that `none` the app **fails to start** in any environment without a key, integration tests included.
    * **The cap is about spend, not abuse.** A public endpoint burning a free tier's quota is exhausted within minutes once someone finds the URL, so it is rate-limited per IP through the `RateLimitFilter` that already existed. Quotas are **independent per route**: burning the assistant's cannot leave anyone unable to recover their password.
    * **Every token is paid again on every later turn.** The history is resent in full with each message, so the tools return minimal records rather than the application's DTOs: a service's image URL does not help state its price and would cost money on every turn. For the same reason there are caps on history, on the answer and on date ranges, and the response reports token usage — on a free tier the limit is quota, and unmeasured quota runs out without warning.
* **PDF payment receipt:** `GET /api/pagos/{id}/recibo` renders a one-page receipt from a Thymeleaf template, **generated on the fly and never stored** — it can always be rebuilt from the database, so keeping it would only add quota and lifecycle to manage. Only for **collected or refunded** payments: issuing a receipt for money that never arrived would state something false, so any other status returns **409**. The document says plainly that it is a proof of payment and **not an invoice**, since it carries no tax data.
* **Business statistics:** `GET /api/estadisticas` (ADMIN only) returns appointments by status, revenue broken down by payment method (refunds excluded, computed by payment date), top services and new customers. Defaults to the **last 30 days** when no date range is given.
* **Email notifications:** event-driven emails (registration, booking, modification, cancellation, payment confirmation, password changes) decoupled from business logic via Spring application events (`@TransactionalEventListener(AFTER_COMMIT)`), plus a **24-hour appointment reminder** sent by a scheduler (runs hourly, injectable `Clock` for testability, `recordatorio_enviado` flag guarantees a single send).
* **Three roles, with `PELUQUERO` in the middle:** a `USER` is the customer, an `ADMIN` can do everything, and a `PELUQUERO` (barber) sees **their own schedule** — the appointments assigned to their profile, not the shop's — closes them and checks their own sales. The `peluqueros` profile and the `usuarios` account are linked by a unique FK that is **optional in both directions**: a profile without an account is a professional the admin books for, and an account with the role but no profile simply has no schedule. There is no implicit Spring Security role hierarchy: every `SecurityConfig` rule states explicitly who gets through, because a hierarchy in one file and the rules in another is how an endpoint gets opened without anyone noticing.
* **Appointment closing and sales:** `PATCH /api/citas/{id}/cierre` leaves the appointment as `COMPLETADA`, `NO_ASISTIO` or `ANULADA` with notes and a `clienteContactado` flag, and stamps who closed it and when. Completing **freezes** the service price and the commission rate into the appointment, and that copy is what holds everything else up: without it, raising a rate in June would change March's already-settled sales and commissions. `GET /api/produccion/mia` adds up **only what is completed AND paid** — money counts once it is in, and cash comes in through the manual payment — with work done but unpaid reported separately so it never drops off every screen. The commission is a per-barber percentage with **per-service exceptions**, because a dye job does not pay the same as a haircut.
* **Ownership control:** a `USER` can only see, modify or delete their own appointments and data; a `PELUQUERO` also reaches the appointments assigned to their profile but **not a colleague's**; an `ADMIN` can access everything. Unauthorized access returns `403 Forbidden`.
* **DTO pattern:** every entity has separate DTOs for creation, partial update and response. Sensitive data is never exposed.
* **Pagination and sorting:** appointment and user listings are paginated (`page`, `size`, `sort`) and return a Spring Data `Page`.
* **Soft delete + reactivation:** users, services and barbers are never physically deleted, only deactivated. Deactivated users can be listed (`?incluirInactivos=true`) and reactivated (`PATCH /api/usuarios/{id}/activar`).
* **User search:** `GET /api/usuarios?search=` filters by name or email (contains, case-insensitive) in the database, combinable with `incluirInactivos` and pagination.
* **Global exception handling:** `@RestControllerAdvice` with specific handlers for validation (400), not found (404), access denied (403), conflicts (409) and a generic handler (500) that never leaks internal details. Includes SLF4J logging.
* **OpenAPI / Swagger UI documentation:** auto-generated with springdoc-openapi, available at `/swagger-ui.html` and `/v3/api-docs`.
* **Configuration profiles:** separate `dev` and `prod` environments. Schema is managed with **Flyway migrations** (`src/main/resources/db/migration/`). Under the `prod` profile the app **refuses to start** without storage credentials rather than falling back to local disk: on an ephemeral container that failure is silent — uploads succeed and vanish on the next deploy.
* **Observability (Actuator + Prometheus + Grafana):** `/actuator/prometheus` publishes JVM, HTTP, connection-pool and **business** metrics: appointments by state and service, payments, sign-ups, reminders, password-reset attempts and the assistant's token usage. The business counters are fed by the **domain events that already existed for the emails**, so no service was modified to measure it — and they count in `AFTER_COMMIT`, because an appointment whose insert rolled back is not an appointment. Three decisions are the design: only `health` and `prometheus` are exposed and Spring Security `denyAll`s everything else, since `env`/`beans`/`configprops` would dump the whole configuration with the Stripe and Gemini keys in it; the metrics endpoint is guarded by a **token in a header** rather than a JWT, because a scraper that runs every 30 seconds cannot renew one that expires; and the **mail health indicator is off**, because Actuator enables it just for having the mail starter and an SMTP hiccup would put the global health in `DOWN` — Render reads that endpoint, so a mail problem would have it restart a backend whose appointments and payments work perfectly.
* **Test suite (382 tests):** 336 unit tests covering the business logic without Spring context or database, plus 46 integration tests with **Testcontainers** (real PostgreSQL in Docker) covering authentication, ownership rules, statistics, payments, sales and commissions, role-based appointment closing and the full Stripe webhook flow with real signature verification.

## Project Structure

```
com.segovia.peluqueria/
├── almacen/        # File storage: port + Supabase/disk adapters, magic-byte validation
├── auth/           # Login, registration, refresh tokens, password reset (rate-limited)
├── cita/           # Appointments: booking, conflicts, availability slots, business hours
├── config/         # Cross-cutting config (async events, scheduling)
├── estadistica/    # Business statistics for the admin dashboard (ADMIN only)
├── exception/      # Global exception handling and shared exceptions
├── galeria/        # Work gallery: photos with manual ordering and a separate thumbnail
├── notificacion/   # Domain events, email notifications and the 24h reminder scheduler
├── pago/           # Payments: Stripe PaymentIntents, webhooks, manual payments, refunds
├── peluquero/      # Barbers: profile, linked account, commission and per-service exceptions
├── produccion/     # Per-barber sales and commission: sold, collected and outstanding
├── security/       # SecurityConfig, JWT service and filter, CORS
├── servicio/       # Service catalog
└── usuario/        # Users, roles, soft delete, search
```

Each business module follows the same layout: JPA entity, controller, service, repository and a `dto/` package.

## Tests

**382 tests** run in CI on every push (GitHub Actions).

### Unit tests (336)

They cover all business logic without Spring context or database (a few seconds):

| Class | Tests | Coverage |
|-------|-------|----------|
| CitaServiceTest | 64 | Booking, business hours, closed days, conflicts, CRUD, ownership, availability, pagination, barber validation, auto-confirmation on payment, and **closing**: freezing price and commission on completion, refusing to complete what has not started, a customer may only cancel, a barber cannot rewrite a finished closing nor touch someone else's schedule, PUT rejects `COMPLETADA`, and the customer never receives notes or commission |
| UsuarioServiceTest | 40 | CRUD, duplicate email, hashing, soft delete, ownership, reactivation, pagination, search, avatar upload/removal |
| PagoServiceTest | 32 | PaymentIntents, webhooks, manual payment, refunds, polling, concurrency, who may request a receipt and in which payment states |
| CalendarioServiceTest | 17 | Closed weekdays, blocking/unblocking dates, past dates, duplicates, days with live appointments, closed-day ranges |
| AsistenteHerramientasTest | 11 | The assistant's tools: delegation to the real services, that only what the model needs travels to it (no image URLs, no descriptions), the closed-day range cap, an invalid date turned into a message the model can correct, and that "today" comes from the `Clock` rather than a guess |
| AsistenteServiceTest | 9 | History mapped to the right role, token usage read back, and exhausted-quota detection versus any other failure (including a cyclic cause, which would hang the walk) |
| RateLimitFilterTest | 8 | **Per-route independent quotas**: burning the assistant's does not leave password recovery without attempts. Plus per-IP quotas, `X-Forwarded-For` behind the proxy, and that it leaves other routes and methods alone |
| ServicioServiceTest | 16 | CRUD, soft delete, catalog photo upload/replacement/removal |
| GaleriaServiceTest | 17 | Gallery: uploads image and thumbnail to the public bucket, server-generated keys kept in separate folders per size, response falls back to the full-size image when there is no thumbnail, rejects non-images (thumbnail included), a new photo lands after the last one, and deleting removes **both** objects |
| ReciboPdfGeneradorTest | 10 | Renders the **real PDF** with the production template and reads the text back with PDFBox: payment and appointment data, refund notice, "not an invoice" disclaimer, fixed decimal format |
| ValidadorImagenTest | 10 | Magic-byte validation: real JPEG/PNG/WebP, lying `Content-Type`, lying extension, oversized and empty files, server-generated key |
| JwtServiceTest | 9 | Token generation/extraction/validation, signatures, tokenVersion |
| AuthControllerTest | 8 | Login, registration, invalid credentials |
| RefreshTokenServiceTest | 8 | Rotation, revocation, expiration |
| JwtAuthenticationFilterTest | 7 | Filter with/without token, invalid/expired token, deactivated account, tokenVersion |
| PasswordResetServiceTest | 7 | Request, reset, expiration, anti-enumeration |
| PeluqueroServiceTest | 19 | CRUD and soft delete, account linking (rejects a `USER`, a deactivated account and one already linked to another profile), unlinking, and the applicable commission: the per-service exception beats the profile's rate |
| ProduccionServiceTest | 9 | Mapping of the summary, the breakdowns and the outstanding amount, account with no linked profile, inverted and over-two-year ranges, and rounding to two decimals what Postgres returns |
| SupabaseStorageAlmacenTest | 7 | Storage REST calls with `MockRestServiceServer`: upload, delete, URL signing, and that keys with a folder are not escaped |
| MetricasNegocioListenerTest | 6 | Business counters: one metric per concept with `estado`/`servicio` as labels, a missing service name falling back instead of throwing, and that **no metric ever carries a customer's name or email** — a personal data leak and a new time series per person |
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

### Integration tests (46, Testcontainers)

They boot the full application against a **real PostgreSQL** started in Docker (`@ServiceConnection`), with Flyway migrations applied:

* **EstadisticasIntegrationTest** (5) — statistics over real data: default 30-day range, revenue by payment method, refunds excluded.
* **WebhookIntegrationTest** (3) — end-to-end Stripe webhook: a signed `payment_intent.succeeded` event is verified with the **real Stripe SDK signature check**, the payment becomes `PAGADO` and the appointment is confirmed; duplicated events are processed only once (idempotency); invalid signatures get 400.
* **PagosIntegrationTest** (10) — payment listing for the dashboard (pagination, range and status filters, ADMIN only) and the PDF receipt over HTTP: the owner gets a real PDF as an attachment, an ADMIN gets anyone's, someone else gets 403, an uncollected payment 409.
* **GaleriaIntegrationTest** (2) — the gallery's asymmetry over HTTP: the listing is read **without a token**, while uploading, reordering and deleting return 403 with no token and as a client, and work as ADMIN. The second one covers what a service unit test cannot see: a photo uploaded without a thumbnail returns the full-size image in its place, and an executable renamed to `.jpg` still ends in 400 after going through a real multipart request.
* **OwnershipIntegrationTest** (2) — a user cannot read (GET) or edit (PUT) someone else's appointment (403); `/api/usuarios/me` never exposes the password.
* **AsistenteApagadoIntegrationTest** (3) — with the assistant switched off (the default) the app **still starts** and its route answers **404, not 500**, which is what lets the client tell "not deployed" from "failed". The third pins the counterpoint: an unknown route that is **not** public answers 403, because Spring Security cuts in before the dispatcher and does not confirm to an anonymous caller which routes exist.
* **MetricasIntegrationTest** (6) — who can read what from Actuator: `prometheus` answers 403 with no token, with a wrong token, and 200 with the right one; `env`, `beans`, `configprops` and `loggers` stay closed **even with the valid token**; `health` is public, shows no internals and **survives a broken SMTP** (this one caught a real bug: the mail indicator was putting health in `DOWN`, which would have had Render restart the backend in a loop). The sixth publishes a domain event and finds `peluqueria_citas_total` in the actual scrape — the only place where the metric name in the code and the one the dashboard queries are checked together.
* **ProduccionIntegrationTest** (7) — sales and commission against real Postgres, the only place the native SQL (`DATE_TRUNC`, `TO_CHAR` and four JOINs) is actually checked: it adds up only what is completed **and paid**, leaving out no-shows, cancellations, another barber's work and completed-but-unpaid work (which comes back as outstanding); the per-service and monthly breakdowns; **the frozen amount wins** — the rate is raised after settling and sales do not move; the staff comparison ordered by amount; and who does not get in: a barber sees neither another's sales nor the comparison, a customer not even their own, and an account with the role but no profile gets a 404. The seventh pins down the owner-who-also-cuts-hair case: an **ADMIN account linked to a profile** sees its own sales through `/produccion/mia` with no sub-role at all, because the role says what you may do and the profile says who does the work.
* **CierreCitaIntegrationTest** (7) — closing over HTTP and what the `PELUQUERO` role may touch. Deliberately over HTTP: the rules for who reaches which appointment are **not** in `SecurityConfig` (`/api/citas/**` is "any authenticated user") but in the service, because they depend on the linked profile. A barber closes their own appointment and the amount is frozen; cannot close or even read a colleague's (403); the customer cancels their own but cannot mark it done, and never receives the internal notes; `COMPLETADA` through the old PUT is a 400; a finished closing is only correctable by an ADMIN, and correcting it stops it counting; the barber's listing is their schedule and not the shop's; and `usuarios`, `estadisticas`, `pagos` and barber management still answer 403.
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

### Work gallery
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/galeria` | Public | List the photos, already ordered |
| POST | `/api/galeria` | ADMIN | Upload a photo at the end of the grid (`multipart/form-data`: `imagen`, optional `miniatura`, optional `titulo`) |
| PUT | `/api/galeria/{id}` | ADMIN | Change the title or the position |
| DELETE | `/api/galeria/{id}` | ADMIN | Delete the photo and both of its objects in the store |

> The thumbnail is optional in the endpoint and mandatory in practice: without it the listing returns the full-size image in `urlMiniatura`, which works but multiplies the grid's bandwidth. Both clients in this repo always generate it.

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
| GET | `/api/citas` | USER/PELUQUERO/ADMIN | List appointments (paginated). A USER only sees their own, a PELUQUERO their schedule, an ADMIN sees all |
| GET | `/api/citas/disponibilidad` | USER/ADMIN | Free slots for `?fecha=YYYY-MM-DD&idServicio=N`. Optional `&peluqueroId=N` for a specific barber. Empty on closed days |
| GET | `/api/citas/dias-cerrados` | USER/ADMIN | Closed days (closed weekdays + blocked dates) with their reason. Optional `?desde=&hasta=`; defaults to the next 3 months, 12-month range cap |
| GET | `/api/citas/{id}` | Own/ADMIN | Get an appointment by ID |
| POST | `/api/citas` | USER/ADMIN | Book an appointment (a USER only for themselves), optionally with a barber |
| PUT | `/api/citas/{id}` | Own/ADMIN | Update an appointment |
| PATCH | `/api/citas/{id}/cierre` | Own/PELUQUERO/ADMIN | Close the appointment: `estado` (`COMPLETADA`, `NO_ASISTIO` or `ANULADA`), `observaciones` and `clienteContactado`. Completing freezes the amount and the commission; a USER may only send `ANULADA`; a finished closing is only correctable by an ADMIN |
| DELETE | `/api/citas/{id}` | Own/ADMIN | Delete an appointment |

> `COMPLETADA` and `NO_ASISTIO` **do not go through the PUT** (it answers 400): closing freezes the amount and the commission, and the other path would leave completed appointments with no frozen price — exactly the hole sales figures cannot have. Cancelling through the PUT still works and leaves the same who-and-when trail.

### Barbers
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/peluqueros` | Authenticated | List active barbers |
| GET | `/api/peluqueros/{id}` | Authenticated | Get a barber by ID |
| POST | `/api/peluqueros` | ADMIN | Create a barber |
| GET | `/api/peluqueros/gestion` | ADMIN | Full profiles (active **and inactive**) with commission and linked account |
| PUT | `/api/peluqueros/{id}` | ADMIN | Update a barber: `nombre`, `activo`, `comisionPorcentaje`, `usuarioId` to link the account or `desvincularUsuario` |
| GET | `/api/peluqueros/{id}/comisiones` | ADMIN | Per-service commission exceptions |
| PUT | `/api/peluqueros/{id}/comisiones` | ADMIN | Replace the whole set of exceptions (whatever is not sent is deleted) |
| DELETE | `/api/peluqueros/{id}` | ADMIN | Deactivate a barber (soft delete) |

> The commission and the linked account are **not** part of `PeluqueroResponseDTO`, which is nested in every appointment and read by customers: what a professional earns only leaves through the ADMIN endpoints. Linking an account with the `USER` role answers 400 instead of silently changing its role: without the role, the owner of that profile would not see a single appointment.

### Sales and commission
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/produccion/mia` | PELUQUERO/ADMIN | Sales for the barber **of the authenticated account** (the id is not a parameter, it is resolved from the account). `?desde=&hasta=`; defaults to the current month, 24-month cap |
| GET | `/api/produccion/peluquero/{id}` | ADMIN | Anyone's |
| GET | `/api/produccion` | ADMIN | Whole-staff comparison, ordered by amount |

> Returns services performed, amount sold and commission, plus the per-service and monthly breakdown, and separately `serviciosSinCobrar`/`importeSinCobrar`. It only adds up `COMPLETADA` appointments **whose payment is `PAGADO`**; cash comes in by registering the manual payment, which until the configurable-permissions phase is still ADMIN-only.

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
| GET | `/api/pagos/{id}/recibo` | Own/ADMIN | Download the PDF receipt (`attachment`). **The id is the payment's, not the appointment's.** 409 unless the payment is `PAGADO` or `REEMBOLSADO` |
| GET | `/api/pagos/cita/{citaId}` | Own/ADMIN | Get the payment of an appointment |

> The listing filters on `COALESCE(fechaPago, fechaCreacion)`, so `?estado=PAGADO` over a range returns exactly the payments that add up to the revenue `/api/estadisticas` reports for that same period — which is what makes the dashboard's revenue bars drillable.

### Statistics
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/estadisticas` | ADMIN | Appointments by status, revenue by payment method, top services and new customers. `?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` optional; defaults to the last 30 days |

### Assistant (public)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/asistente` | Public | Natural-language question about services, prices, opening hours, closed days and free slots. The body carries `mensaje` plus the conversation `historial` (max. 10 turns). Returns the answer and the turn's token usage. Capped at 10 requests/hour per IP; 503 if the provider fails or the quota is exhausted; 404 if the assistant is switched off |

### Observability
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/actuator/health` | Public | Liveness for Render's health check. No details, no components, and the mail indicator does not count towards it |
| GET | `/actuator/prometheus` | Token | Metrics in Prometheus format. Requires the `X-Metrics-Token` header matching `METRICAS_TOKEN`; without the variable set the endpoint is closed to everyone |
| — | `/actuator/**` | Denied | Everything else is `denyAll`, `env` and `configprops` included |

### Documentation (public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Interactive Swagger UI |
| GET | `/v3/api-docs` | OpenAPI specification (JSON) |

## Data Model

Schema is managed with **Flyway** (migrations `V1` to `V11` in `src/main/resources/db/migration/`):

* **`usuarios`** — customers and administrators: name, unique email, phone, hashed password, role, active flag, `token_version` and `avatar_clave`.
* **`servicios`** — salon catalog (haircuts, coloring...): description, duration in minutes, price, active flag and `imagen_clave`.
* **`peluqueros`** — barbers/stylists, with soft delete. An appointment may optionally be assigned to one.
* **`citas`** — links a `usuario` with a `servicio` (and optionally a `peluquero`) at a specific time. Status enum (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) and a `recordatorio_enviado` flag for the 24h reminder.
* **`galeria_fotos`** — work gallery photos: `imagen_clave`, `miniatura_clave` (optional), title, manual `orden` and upload date.
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

    * `ASISTENTE_MODELO` / `GEMINI_API_KEY` *(optional)*: switch the conversational assistant on. They go **together**: `ASISTENTE_MODELO=google-genai` plus an API key from [Google AI Studio](https://aistudio.google.com/apikey). Without them the assistant is not deployed and the rest of the API works exactly the same.
    * `METRICAS_TOKEN` *(optional)*: token that `/actuator/prometheus` requires in the `X-Metrics-Token` header. Without it the endpoint is closed to everyone, which is the intended failure mode: forgetting the variable hides the metrics instead of publishing them.
    * `GEMINI_MODEL` *(optional)*: model id, default `gemini-3.6-flash`. Worth knowing it exists, because Google retires ids faster than it documents them: `gemini-2.5-flash` answers **404 `no longer available to new users`** to a freshly created key while the deprecation page still lists it as active with no shutdown date. The symptom is a 503 from `/api/asistente`, and this variable fixes it without a redeploy.

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
* **Image storage:** Supabase Storage, with three buckets to create — **`servicios`** and **`galeria`** (public read) and **`avatares`** (private, read through signed URLs). Binaries never live next to the application: Render's disk is ephemeral and every push to `main` wipes it.
* **Email:** transactional emails are sent through a provider's SMTP relay on port **2525** (Render's Free tier blocks outbound SMTP ports 25/465/587).
* **Frontends:** Firebase Hosting (see below).
* **CI:** GitHub Actions runs the full suite — unit + Testcontainers integration tests — on every push and pull request.

## Observability

Metrics live in **Prometheus + Grafana running locally** and scraping production. They are not deployed to Render: its free tier is already at the limit of its 750 h/month with the backend and the cron job that keeps it awake. The trade-off is that Prometheus only collects while the stack is up, so the graphs have gaps shaped like the hours the laptop was off.

```
Render (production)                    Your machine
┌──────────────────────┐   scrape +   ┌──────────────┐     ┌─────────┐
│ /actuator/prometheus │◀─── token ───│  Prometheus  │────▶│ Grafana │
└──────────────────────┘    30s       └──────────────┘     └─────────┘
```

```bash
# 1. In Render, set METRICAS_TOKEN to a long random value
# 2. Same value here (no trailing newline: the header must match exactly)
echo -n 'THE_TOKEN' > observabilidad/prometheus/token
# 3. Up
cd observabilidad && docker compose up -d
# 4. http://localhost:3000 — user admin, password admin
```

The dashboard (`observabilidad/grafana/dashboards/peluqueria.json`) is **provisioned from the repository**, not stored in a Docker volume: it survives `docker compose down -v` and shows up in diffs. The datasource is provisioned too, so there is nothing to click after starting.

To try it against your own backend instead of production — useful because business metrics can be triggered on demand in local, while in production you wait for a real customer:

```bash
cd observabilidad && docker compose -f compose.yaml -f compose.local.yaml up -d
```

Two things worth knowing before reading the panels:

* **A counter does not exist until it happens.** Micrometer only publishes a counter after its first increment, which is what keeps the endpoint from serving hundreds of zeroed series. A business panel is empty because nothing has happened yet, not because it is broken.
* **Every panel was checked query by query against production.** Two of them had to be fixed after seeing the real output, because Spring AI's metric names are not the ones its documentation suggests: the token meter is a **counter** (`gen_ai_client_token_usage_total`, not a `_sum`), and the timer is `gen_ai_client_operation_seconds` **without buckets** — which is why the assistant has no percentiles and shows mean and worst case instead.
* **One customer question is several calls to the model, not one.** A single "how much is a haircut?" left `gen_ai_client_operation_seconds_count = 4`: the model asks for a tool, gets the result, and calls again. It matters for the quota, because the free tier limits *requests*: ~1500/day is around **375 customer questions**, not 1500. That is what the "calls to the model per hour" series is for.

## Frontend

The project includes two frontend applications in a separate repository:

* **Admin panel** ([Angular 21](https://angular.dev) zoneless + Tailwind v4), with appointment/user/service/barber management, payments and a statistics dashboard.
* **Customer mobile app** ([Ionic 8](https://ionicframework.com) + Angular + Capacitor), with booking, barber selection, Stripe payment and biometric login.

Both live in the [peluqueria_citas_frontend](https://github.com/eduardoandr3s/peluqueria_citas_frontend) monorepo (npm workspaces), sharing models and services through the `packages/core` library.

---
*Developed by Eduardo Andres Segovia Roman.*
