# Proposal

## Why

En la pestana «Contacto» de la app movil el telefono y el email ya son enlaces: al tocarlos se
abre el marcador o el correo. La direccion, en cambio, es texto muerto. Un cliente que quiere
llegar al salon tiene que copiarla a mano y pegarla en su app de mapas, que es justo lo que la
pantalla deberia ahorrarle.

## What Changes

- Tocar la direccion en la pestana «Contacto» abre la direccion del salon en un mapa.
- En la app Android se abre la **app de mapas que el usuario tenga como predeterminada**
  (Google Maps, Waze u otra). Si no ha elegido ninguna, Android le pregunta con cual abrirla.
- Si en el telefono no hay ninguna app capaz de abrir una ubicacion, se abre **el navegador en
  Google Maps** con la direccion buscada, en vez de no hacer nada.
- En la version web (el navegador, incluida la web publicada) se abre Google Maps con la
  direccion en una pestana nueva, sin sacar al usuario de la app.
- Lo que se busca es la direccion que ya muestra la pantalla (calle y localidad), la misma que se
  edita desde el panel. Si el negocio solo tiene una de las dos, se busca esa.
- El telefono y el email no cambian.
- El backend no cambia.

**Modulos**: es **siempre-activa**. La pestana «Contacto» no va detras de ningun modulo y la
direccion tampoco.

## Capabilities

### New Capabilities
- `negocio`: los datos del negocio (nombre, contacto y horario) y como los usa la app movil. En
  este cambio recoge que la direccion de la pestana «Contacto» abre un mapa, con que app y que
  pasa cuando no hay ninguna.

### Modified Capabilities
(ninguna: la unica especificacion que existe es `servicio`, que no se toca)

## Impact

- **Repositorio del frontend, app movil**: la pantalla de Contacto y un servicio nuevo que decide
  como abrir el mapa segun la plataforma, con sus tests.
- **Dependencias**: la app movil gana el plugin oficial de Capacitor para abrir URLs en otras
  apps, que es lo que permite saber si habia una app de mapas y, si no, pasar al navegador. Hay
  que sincronizarlo con el proyecto Android.
- **Backend**: ninguno. Ni endpoints, ni permisos, ni migraciones, ni tests.
- **Documentacion**: los contadores de tests de los README del frontend y la frase que describe
  la pantalla de contacto. Los README del backend no cambian.
- **Despliegue**: en el movil solo llega con una APK nueva, porque trae un plugin nativo. En la
  web basta con el despliegue manual del frontend.
