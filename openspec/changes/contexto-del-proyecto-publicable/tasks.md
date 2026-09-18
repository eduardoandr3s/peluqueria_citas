# Tasks

## 1. Rotar lo expuesto

- [x] 1.1 Rotar en el panel de Stripe la clave secreta de modo de pruebas que aparece en el bloque
      de variables de `AGENTS.md`, y verificar que la clave anterior queda revocada y que la nueva
      arranca la aplicacion en local con los pagos operativos. Tarea manual: no se puede automatizar
      desde el repositorio.

## 2. Sanear AGENTS.md

- [x] 2.1 Eliminar el bloque de valores listo para pegar en la Run Configuration, que contiene la
      clave de Stripe, el secreto de firma de tokens y la credencial de la base de datos, y
      verificar que ninguna linea del fichero contiene ya un valor de credencial.
- [x] 2.2 Vaciar de la tabla de variables de entorno los valores de credencial que quedan, dejando
      solo nombre, propiedad asociada y proposito, y verificar que la tabla sigue listando todas las
      variables que hoy documenta.
- [x] 2.3 Eliminar el parrafo que justifica guardar credenciales en el fichero por estar ignorado, y
      sustituirlo por una nota de que las credenciales de desarrollo viven en la Run Configuration
      del IDE y que este fichero no admite valores. Verificar que la palabra que autorizaba a
      guardarlas ya no aparece.
- [x] 2.4 Sustituir las rutas absolutas de una maquina concreta y el JDK fijado por instrucciones
      independientes de la maquina, y verificar que no queda ninguna ruta que empiece por el
      directorio personal de un usuario.
- [x] 2.5 Generalizar la seccion de despliegue quitando los identificadores del proyecto de hosting,
      el modo de conexion de la base de datos gestionada y el puerto del relay de correo, dejando
      que proveedor cubre cada pieza. Verificar que no queda ningun identificador de recurso.
- [x] 2.6 Sustituir los detalles del cluster local de base de datos por los requisitos minimos
      (motor y version), y verificar que el arranque en local sigue siendo reproducible siguiendo
      solo lo que queda escrito.

## 3. Corregir lo desfasado en AGENTS.md

- [x] 3.1 Reescribir la seccion de tests: los de integracion si arrancan en Linux, el comando por
      defecto pasa a ser la suite completa, y la exclusion de la suite de integracion queda
      reservada a Windows. Verificar ejecutando el comando que quede documentado como el normal y
      comprobando que termina en verde.
- [x] 3.2 Eliminar la nota que declara el arranque de los tests de integracion como problema sin
      resolver, y dejar constancia de la causa real ya corregida: la negociacion de la version de la
      API de Docker, que el `pom.xml` resuelve fijando la version de Testcontainers. Verificar que
      el fichero ya no describe el problema como abierto.
- [x] 3.3 Corregir la version del framework citada en la introduccion para que coincida con la que
      declara el `pom.xml`, y verificar comparando ambos ficheros.
- [x] 3.4 Revisar la seccion de estado y pendientes y retirar lo que ya no es cierto, verificando
      cada punto contra el repositorio antes de conservarlo.

## 4. Publicar AGENTS.md

- [x] 4.1 Revisar el fichero entero buscando credenciales, rutas personales, direcciones de correo e
      identificadores de infraestructura, y verificar que la busqueda no devuelve ninguna
      coincidencia. Esta tarea es el punto de no retorno: no continuar si queda alguna.
- [x] 4.2 Retirar la entrada de `AGENTS.md` de `.gitignore` y verificar que el fichero aparece ya
      como pendiente de anadir en el estado del repositorio.

## 5. Fuente unica de las condiciones de ejecucion de los tests

- [x] 5.1 Sustituir en `README.md` y `README.es.md` la explicacion de como correr los tests por una
      referencia a `AGENTS.md`, conservando los contadores de tests, y verificar que ningun README
      repite ya comandos ni condiciones de entorno.
- [x] 5.2 Comprobar que el comentario del `pom.xml` sobre la version de Testcontainers sigue intacto
      y que sigue siendo el unico sitio que explica el porque tecnico de esa version fijada.

## 6. Rellenar openspec/config.yaml

- [x] 6.1 Escribir el campo de contexto con el stack, la organizacion por dominio, las convenciones
      que no hay que corregir al tocar codigo, donde vive la configuracion del negocio y el hecho de
      que el producto se vende a varios negocios con modulos activables. Verificar que no contiene
      contadores, numeros de migracion ni versiones dentro de la prosa.
- [x] 6.2 Escribir las reglas por artefacto para `proposal`, `specs`, `design` y `tasks`, incluyendo
      el idioma y la ortografia de los artefactos, la obligacion de declarar si una funcionalidad va
      detras de un modulo activable, la de acompanar todo cambio de comportamiento con su test y la
      de actualizar los contadores de los README. Verificar que el fichero sigue siendo YAML valido.
- [x] 6.3 Escribir la guia por operacion para `apply` y `archive` con las convenciones de commit del
      proyecto y el trato de los problemas colaterales, y verificar que el fichero sigue siendo YAML
      valido.
- [x] 6.4 Comprobar que el contexto y las reglas escritas no duplican las condiciones de ejecucion
      de los tests, sino que remiten a `AGENTS.md`.

## 7. Verificacion final

- [x] 7.1 Ejecutar la suite completa de tests y verificar que termina en verde, para confirmar que
      el cambio no ha tocado comportamiento.
- [x] 7.2 Validar el cambio con la herramienta de OpenSpec y verificar que no reporta errores.
- [x] 7.3 Revisar el conjunto de ficheros modificados antes de entregarlos y confirmar que ninguno
      incorpora credenciales ni datos personales.
