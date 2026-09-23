# servicio Specification

## Purpose

El catalogo de servicios del negocio: que servicios se ofrecen, a que precio y con que duracion,
y quien puede consultarlo, tanto a traves de la API como desde cada area de la app movil.

## Requirements

### Requirement: El listado de servicios lo lee cualquiera, incluido el personal
El listado de servicios (`GET /api/servicios`) SHALL responder 200 con los servicios activos tanto
a una peticion sin sesion como a una con sesion de cualquier rol: cliente, PELUQUERO o ADMIN. Para
un PELUQUERO no SHALL depender de ningun permiso configurable. Un servicio dado de baja MUST NOT
aparecer en el listado.

#### Scenario: Un peluquero sin permisos lee el catalogo
- **WHEN** un PELUQUERO sin ningun permiso configurable encendido pide `GET /api/servicios` con su token
- **THEN** la respuesta es 200 y trae los servicios activos del negocio

#### Scenario: Un servicio dado de baja no aparece
- **WHEN** un ADMIN da de baja un servicio y despues un PELUQUERO pide `GET /api/servicios`
- **THEN** ese servicio no esta en la respuesta y los demas activos si

### Requirement: El peluquero consulta el catalogo desde su area de trabajo
La app movil SHALL ofrecer al PELUQUERO una pestana «Servicios» en la barra de su area de trabajo,
entre «Mi produccion» y «Perfil». La pestana SHALL mostrar el catalogo igual que lo ve un cliente:
los servicios activos con su foto si la tiene, nombre, descripcion si la tiene, precio en euros y
duracion, con el mismo buscador por nombre o descripcion (sin distinguir mayusculas ni tildes) y
el mismo gesto de recargar. La pestana es siempre-activa: no depende de ningun modulo.

#### Scenario: La pestana esta en la barra del peluquero
- **WHEN** un PELUQUERO entra en su area de trabajo de la app movil
- **THEN** la barra de pestanas muestra «Citas», «Mi produccion», «Servicios» y «Perfil», en ese orden

#### Scenario: Sin el modulo de produccion la pestana sigue ahi
- **WHEN** el negocio tiene apagado el modulo de produccion y un PELUQUERO entra en su area de trabajo
- **THEN** la barra muestra «Citas», «Servicios» y «Perfil»

#### Scenario: El peluquero ve los mismos datos que un cliente
- **WHEN** un PELUQUERO abre la pestana «Servicios»
- **THEN** ve cada servicio activo con su foto, nombre, descripcion, precio y duracion, y ningun servicio dado de baja

#### Scenario: El buscador funciona igual
- **WHEN** un PELUQUERO escribe «coloracion» sin tilde en el buscador de la pestana
- **THEN** la lista se queda con los servicios cuyo nombre o descripcion contienen «coloración»

### Requirement: La vista del peluquero es de solo consulta
En el area de trabajo, la pantalla del catalogo MUST NOT ofrecer acciones que lleven al area de
cliente: ni el boton «Agendar» de cada servicio ni los accesos de la cabecera a la galeria y al
equipo. En el area de cliente la pantalla SHALL seguir como estaba.

#### Scenario: Sin boton de agendar para el personal
- **WHEN** un PELUQUERO abre la pestana «Servicios»
- **THEN** ningun servicio muestra el boton «Agendar»

#### Scenario: Sin accesos de cabecera para el personal
- **WHEN** un PELUQUERO abre la pestana «Servicios» en un negocio con los modulos de galeria y equipo encendidos
- **THEN** la cabecera no muestra los accesos a la galeria ni al equipo

#### Scenario: El cliente no pierde nada
- **WHEN** un cliente abre «Servicios» en su area
- **THEN** cada servicio sigue mostrando «Agendar» y la cabecera sigue mostrando los accesos a la galeria y al equipo segun sus modulos

### Requirement: El administrador conserva su pestana de gestion
La barra del area de trabajo de un ADMIN MUST NOT cambiar: sigue teniendo una sola pestana
«Servicios», la de gestion del catalogo, y no gana una pestana de consulta.

#### Scenario: La barra del administrador queda igual
- **WHEN** un ADMIN entra en su area de trabajo de la app movil
- **THEN** la barra muestra «Citas», «Produccion», «Servicios», «Usuarios» y «Perfil», y «Servicios» abre la gestion del catalogo
