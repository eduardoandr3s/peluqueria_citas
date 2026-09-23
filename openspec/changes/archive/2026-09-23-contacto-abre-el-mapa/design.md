# Design

## Context

La pantalla de Contacto de la app movil (`mobile/src/app/contacto/`, en el repositorio del
frontend) pinta la calle y la localidad que devuelve `/api/negocio`, sin enlace. El telefono y el
email si son enlaces (`tel:` y `mailto:` puestos en el `href` de un `ion-item`), y funcionan por
un mecanismo que conviene tener claro, porque es el que hace falta sortear aqui:

- En la app Android, cuando el WebView intenta navegar a una URL que no es la de la propia app,
  Capacitor lanza un `Intent.ACTION_VIEW` con esa URL. Asi es como `tel:` acaba en el marcador.
- Si ninguna app atiende ese intent, Capacitor **se traga la excepcion en silencio**
  (`ActivityNotFoundException`, con un `// TODO - trigger an event` en `Bridge.launchIntent`). Al
  JavaScript no le llega nada: el enlace simplemente no hace nada.

Con `tel:` y `mailto:` da igual, porque todo Android tiene marcador. Con un mapa no: la peticion
es precisamente que, si no hay app de mapas, se abra el navegador. Un enlace no puede enterarse de
que ha fallado, asi que la decision tiene que tomarse en codigo que si reciba la respuesta.

La app ya se separa de la web en otro sitio con el mismo patron: `FicheroService` decide entre
descargar (navegador) y compartir (nativo) por `Capacitor.isNativePlatform()`, encerrado en un
metodo `esNativo()` que los tests fuerzan. El backend no interviene: la direccion ya llega.

## Goals / Non-Goals

**Goals:**
- Una sola accion al tocar la direccion, que se resuelve segun la plataforma sin que la pantalla
  lo sepa.
- Poder distinguir en Android entre «se abrio una app de mapas» y «no habia ninguna».
- Tests que cubran las tres ramas (nativa con app, nativa sin app, navegador) sin un telefono.

**Non-Goals:**
- iOS. La app solo se compila para Android. En un iPhone con la version web se abre Google Maps
  como en cualquier navegador, y no se intenta abrir Apple Maps.
- Coordenadas. La tabla `negocio` no tiene latitud ni longitud y no se anaden: se busca por texto.
  Ni migracion ni cache que invalidar.
- Avisar con un mensaje si fallan a la vez la app de mapas y el navegador. En un Android con
  navegador eso no ocurre, y un aviso para un caso que no ocurre es codigo sin probar de verdad.
- Tocar el telefono o el email.

## Decisions

### 1. En Android, un `geo:` y no un enlace a Google Maps

Se pide al sistema que abra `geo:0,0?q=<direccion codificada>`. Es la URI estandar de ubicacion
en Android: la atienden Google Maps, Waze y cualquier app de mapas, y el sistema abre la
predeterminada o pregunta si no hay ninguna. Es exactamente la «app predeterminada de mapas».

- *Descartado: `https://www.google.com/maps/search/?api=1&query=...` tambien en Android.* Si
  Google Maps esta instalado se abre ahi (es un App Link verificado) y si no, en el navegador:
  cubriria el respaldo sin ningun plugin. Pero **nunca abriria Waze** aunque el usuario lo tenga
  como predeterminado, porque esa URL es de Google y el sistema se la da a Google. Es justo lo
  que no se pide.
- *Descartado: una URL propia de cada app (`waze://`, `comgooglemaps://`).* Obligaria a la app a
  elegir por el usuario y a mantener una lista.

El `0,0` es el centro que exige la sintaxis de `geo:` cuando se busca por texto. Con `q=` las apps
lo ignoran y centran en el resultado de la busqueda.

### 2. `@capacitor/app-launcher` para saber si alguien atendio el `geo:`

`AppLauncher.openUrl({ url })` lanza el mismo `ACTION_VIEW` pero **devuelve `{ completed }`**: en
Android es `false` si ninguna actividad lo atiende. Es el dato que el enlace no puede dar. Si llega
`false`, o si la llamada rechaza, se vuelve a llamar con la URL de Google Maps: un `ACTION_VIEW`
sobre `https` lo atiende el navegador predeterminado.

Es un plugin oficial de Capacitor, de la misma familia que `camera`, `filesystem` y `share`, que
ya estan en el proyecto. `openUrl` usa `startActivity` directamente, asi que **no hace falta
declarar `<queries>` en el `AndroidManifest`** por la visibilidad de paquetes de Android 11. Eso
solo lo exigiria `canOpenUrl`, que no se usa: preguntar primero y abrir despues son dos idas al
sistema para el mismo resultado.

- *Descartado: un plugin propio en `MainActivity`.* Haria lo mismo que el oficial con codigo
  nativo que mantener aqui.
- *Descartado: URI `intent:` con `S.browser_fallback_url`.* Ese respaldo lo interpreta Chrome,
  no Capacitor: dentro del WebView el intent fallaria igual de callado.
- *Descartado: `@capacitor/browser` para el respaldo.* Abriria Google Maps en una pestana dentro
  de la app (Custom Tabs). Lo que se pidio es el navegador, y el `ACTION_VIEW` ya lo da sin
  segundo plugin.

### 3. En el navegador, `window.open` a Google Maps en una pestana nueva

Fuera de la app nativa se abre `https://www.google.com/maps/search/?api=1&query=<direccion>` con
`window.open(url, '_blank', 'noopener')`. Es la URL universal que documenta Google: en un movil con
Google Maps instalado la abre la app, y si no, la web. `_blank` deja la app en su pestana.

La llamada va **sin ningun `await` antes**. Un navegador solo deja abrir una ventana nueva dentro
del gesto del usuario, y un `await` intermedio lo rompe y el bloqueador de ventanas se la come.

- *Descartado: `geo:` tambien en el navegador.* Chrome para Android lo entiende, pero un
  navegador de escritorio no hace nada con el, y la web publicada tambien se abre en escritorio.

### 4. Un servicio aparte, `MapaService`, junto a `FicheroService`

La logica de plataforma va en `mobile/src/app/core/mapa.service.ts`, con un unico metodo publico
`abrir(direccion: string)` y el mismo `protected esNativo()` que usa `FicheroService`. La pantalla
solo compone el texto a buscar y se lo pasa.

- Asi la pantalla no importa ningun plugin y su spec no necesita `vi.mock`: le basta un doble del
  servicio por inyeccion.
- **`@capacitor/app-launcher` solo lo mockea `mapa.service.spec.ts`**, que es el unico fichero que
  lo importa. Dos specs que hagan `vi.mock` del mismo modulo se pisan en el runner de CI.
- **`@capacitor/core` no se mockea**, por el mismo motivo que ya explica el comentario de
  `FicheroService.esNativo()`: un doble parcial de ese modulo puede dejar a Ionic sin
  `registerPlugin`. La rama se fuerza espiando `esNativo()`.
- El spy de `window.open` se restaura en un `afterEach`: el runner comparte `window` entre specs y
  uno que se quede espiado rompe al siguiente.

### 5. La fila de la direccion pasa a ser un boton

El `ion-item` de la direccion gana `button`, `detail="false"` y `(click)`, igual de aspecto que
las filas del telefono y el email. No se usa `[href]` porque no es una navegacion: es una accion
cuyo resultado hay que mirar. El texto a buscar lo compone un `computed` de la pantalla a partir
de `calle()` y `ciudad()`, las mismas senales que pinta la plantilla, para que lo que se busca no
pueda separarse de lo que se ve.

Se busca solo la direccion, no el nombre del salon delante. Con el nombre, un negocio que no este
dado de alta en Google puede no encontrarse, mientras que una direccion postal se encuentra
siempre.

## Risks / Trade-offs

- [La APK instalada no tiene el plugin] → Hasta reconstruirla, la app instalada no puede
  resolver la llamada al plugin. El plugin se registra en la APK nueva, y la web no lo necesita.
  Se entrega con la APK reconstruida y se avisa en la entrega.
- [Waze entiende peor que Google Maps una busqueda por texto libre] → Se le pasa la direccion
  postal completa, que es lo que mejor resuelve. Se comprueba a mano si en el telefono de prueba
  esta Waze. Si fallara, el usuario siempre puede elegir otra app en el selector del sistema.
- [La rama «sin app de mapas» no se puede ver en un telefono normal] → Google Maps viene de serie
  en casi todos los Android. La cubren los tests unitarios. A mano solo se prueba si se desactiva
  Maps temporalmente, y eso lo decide Eduardo: no se toca su telefono sin preguntar.
- [Dependencia nueva] → Oficial, pequena y de la misma version mayor que el resto de plugins
  (`^8`). Entra en el `package-lock.json` y en los ficheros Gradle que genera `cap sync`, que se
  commitean con el cambio.

## Migration Plan

Sin migracion de datos. Para desplegar: build y despliegue manual del frontend para la web, y APK
nueva para Android. Para deshacerlo basta revertir el commit del frontend y reconstruir la APK: la
direccion vuelve a ser texto.
