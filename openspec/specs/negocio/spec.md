# negocio Specification

## Purpose

Los datos del negocio (nombre, contacto, marca y horario), que se editan desde el panel, y como
los aprovecha la app movil para que un cliente pueda llamar, escribir o llegar al salon.

## Requirements

### Requirement: La direccion de contacto abre un mapa
En la pestana «Contacto» de la app movil, la direccion del salon SHALL poderse tocar cuando el
negocio tiene rellena la calle, la localidad o las dos. Al tocarla SHALL abrirse un mapa que busca
esa direccion: la calle y la localidad separadas por una coma, o solo la que este rellena. Lo que
se busca MUST ser la misma direccion que se muestra en la pantalla. La funcionalidad es
siempre-activa: no depende de ningun modulo.

#### Scenario: Se busca la direccion completa
- **WHEN** el negocio tiene la calle «Carrer de Colon, 42» y la localidad «46004 Valencia, Espana» y el cliente toca la direccion
- **THEN** se abre un mapa buscando «Carrer de Colon, 42, 46004 Valencia, Espana»

#### Scenario: Solo hay localidad
- **WHEN** el negocio tiene la localidad rellena pero no la calle y el cliente toca la direccion
- **THEN** se abre un mapa buscando solo la localidad

#### Scenario: Sin direccion no hay nada que tocar
- **WHEN** el negocio no tiene ni calle ni localidad
- **THEN** la pestana «Contacto» no muestra la fila de direccion

### Requirement: En la app Android se abre la app de mapas predeterminada
En la app Android, tocar la direccion SHALL entregar la direccion al sistema como una ubicacion,
de forma que la abra la app de mapas que el usuario tenga como predeterminada (Google Maps, Waze u
otra). Si el usuario no ha elegido ninguna y tiene varias, SHALL ser el sistema quien le pregunte
con cual abrirla. La app MUST NOT imponer una app de mapas concreta.

#### Scenario: Hay una app de mapas predeterminada
- **WHEN** el usuario tiene Waze como app predeterminada para ubicaciones y toca la direccion
- **THEN** se abre Waze buscando la direccion del salon

#### Scenario: Hay varias y ninguna predeterminada
- **WHEN** el usuario tiene Google Maps y Waze instaladas sin ninguna marcada como predeterminada y toca la direccion
- **THEN** el sistema le pregunta con cual de las dos abrir la direccion

### Requirement: Sin app de mapas se abre el navegador en Google Maps
En la app Android, si en el telefono no hay ninguna app capaz de abrir una ubicacion, tocar la
direccion SHALL abrir el navegador del telefono en Google Maps con la direccion buscada. Tocar la
direccion MUST NOT quedarse sin hacer nada.

#### Scenario: El telefono no tiene app de mapas
- **WHEN** en el telefono no hay ninguna app que abra ubicaciones y el usuario toca la direccion
- **THEN** se abre el navegador en Google Maps buscando la direccion del salon

### Requirement: En el navegador se abre Google Maps en una pestana nueva
En la version web de la app movil, tocar la direccion SHALL abrir Google Maps con la direccion
buscada en una pestana nueva. La pestana de la app MUST seguir abierta donde estaba.

#### Scenario: El cliente usa la version web
- **WHEN** un cliente abre la pestana «Contacto» desde el navegador y toca la direccion
- **THEN** se abre una pestana nueva con Google Maps buscando la direccion y la de la app sigue en «Contacto»
