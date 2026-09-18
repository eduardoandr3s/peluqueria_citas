# Proposal

## Why

El contexto de como se trabaja en este proyecto esta disperso y nada lo mantiene sincronizado:
`openspec/config.yaml` esta inicializado pero completamente vacio, `AGENTS.md` no sube al
repositorio y ya contiene informacion falsa, y las condiciones para ejecutar los tests viven
duplicadas en el `pom.xml`, en `AGENTS.md` y en los dos README.

Esa dispersion ya ha provocado un error real y medible: `AGENTS.md` afirma que los tests de
integracion no arrancan en Linux y manda excluirlos siempre, cuando el problema se arreglo al fijar
la version de Testcontainers en el `pom.xml`. La suite completa de integracion corre hoy en local en
menos de un minuto, y se estaba saltando por una nota que nadie volvio a revisar.

Publicar `AGENTS.md` obliga ademas a sanearlo: hoy contiene credenciales de desarrollo en claro,
incluida una clave real de Stripe, y rutas de una maquina concreta.

## What Changes

- Rellenar `openspec/config.yaml` con el contexto del proyecto, las reglas por artefacto
  (`proposal`, `specs`, `design`, `tasks`) y la guia por operacion (`apply`, `archive`). Sin datos
  que caduquen: nada de contadores de tests ni numeros de version dentro de la prosa.
- Sanear `AGENTS.md` y sacarlo de `.gitignore` para que pueda subir al repositorio publico. Se
  eliminan credenciales, rutas de maquina y detalles identificables de la infraestructura de
  produccion. La tabla de variables de entorno se queda, pero solo con nombres y proposito.
- Rotar la clave de Stripe expuesta en `AGENTS.md`. Es una tarea manual en el panel de Stripe, no
  automatizable, y debe hacerse aunque el fichero nunca llegue a subirse.
- Declarar `AGENTS.md` como fuente unica de las condiciones de ejecucion de los tests. Los README y
  `config.yaml` dejan de repetirlas y pasan a apuntar a el.
- Corregir lo que `AGENTS.md` dice y ya no es cierto: los tests de integracion si corren en Linux,
  el comando por defecto pasa a ser `./mvnw test` a secas, y la exclusion de la suite de integracion
  queda reservada a Windows, donde Testcontainers sigue sin poder hablar con Docker Desktop.

No es un cambio con ruptura: no se toca ningun comportamiento del producto ni ninguna API.

## Capabilities

### New Capabilities

Ninguna. Este cambio no introduce comportamiento observable.

### Modified Capabilities

Ninguna. El cambio afecta solo a documentacion y a configuracion del flujo de trabajo, por lo que
declara `skip_specs: true` en su `.openspec.yaml`.

## Impact

Ficheros afectados:

- `openspec/config.yaml`: pasa de estar vacio a llevar contexto, reglas y guia de operaciones.
- `AGENTS.md`: saneado y reescrito en las secciones de entorno, variables, tests y despliegue.
- `.gitignore`: se retira la entrada de `AGENTS.md`.
- `README.md` y `README.es.md`: la explicacion de como correr los tests se sustituye por una
  referencia a `AGENTS.md`.

Sin impacto en codigo de produccion, dependencias, esquema de base de datos ni API.

Riesgo principal: `AGENTS.md` pasa a ser publico, asi que cualquier dato sensible que quede dentro
queda expuesto. Se mitiga con una revision explicita en las tareas y con la rotacion de la clave de
Stripe. A favor juega que nada se ha filtrado todavia: `AGENTS.md` no tiene ningun commit, `.idea/`
no esta rastreado y la clave no aparece en el historial, de modo que no hace falta reescribir
historia.

Fuera de alcance, para un cambio aparte: el valor por defecto de `peluqueria.mail.negocio` en
`application.properties` apunta a un correo personal, de forma que un negocio que despliegue sin
definir `BUSINESS_EMAIL` envia sus avisos a ese buzon. Es un defecto de comportamiento del producto
y no encaja en un cambio de documentacion.
