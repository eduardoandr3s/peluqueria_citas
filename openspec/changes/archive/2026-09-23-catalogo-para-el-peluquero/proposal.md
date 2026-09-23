# Proposal

## Why

Un peluquero que entra en la app movil no tiene forma de ver el catalogo de servicios. Su barra
de pestanas solo tiene su agenda, su produccion y su perfil, y la pantalla de Servicios del area
de cliente la rebota su guard. Cuando un cliente le pregunta cuanto cuesta un servicio, cuanto
dura o que incluye, tiene que preguntarselo a su vez al administrador o pedirle el movil a un
cliente. El dato ya existe y ya es publico: lo unico que falta es que el personal pueda llegar a
el.

## What Changes

- La barra de pestanas del area de trabajo de la app movil gana una pestana **«Servicios»** para
  el PELUQUERO, entre «Mi produccion» y «Perfil».
- Esa pestana muestra el catalogo **tal como lo ve un cliente**: los servicios activos con su
  foto, nombre, descripcion, precio y duracion, el mismo buscador y el mismo gesto de recargar.
- Es de **solo consulta**. No aparece el boton «Agendar» (agendar es del area de cliente y su
  guard rebotaria al personal) ni los accesos de la cabecera a la galeria y al equipo, que viven
  en rutas del area de cliente.
- El ADMIN no cambia: sigue teniendo su pestana «Servicios» de gestion, y no se le anade una
  sexta pestana.
- El backend no cambia de comportamiento. Se anade un test de integracion que deja fijado que el
  listado de servicios responde al personal, porque a partir de ahora la app depende de ello.
- Fuera de alcance: el panel web. Un PELUQUERO sigue sin ver «Servicios» en el menu del panel.

**Modulos**: es **siempre-activa**. El catalogo de servicios no va detras de ningun modulo, y la
pestana tampoco. Si el modulo de produccion esta apagado, la barra del peluquero queda en
«Citas · Servicios · Perfil».

## Capabilities

### New Capabilities
- `servicio`: el catalogo de servicios del negocio. En este cambio recoge quien puede leer el
  listado (incluido el personal) y como lo consulta un peluquero desde su area de trabajo en la
  app movil.

### Modified Capabilities
(ninguna: todavia no hay especificaciones en `openspec/specs/`)

## Impact

- **Repositorio del frontend, app movil**: la barra de pestanas del area de trabajo, las rutas de
  esa area y la pantalla de Servicios del cliente, que pasa a servir tambien al personal en modo
  consulta. Con sus tests.
- **Backend**: solo un test de integracion nuevo. Ningun endpoint, permiso ni migracion cambia.
- **Documentacion**: los contadores de tests de los README de los dos repositorios, y en el README
  del frontend la frase que dice que un peluquero no ve la pestana de servicios.
- **Despliegue**: los peluqueros lo reciben al instalar una APK nueva. Pushear el frontend no
  basta, porque su despliegue es manual.
