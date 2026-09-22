# Design

## Context

La motivacion esta en `proposal.md`; los requisitos, en `specs/servicio/spec.md`.

Casi todo el cambio vive en el **repositorio del frontend**, en la app movil. Las rutas que se citan
abajo son relativas a la raiz de ese repositorio. Este repositorio solo recibe los artefactos de
OpenSpec y un test de integracion.

Estado actual en la app movil:

- Hay dos areas con su propia barra de pestanas. `/tabs` es la del cliente y la cierra el
  `clientGuard`, que manda al personal a `/admin`. `/admin` es el area de trabajo, la cierra el
  `staffGuard` (ADMIN o PELUQUERO), y dentro las pantallas de gestion repiten el `adminGuard`.
- `/admin/servicios` es la **gestion** del catalogo y lleva `adminGuard`. `admin-tabs.page.html`
  solo pinta su pestana si `esAdmin()`, asi que la barra de un PELUQUERO es «Citas · Mi produccion
  · Perfil».
- La pantalla que ve el cliente es `mobile/src/app/servicios/servicios.page.*`: pide
  `ServicioService.listar()`, se queda con los activos, filtra en memoria por nombre o
  descripcion sin tildes, y cada tarjeta lleva un boton «Agendar» que navega a `/tabs/agendar`. En
  la cabecera tiene los accesos a `/tabs/galeria` y `/tabs/equipo`, cada uno detras de su modulo.
- Ya hay un precedente de pantalla compartida por las dos areas: `equipo.page` se abre tambien
  desde la agenda del personal, y **lo que cambia entre las dos entradas lo decide la sesion**
  (`esStaff`, leido de `AuthService.isStaff()`), no la ruta.

En el backend, `GET /api/servicios` es `permitAll` en `SecurityConfig` y el service devuelve solo
los activos (`findByActivoTrue`). Ningun test lo comprueba con una sesion de PELUQUERO.

## Goals / Non-Goals

**Goals:**

- Una sola pantalla de catalogo para cliente y personal, de forma que «lo ve como un cliente» siga
  siendo verdad cuando la pantalla del cliente cambie.
- Que la diferencia entre las dos areas se reduzca a quitar las acciones que llevan al area de
  cliente.

**Non-Goals:**

- Cambiar la pantalla de gestion del ADMIN o su barra.
- Tocar el backend mas alla de un test: ningun endpoint, permiso, modulo ni migracion. **No hace
  falta migracion Flyway** y no se toca ningun dato cacheado.
- El panel web.

## Decisions

### 1. Reutilizar la pantalla del cliente, no copiarla

La pestana del peluquero carga el mismo componente `ServiciosPage`.

- *Descartado: una pantalla nueva para el personal.* Es la forma rapida, pero la peticion es verlo
  «como lo veria un cliente», y dos copias dejan de coincidir en cuanto una cambia (una foto nueva,
  otro formato de precio, otro orden) sin que ningun test lo note.
- *Descartado por ahora: extraer la tarjeta a un componente compartido y montar dos pantallas
  encima.* Da lo mismo con mas piezas. Tendria sentido si algun dia las dos vistas divergen de
  verdad; hoy la unica diferencia son las acciones.

### 2. Una ruta nueva hija del area de trabajo: `/admin/catalogo`

Sin `adminGuard`, porque la puerta del area (`staffGuard`) ya es la que corresponde. La pestana
dice «Servicios»: un PELUQUERO nunca ve la de gestion, asi que no hay dos pestanas con el mismo
nombre. La ruta se llama distinto porque `servicios` ya es la gestion.

- *Descartado: servir la consulta desde `/admin/servicios` y elegir el componente segun el rol.*
  Esa ruta lleva `adminGuard` y es la de gestion. Mezclar las dos en una URL obliga a quitar el
  guard y a decidir el componente por rol dentro de la ruta, y los tests de rutas dejan de poder
  afirmar algo tan simple como «la gestion esta cerrada al personal».
- *Descartado: dejar entrar al personal en `/tabs/servicios`.* El `clientGuard` existe para que el
  personal no caiga en el area de cliente. Abrirle esa ruta le da una barra con agendar y mis citas
  que no son suyas.

Un ADMIN que escriba `/admin/catalogo` a mano ve la vista de consulta. Es inofensivo y no hace falta
cerrarlo: es su area y el catalogo es publico.

### 3. El modo consulta lo decide la sesion, no la ruta

`ServiciosPage` lee `AuthService.isStaff()` en un `esStaff` y la plantilla envuelve las acciones de
cliente (el boton «Agendar» y los dos accesos de la cabecera) en un `@if (!esStaff())`. Es el mismo
criterio que ya usa `equipo.page`.

- *Descartado: un flag en el `data` de la ruta.* La app no usa `withComponentInputBinding`, asi que
  habria que leer `ActivatedRoute`. Y un flag permite combinaciones que no existen: un cliente en
  modo consulta, o el personal con «Agendar», que su guard rebotaria. Con la sesion la regla es
  literal: el personal no agenda desde la app, luego no ve «Agendar».

### 4. Los accesos de la cabecera se ocultan, no se redirigen

Para el personal desaparecen los dos. La galeria solo tiene ruta dentro de `/tabs`. El equipo tiene
la ruta publica `/equipo`, pero el personal ya entra ahi desde su agenda.

- *Descartado: que el icono del equipo apunte a `/equipo` cuando la sesion es de personal.* Repite
  una entrada que ya tiene y convierte la cabecera en cuatro casos (dos modulos por dos roles) para
  ganar un atajo.

### 5. El ADMIN no gana pestana

Su barra ya tiene cinco, y en la app ya se decidio no poner una sexta porque se aprieta en pantallas
pequenas (por eso la galeria entra por la cabecera). Ademas su pantalla de gestion ya muestra
precio, descripcion y duracion. Si algun dia la pide, la ruta ya existe y solo faltaria la entrada.

### 6. La pestana va entre «Mi produccion» y «Perfil»

«Perfil» es la ultima en todas las barras de la app, y la agenda es la primera porque es a donde
se aterriza.

### 7. El test del backend va en `PermisoIntegrationTest`

Cada clase de integracion levanta su propio contenedor por el `@DirtiesContext`, asi que una clase
nueva para un test cuesta un arranque entero. `PermisoIntegrationTest` ya tiene en su `setUp` una
sesion de PELUQUERO con **toda la matriz de permisos apagada** y un servicio activo sembrado, que
es justo el escenario del spec. La baja se hace por la API (`DELETE /api/servicios/{id}` con el
token de ADMIN), no por SQL, para que el test recorra el camino real.

## Risks / Trade-offs

- [Una accion nueva de cliente que se anada a esta pantalla le aparecera tambien al personal] →
  Un comentario en `servicios.page` dice que la pantalla sirve a las dos areas y que lo que lleve
  al area de cliente va dentro del `@if (!esStaff())`. El test de plantilla del personal falla si
  aparece un «Agendar».
- [Ionic empareja la pestana con la ruta por el atributo `tab`, y un nombre que no coincide deja la
  pestana sin marcar al entrar] → El `tab` es `catalogo`, igual que la ruta hija, y se comprueba a
  mano en el navegador que la pestana se marca.
- [`ServiciosPage` pasa a inyectar `AuthService`] → El `setup` de su spec tiene que proveer un doble
  con `isStaff`. Si no, fallan todos sus tests a la vez, lo que es facil de ver.
- [El comentario de `admin-tabs.page.ts` sobre la produccion deja de ser verdad («la unica pestana
  que un PELUQUERO tiene aparte de sus citas y su perfil»), y lo mismo el test que lo repite] → Se
  corrigen en el mismo cambio.

## Migration Plan

1. El test del backend se puede subir en cualquier momento: no cambia nada desplegado.
2. El frontend se despliega a mano, como siempre. El target `app` del hosting publica la app movil
   como web, y los peluqueros que usan la APK la reciben al instalar una nueva.
3. Marcha atras: revertir el commit del frontend y volver a desplegar. No hay datos ni migraciones
   de por medio.
