# AGENTS.md — Contexto del proyecto

App de peluqueria: reservas de citas, pagos con Stripe, notificaciones por email y panel de
administracion. Backend Spring Boot 4.1.1 + Java 21. Completo, desplegado y en funcionamiento.

Repo backend: `https://github.com/eduardoandr3s/peluqueria_citas`. El frontend vive en un repo
aparte (monorepo Angular 21 + Ionic 8). Se trabaja directamente sobre `main` en ambos.

## Entorno de desarrollo

Hace falta un JDK 21 y un PostgreSQL 17 al que conectarse. El nombre de la base de datos y sus
credenciales salen de variables de entorno (ver mas abajo), no estan escritos en el codigo. Para la
suite de integracion hace falta ademas un Docker en marcha.

```bash
./mvnw install -DskipTests
```

Si en la maquina conviven varios JDK, apunta `JAVA_HOME` al 21 antes de invocar `./mvnw`.

## Organizacion del codigo

Por **dominio**, no por capa. Paquetes bajo `com.segovia.peluqueria/`: `almacen`, `asistente`,
`auth`, `calendario`, `cita`, `config`, `estadistica`, `exception`, `galeria`, `metrica`, `modulo`,
`negocio`, `notificacion`, `pago`, `peluquero`, `permiso`, `security`, `servicio`, `usuario`. Cada
uno agrupa entidad + repository + service + controller + `dto/`.

Convenciones que **no** hay que "limpiar":

- Constructor injection con campos `final`, sin `@Autowired`.
- DTOs Request/Response explicitos con mapeo manual en los services. Nada de ModelMapper.
- Excepciones centralizadas en `exception/GlobalExceptionHandler`.
- Soft delete en `Usuario` y `Servicio` (`activo=false`); las citas si se borran fisicamente.
- Lombok: en entidades JPA `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, **nunca
  `@Data`**. En los DTOs si se usa `@Data`.
- La version de Testcontainers va explicita en el `pom.xml` porque el BOM de Spring Boot no la
  gestiona. Hay un comentario que explica por que no se puede bajar: no lo quites.
- Se comenta el porque, no el que.

Migraciones Flyway hasta `V17__negocio.sql`; la siguiente seria `V18__*.sql`. Para saber cual es la
ultima de verdad, mira `src/main/resources/db/migration`.

La configuracion de negocio (nombre, contacto, marca y **horario**) esta en la tabla `negocio`, no
en `application.properties`: es una fila unica con un CHECK, la siembra la V17 y se edita desde el
panel. `peluqueria.horario.*` ya no existe. Quien necesita la hora de apertura pregunta a
`NegocioService`, que cachea igual que `ModuloService` y `PermisoService`.

Dar de alta una peluqueria nueva: `scripts/alta-cliente.sh` + `docs/alta-cliente.md`.

## Tests

**Este fichero es la unica fuente sobre como se corren los tests.** Los README no repiten estas
condiciones, solo llevan los contadores.

```bash
# Suite completa: unitarios + integracion. Necesita Docker en marcha.
./mvnw test

# Solo unitarios, sin Docker. En Windows es la unica opcion.
./mvnw test -Dtest='!*IntegrationTest'
```

En Linux y macOS **el comando normal es la suite completa**. La integracion entera tarda menos de
un minuto una vez las imagenes estan descargadas, asi que excluirla no compensa: se dejarian de
comprobar todos los tests que tocan PostgreSQL de verdad, Flyway, la seguridad y los webhooks.

Los tests de integracion estan en `integracion/`, todos sobre `AbstractIntegrationTest`
(`@SpringBootTest(RANDOM_PORT)` + `@Testcontainers` + `@DirtiesContext`). El `@DirtiesContext` es
necesario porque Spring cachea el contexto entre clases y Testcontainers para el container al
acabar cada una.

Cosas que muerden:

- **En Windows los tests de integracion no arrancan**: Testcontainers no habla con el named pipe de
  Docker Desktop. Ahi se usa la exclusion y se confia en CI, que corre en Ubuntu.
- **No bajes la version de Testcontainers del `pom.xml`.** Las versiones anteriores a la fijada
  negocian una version vieja de la API de Docker que los Docker Engine recientes ya rechazan, y el
  error que sale no lo dice: aparece un enganoso «Could not find a valid Docker environment». El
  comentario del `pom.xml` lo explica en el sitio donde importa.
- `PeluqueriaApplicationTests` se salta si `DB_USERNAME` no esta definida.
- Ningun test agenda "manana": un helper busca el proximo lunes, para que una ejecucion en sabado
  no caiga en un dia cerrado.
- `ddl-auto=validate` en test: Flyway tiene que cuadrar con las entidades.
- Si Flyway falla con «Found more than one migration with version N», hay una migracion fosil en
  `target/classes` de un nombre viejo. Se arregla con `./mvnw clean test`.

## Variables de entorno

No estan en el sistema ni en un `.env`: viven en la Run Configuration del IDE (en IntelliJ,
`.idea/workspace.xml`, seccion `<envs>`, separadas por `;`). Por eso la terminal y `psql` no las
ven. Las minimas para arrancar en local son `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`.

**Este fichero es publico: lleva nombres de variables, nunca valores.** Las credenciales de
desarrollo viven unicamente en la Run Configuration, que queda fuera del control de versiones. No
las copies aqui ni a ningun otro fichero del repositorio.

| Variable | Propiedad | Para que sirve |
|----------|-----------|----------------|
| `DB_URL` | `spring.datasource.url` | conexion a PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | credenciales de la BD | acceso a PostgreSQL |
| `JWT_SECRET` | `jwt.secret` | firma de los tokens |
| `JWT_EXPIRATION` | `jwt.expiration` | caducidad del token de acceso (30 min) |
| `REFRESH_EXPIRACION_DIAS` | `peluqueria.refresh.expiracion-dias` | caducidad del refresh (30 dias) |
| `MAIL_HOST` / `MAIL_PORT` | SMTP | servidor de salida del correo |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP | credenciales del correo |
| `MAIL_FROM` | `peluqueria.mail.from` | remitente; si falta, cae a `MAIL_USERNAME` |
| `MAIL_ENABLED` | `peluqueria.mail.enabled` | apaga el envio real (activo por defecto) |
| `BUSINESS_EMAIL` | `peluqueria.mail.negocio` | buzon del negocio para los avisos |
| `FRONTEND_URL` | `peluqueria.frontend-url` | base de los enlaces que van en los correos |
| `RECORDATORIO_HORAS_ANTES` | `peluqueria.recordatorio.horas-antes` | antelacion del recordatorio (24 h) |
| `RESET_EXPIRACION_MINUTOS` | `peluqueria.reset.expiracion-minutos` | caducidad del enlace de reset (30 min) |
| `RATELIMIT_RESET_CAPACIDAD` | rate limit de `/recuperar` y `/reset` | intentos permitidos |
| `RATELIMIT_RESET_VENTANA_MINUTOS` | ventana del rate limit | duracion de la ventana |
| `CORS_ALLOWED_ORIGINS` | `cors.allowed-origins` | origenes permitidos en produccion |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | pagos | cuenta de Stripe y firma del webhook |
| `SUPABASE_URL` / `SUPABASE_SERVICE_KEY` | almacen de ficheros | sin ellas, cae al disco local |
| `SUPABASE_BUCKET_SERVICIOS` / `SUPABASE_BUCKET_AVATARES` | buckets | fotos de catalogo y avatares |
| `SUPABASE_BUCKET_GALERIA` | bucket de la galeria de trabajos | es el unico publico |
| `ALMACEN_VALIDEZ_URL_FIRMADA` | caducidad de la URL firmada del avatar | 1 h |

En perfil `prod` sin credenciales de almacen la aplicacion **no arranca** a proposito: el disco del
contenedor es efimero. En `dev` cae al disco local.

## Docker en local

`compose.yaml` levanta `db` (postgres:17-alpine con healthcheck) y `api` (build multi-stage,
`depends_on: service_healthy`), con credenciales de dev hardcodeadas a proposito.

```bash
docker compose up --build
```

Trampa conocida: `db` publica el puerto estandar de PostgreSQL y choca con el que este instalado en
la maquina.

## Despliegue

- **Backend:** plataforma de hosting gestionada, con auto-deploy al pushear a `main`. La base de
  datos de produccion es un PostgreSQL gestionado, con TLS obligatorio en la conexion.
- **Correo en produccion:** el plan de hosting bloquea los puertos SMTP habituales, asi que el
  correo sale por un relay transaccional en un puerto alternativo. Esa es la razon de que
  `MAIL_HOST` y `MAIL_PORT` sean variables y no constantes.
- **Frontend:** manual, el CI **no** despliega. Se publica en un hosting estatico con dos targets
  (`admin` y `app`), que suben a la vez.
- Trampa conocida: reinicializar el hosting del frontend **sobrescribe** su fichero de
  configuracion y lo deja apuntando a un directorio que no existe. Verificar siempre que sigue
  apuntando al `dist` real del build.

## CI

`.github/workflows/ci.yml` en ambos repos: solo tests y builds, ningun paso de deploy. Backend con
JDK 21 + cache de Maven + `./mvnw -B test` (los tests de integracion si corren, es Ubuntu).

## Convenciones de trabajo

- Los commits los decide Eduardo. Preparar el cambio y entregarle los ficheros y el mensaje; no
  commitear por cuenta propia salvo que lo pida.
- Mensajes en espanol, conventional commits (`feat:`, `fix:`, `docs:`, `test:`), **sin tildes ni
  enies**. Cuerpo explicando que cambia y por que.
- **Nunca `Co-Authored-By`** en los commits.
- Los mensajes y la documentacion van autocontenidos: nada de referencias a planificacion interna.
- Todo cambio de comportamiento lleva su test, y actualiza los contadores de los dos README.
- Si aparece un problema colateral, se senala pero no se arregla sin preguntar.

## Estado y pendientes

Funcionando: citas, pagos con Stripe, notificaciones por email, recordatorio 24h, estadisticas,
multi-peluquero, dias cerrados, ficheros (foto de catalogo, avatar, recibo en PDF), galeria de
trabajos, CV publico del equipo, modulos activables, asistente con Spring AI, metricas con Actuator
y Prometheus, app Android, biometria, README bilingues, CI verde.

Nada critico pendiente. En el aire:

- Dos extras opcionales de portfolio, ninguno empezado: RabbitMQ y Kubernetes en local.
- El separador decimal no es uniforme en el frontend: conviven «15,00 €» y «15.00 €» segun se use
  el pipe `number` o `toFixed`.
