# Tasks

Todas las rutas `mobile/...` y los README que se citan sin repositorio son del repositorio del
frontend, y los comandos se lanzan desde la raiz de ese monorepo. En el backend no se toca codigo.
Ningun test de este cambio agenda citas, asi que no entra en juego el calendario.

## 1. Dependencia

- [x] 1.1 Instalar el plugin con `npm install @capacitor/app-launcher@^8 -w @peluqueria/mobile`. Se verifica con `mobile/package.json`, que lo lista con `^8` como el resto de plugins de Capacitor, y con `package-lock.json` actualizado.
- [x] 1.2 Antes de escribir ningun `vi.mock`, comprobar con `grep -rn "app-launcher" mobile/src` que ningun spec lo mockea ya. Se verifica con el grep vacio.

## 2. Servicio que abre el mapa

- [x] 2.1 Crear `mobile/src/app/core/mapa.service.spec.ts` con `vi.hoisted` y `vi.mock('@capacitor/app-launcher')`. La rama se fuerza espiando `esNativo()`, sin mockear `@capacitor/core`, y el spy de `window.open` se restaura en un `afterEach`. Casos que cubre: en el navegador llama a `window.open` con la URL de Google Maps, `'_blank'` y `'noopener'`, sin tocar el plugin. En nativo con `completed: true` llama una sola vez a `openUrl` con `geo:0,0?q=...`. En nativo con `completed: false` hace una segunda llamada con la URL de Google Maps. Si `openUrl` rechaza, tambien pasa a la URL de Google Maps. Una direccion con comas, espacios, tildes y enie llega codificada con `encodeURIComponent` a las dos URL. Se verifica con los tests en rojo porque el servicio aun no existe.
- [x] 2.2 Crear `mobile/src/app/core/mapa.service.ts` (`providedIn: 'root'`) con `abrir(direccion)` y `protected esNativo()`, comentando el porque: por que no basta un enlace (Capacitor se traga el fallo), por que `geo:`, por que no hay ningun `await` antes del `window.open` y por que no se mockea `@capacitor/core`, remitiendo al comentario de `FicheroService` en vez de repetirlo. Se verifica con `npm test -w @peluqueria/mobile -- --watch=false` y los tests de 2.1 en verde.

## 3. Pantalla de Contacto

- [x] 3.1 En `mobile/src/app/contacto/contacto.page.ts`, inyectar `MapaService`, anadir un `computed` `direccionMapa` que una `calle()` y `ciudad()` con `', '` (solo la que exista si falta una) y un metodo `abrirMapa()` que se la pase a `abrir`. En `contacto.page.spec.ts`, anadir un doble de `MapaService` al `setup` y tests de la direccion completa, de solo localidad, de solo calle y de que `abrirMapa()` manda al servicio ese mismo texto. Se verifica con la suite del movil en verde.
- [x] 3.2 En `mobile/src/app/contacto/contacto.page.html`, dar al `ion-item` de la direccion `button`, `detail="false"` y `(click)="abrirMapa()"`. Anadir al comentario de la plantilla por que la direccion no va por `href` como el telefono y el email. Se verifica con la build del movil sin errores.
- [x] 3.3 Anadir en `contacto.page.spec.ts` tests sobre la plantilla, con `TestBed.createComponent`. Pulsar la fila de la direccion llama a `abrir` con la direccion completa. Sin calle ni localidad no hay fila de direccion. Las filas del telefono y el email siguen llevando su `href`. Sin la plantilla nada comprobaria que la fila sigue siendo pulsable. Se verifica con la suite del movil en verde, y viendo que el test del clic falla si se quita el `(click)`.

## 4. Proyecto Android

- [x] 4.1 Ejecutar `npm run build -w @peluqueria/mobile` y despues `npx cap sync android` desde `mobile/`. Se verifica con `mobile/android/capacitor.settings.gradle` y `mobile/android/app/capacitor.build.gradle`, que incluyen `capacitor-app-launcher`, y con el `AndroidManifest.xml` de la app sin `<queries>` nuevas.
- [x] 4.2 Compilar la APK con `./gradlew assembleDebug` desde `mobile/android`, con `ANDROID_HOME` apuntando al SDK. Se verifica con la build terminada y la APK generada en `app/build/outputs/apk/debug/`.

## 5. Verificacion de conjunto

- [x] 5.1 Ejecutar las dos suites del frontend (`npx ng test --watch=false` y `npm test -w @peluqueria/mobile -- --watch=false`) y anotar los totales. Se verifica con 0 fallos.
- [x] 5.2 Comprobar que en el backend no ha cambiado nada fuera de `openspec/`, con `git status` en ese repositorio. Se verifica con la salida: solo aparece el directorio del cambio.
- [x] 5.3 Probar a mano en el movil, con una sesion de cliente (la pestana Contacto es del area de cliente). Tocar la direccion abre la app de mapas predeterminada, o el selector del sistema si no hay ninguna marcada, buscando la direccion del salon. Instalar la APK en su telefono y, si se quiere ver la rama sin app de mapas, desactivar temporalmente Google Maps: las dos cosas se le preguntan a Eduardo antes. Se verifica con lo observado anotado en la entrega. Si no se puede hacer, se deja indicado para que lo haga Eduardo. Hecha a mano por Eduardo el 2026-09-23, con la APK instalada en su movil.
- [x] 5.4 Probar a mano en el navegador que tocar la direccion abre Google Maps en una pestana nueva con la direccion buscada y que la app sigue en Contacto. Se verifica con lo observado anotado en la entrega, o se deja indicado para Eduardo si hace falta un backend en marcha y no hay credenciales. Hecha a mano por Eduardo el 2026-09-23, tras desplegar el frontend.

## 6. Documentacion

- [x] 6.1 Actualizar los contadores de los README del frontend (`README.md` y `README.es.md`): el total de Vitest, que aparece dos veces en cada uno, y la cifra de la fila del movil, con los totales de 5.1. Anadir a esa fila que se cubre como se abre el mapa: la app predeterminada, el navegador cuando no hay app, la pestana nueva en la web y la fila pulsable comprobada sobre la plantilla. Se verifica con un `grep` de los numeros viejos, que no devuelve nada.
- [x] 6.2 Confirmar que los README del backend no cambian: el cambio no anade ni quita tests alli, y la cifra del backend que citan los README del frontend sigue siendo la misma. Se verifica con esa cifra coincidiendo con el total de los README del backend.
- [x] 6.3 En los dos README del frontend, ampliar la frase de la pantalla de contacto: la direccion abre la app de mapas predeterminada del telefono y, si no hay ninguna, Google Maps en el navegador. Se verifica leyendo las dos versiones, que dicen lo mismo.

## 7. Entrega

- [x] 7.1 No commitear. Entregar a Eduardo la lista de ficheros tocados en cada repositorio y un mensaje propuesto para cada uno, en conventional commits, en espanol sin tildes ni enies y sin `Co-Authored-By`: `feat:` para el frontend, y para el backend el del directorio del cambio. Recordar que en el movil no llega sin una APK nueva, porque trae un plugin nativo, y que la web necesita el despliegue manual. Se verifica con la entrega hecha.
