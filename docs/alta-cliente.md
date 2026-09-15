# Dar de alta una peluquería nueva

Este producto se instala **una vez por peluquería**: cada cliente tiene su base de datos, su
backend, su sitio web y su APK. Los módulos deciden qué usa cada una, pero la instalación es
suya y no se comparte con nadie.

Esa decisión es deliberada y tiene su motivo escrito: hoy la configuración de negocio que no
está en la tabla `negocio` sigue repartida entre variables de entorno (las claves de Stripe,
el remitente del correo, los buckets), y con una instalación por cliente eso ya es «de este
cliente» sin escribir una línea de código. El día que haya suficientes peluquerías para que
mantener N despliegues duela más que aislarlas dentro de uno solo, se añade `negocio_id` y
esta guía cambia entera.

**Lo que hay que saber antes de empezar:** el alta tiene una parte que no se puede
automatizar (crear cuentas en Supabase, en Render y en Firebase, que son tres paneles web) y
otra que sí. La segunda la hace `scripts/alta-cliente.sh`. Esta guía es el orden completo.

---

## 1. La base de datos (Supabase)

1. Proyecto nuevo en [Supabase](https://supabase.com). Región cerca del cliente.
2. Guardar la contraseña de la base: no se vuelve a enseñar.
3. Copiar la cadena de conexión del **session pooler** (no la directa) y añadirle
   `?sslmode=require`.
4. **Tres buckets de Storage**, con estos nombres y esta visibilidad:

   | Bucket      | Público | Por qué                                                   |
   |-------------|---------|-----------------------------------------------------------|
   | `servicios` | sí      | fotos del catálogo, que se ven sin cuenta                 |
   | `galeria`   | sí      | escaparate de trabajos, y también las fotos del CV        |
   | `avatares`  | **no**  | un avatar es un dato personal: se sirve con URL firmada   |

   El CV de los peluqueros **no tiene bucket propio**: va dentro de `galeria` con el prefijo
   `peluqueros/`. Es a propósito, para no añadir un bucket y una variable más cuyo olvido no
   se nota hasta que alguien sube una foto.

5. Copiar la **service key** (`Settings → API`). Vive solo en el backend y nunca en un
   frontend: se salta las políticas de Supabase.

Las tablas **no** se crean a mano: las crea Flyway al arrancar el backend por primera vez.

## 2. El backend (Render)

Servicio web nuevo apuntando al repo del backend, rama `main`. Variables de entorno:

| Variable | Qué es | ¿Obligatoria? |
|---|---|---|
| `DB_URL` | la cadena JDBC del pooler de Supabase, con `?sslmode=require` | sí |
| `DB_USERNAME`, `DB_PASSWORD` | usuario y contraseña de esa base | sí |
| `JWT_SECRET` | secreto propio de esta instalación, largo y aleatorio | sí |
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | el almacén de ficheros | sí |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | el relay de correo | sí |
| `MAIL_FROM` | remitente **verificado** en el proveedor | sí |
| `BUSINESS_EMAIL` | dónde llegan los avisos de citas del negocio | sí |
| `FRONTEND_URL` | la web de este cliente; va en los enlaces de los correos | sí |
| `CORS_ALLOWED_ORIGINS` | los orígenes del panel y de la app de este cliente | sí |
| `SPRING_PROFILES_ACTIVE` | `prod` | sí |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | solo si va a cobrar con tarjeta | no |
| `ASISTENTE_MODELO`, `GEMINI_API_KEY` | solo si quiere el asistente | no |

**Dos trampas que cuestan una tarde cada una:**

- **En Render Free los puertos SMTP 25, 465 y 587 están bloqueados de salida.** Hay que usar
  un relay transaccional por el 2525 (Brevo, por ejemplo). Con el 587 no falla el arranque:
  fallan los correos, y en silencio.
- **`JWT_SECRET` es de cada instalación.** Reutilizar el de otra peluquería haría que un
  token de una valiera en la otra.

`MAIL_FROM` y las claves de Stripe son **de la instalación, no de la tabla `negocio`**: el
remitente lo impone el proveedor de correo (tiene que ser un sender verificado) y la cuenta
de Stripe es de cada negocio. Por eso no están en la pantalla del panel.

## 3. El alta propiamente dicha (el script)

Con el backend ya desplegado y las migraciones pasadas:

```bash
cp scripts/alta-cliente.env.ejemplo /tmp/peluqueria-tal.env
$EDITOR /tmp/peluqueria-tal.env      # API, DB, la primera cuenta, el negocio y el perfil
./scripts/alta-cliente.sh /tmp/peluqueria-tal.env
```

El script espera a que el backend responda (en Render Free el primer arranque tarda), crea la
primera cuenta, la asciende a ADMIN, guarda los datos del negocio, aplica el perfil de
módulos y, si se le pide, siembra tres servicios de ejemplo.

**El fichero `.env` no se guarda en el repo:** lleva la contraseña del administrador y la
conexión a la base.

### Con qué módulos arranca

| Perfil | Qué deja encendido |
|---|---|
| `SOLO_AGENDA` | citas y recordatorios por correo |
| `AGENDA_Y_CAJA` | lo anterior + cobro en efectivo o transferencia + producción |
| `TODO` | el producto entero, pasarela de tarjeta incluida |

**Ni `SOLO_AGENDA` ni `AGENDA_Y_CAJA` encienden el pago con tarjeta**, y no es un descuido:
Stripe necesita una cuenta y unas claves propias de ese negocio, así que el primer día no hay
con qué cobrar online y encenderlo sería ofrecerle al cliente final una pasarela que devuelve
error. Se enciende después, desde el panel, cuando las claves estén puestas.

Aplicar un perfil **no borra nada**: lo que apaga sigue en la base y vuelve tal cual al
encenderlo. Y no deja al negocio «en» ningún perfil: después se le cambia cualquier módulo
desde la pantalla de Módulos.

## 4. El frontend (Firebase Hosting)

El despliegue del frontend es **manual**: el CI no despliega.

1. Proyecto de Firebase para este cliente, con dos sitios de hosting (el panel y la app).
2. En el repo del frontend, apuntar `src/environments/environment.ts` y
   `mobile/src/environments/environment.prod.ts` al backend de este cliente.
3. `npm run build` en la raíz (panel) y en `mobile/` (app).
4. `firebase deploy --only hosting` sube los dos targets a la vez.

Trampa conocida: `firebase init hosting` **sobrescribe** `firebase.json` y lo deja en
`"public": "public"`. Verificar siempre que apunta a `dist/peluqueria-frontend/browser`.

**El orden importa: backend primero, frontend después.** El frontend pregunta
`GET /api/modulos/activos` y `GET /api/negocio`, y esconde lo que no venga en la respuesta.

## 5. La APK

Es una app por cliente, no una app con varios negocios dentro. Hay que cambiar en el repo del
frontend, antes de compilar:

- El `applicationId` de Android (`mobile/android/app/build.gradle` y `capacitor.config.ts`).
  Dos peluquerías con el mismo id no pueden convivir en un mismo móvil ni en Play.
- `mobile/src/index.html` → `<title>`.
- El logo: `mobile/src/assets/logo.png` y los iconos (`npm run assets`).

El **nombre, el teléfono, la dirección y el horario NO se tocan aquí**: salen de
`GET /api/negocio` y se editan desde el panel. Eso es lo que evita tener que recompilar la
APK para cambiar un número de teléfono.

## 6. Lo que queda por hacer dentro del panel

El script no lo hace porque son decisiones de la peluquería, no del despliegue:

1. **Dar de alta al equipo** (Peluqueros) y, si va a usar el CV público, rellenarlo.
2. **Repasar los permisos por rol.** Los cuatro de galería y `PAGO_MANUAL_REGISTRAR` nacen
   **apagados**: un permiso nace cerrado, al revés que un módulo.
3. **Días cerrados**: los festivos del año. El horario semanal ya lo puso el script; los
   cierres de un día concreto son otra pantalla.
4. Si va a cobrar con tarjeta: poner las claves de **su** cuenta de Stripe en Render y
   encender después el módulo «Pago con tarjeta».

---

## Comprobación final

```bash
curl -s https://EL-BACKEND/api/negocio | jq
curl -s https://EL-BACKEND/api/modulos/activos | jq
```

La primera tiene que devolver el nombre y el horario de este cliente; la segunda, los módulos
de su perfil. Si la segunda devuelve un catálogo que no cuadra con lo que se pidió, casi
siempre es que Render todavía no ha terminado de desplegar: no es un bug, es el orden.
