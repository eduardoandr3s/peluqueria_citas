# Design

## Context

Ver `proposal.md` - Why para la motivacion.

El estado del que se parte, ya verificado:

- `openspec/config.yaml` existe con el esquema `spec-driven` y todo lo demas comentado.
- `AGENTS.md` esta listado en `.gitignore` y no tiene ningun commit. `.idea/` tampoco esta
  rastreado, y la clave de Stripe no aparece en el historial. Nada se ha filtrado todavia.
- Las variables de entorno de desarrollo viven en la Run Configuration del IDE, dentro de `.idea/`.
  El bloque de valores que hay en `AGENTS.md` es una copia por comodidad, no la fuente.
- Los tests de integracion arrancan en local: la suite entera pasa en verde en menos de un minuto.
  En Windows siguen sin arrancar.

La restriccion que da forma a todo el cambio es que `AGENTS.md` deja de ser privado. Todo el reparto
anterior de informacion se apoyaba en que ese fichero no salia de la maquina.

## Goals / Non-Goals

**Goals:**

- Que cada dato sobre como se trabaja en el proyecto tenga un unico sitio donde vive.
- Que `AGENTS.md` pueda publicarse sin exponer credenciales, rutas personales ni identificadores de
  la infraestructura de produccion.
- Que la informacion publicada no dependa de una maquina concreta ni caduque sola.

**Non-Goals:**

- No se automatiza la deteccion de secretos. No se incorpora ningun escaner ni gancho de pre-commit;
  eso es un cambio propio si algun dia interesa.
- No se reescribe el historial de git. No hace falta.
- No se unifica la documentacion con el repositorio del frontend. Cada repositorio mantiene la suya.

## Decisions

### El reparto entre `AGENTS.md` y `config.yaml` pasa a ser por proposito, no por sensibilidad

Mientras `AGENTS.md` era privado, la linea natural era la sensibilidad: lo secreto dentro, lo demas
en el repositorio. Al publicarse los dos ficheros esa linea desaparece y hace falta otra.

Se reparte por a quien sirve cada cosa: `AGENTS.md` responde a como se arranca, se configura y se
prueba esto; `config.yaml` responde a como se decide y se escribe codigo aqui, y es lo que el flujo
de trabajo inyecta al redactar artefactos.

Alternativa descartada: volcar todo en `config.yaml` y borrar `AGENTS.md`. Se descarta porque
`config.yaml` solo lo lee la herramienta, mientras que `AGENTS.md` es lo primero que abre una
persona que llega al repositorio.

### Las credenciales de desarrollo se quedan solo en la Run Configuration del IDE

Ya viven ahi, y ese directorio esta fuera del control de versiones. `AGENTS.md` pasa a listar los
nombres de las variables y para que sirve cada una, nunca valores.

Alternativas consideradas:

- Un fichero local ignorado con el bloque de valores listo para pegar. Mantiene la comodidad, pero
  reintroduce exactamente el patron que causo el problema: un sitio con secretos que un descuido de
  `.gitignore` publica.
- Un fichero de ejemplo con los nombres y los valores vacios. Es el patron habitual, pero aqui
  duplicaria la tabla de variables que ya va en `AGENTS.md` y no encaja con arrancar desde el IDE.

### La fuente unica de las condiciones de ejecucion de los tests es `AGENTS.md`

Los README pasan a referenciarla en vez de repetirla, y `config.yaml` tampoco la duplica.

El `pom.xml` es la excepcion deliberada: conserva su comentario sobre por que la version de
Testcontainers esta fijada. No es duplicacion sino otra cosa distinta, el porque tecnico pegado a la
linea que lo provoca, y es justo lo unico que no se quedo obsoleto cuando el entorno cambio. Ese
contraste es la leccion del cambio: lo que vive lejos del codigo que describe se pudre.

### `config.yaml` no contiene datos que caduquen

Quedan fuera los contadores de tests, los numeros de migracion y los numeros de version dentro de la
prosa. Son ciertos el dia que se escriben y nadie los revisa despues. Las convenciones y las reglas
si son estables y son lo que aporta valor al redactar artefactos.

### La clave de Stripe se rota aunque no se haya filtrado

Es de modo de pruebas y nunca ha llegado al repositorio, asi que el riesgo actual es bajo. Aun asi
lleva tiempo en claro en disco y en cualquier copia de seguridad, y el coste de rotarla es minimo.
Se rota antes de tocar nada mas, para que el resto del trabajo no dependa de recordar hacerlo.

## Risks / Trade-offs

- Queda dentro de `AGENTS.md` algun dato sensible que la revision no detecta, y se publica al
  commitear. -> El saneado se hace y se verifica antes de retirar la entrada de `.gitignore`, de
  forma que un commit accidental a mitad del trabajo no pueda publicar el fichero sin sanear.
- La costumbre vuelve: alguien pega credenciales en un fichero que ahora es publico. -> Desaparece
  el parrafo que hoy autoriza a guardarlas ahi, y el fichero dice de forma explicita que no admite
  valores, solo nombres de variables.
- Al generalizar la seccion de despliegue se pierde detalle util para operar. -> El detalle
  identificable vive en los paneles de los proveedores, que es donde se opera de verdad; en el
  fichero queda que proveedor cubre cada pieza.
- La fuente unica se vuelve a duplicar con el tiempo. -> Los README apuntan en lugar de repetir, asi
  que una divergencia futura exige reintroducir texto a proposito.
- Publicar `AGENTS.md` expone decisiones internas del proyecto. -> Es el objetivo buscado: el
  fichero pasa a ser parte de la carta de presentacion del repositorio.

## Migration Plan

El orden importa, porque el fichero solo esta protegido mientras siga ignorado:

1. Rotar la clave de Stripe en su panel.
2. Sanear `AGENTS.md` y corregir lo que esta desfasado.
3. Verificar que no quedan credenciales ni rutas personales dentro.
4. Solo entonces, retirar la entrada de `.gitignore`.
5. Ajustar los README y rellenar `config.yaml`.

Marcha atras: si algo se publica por error, basta con volver a ignorar el fichero y rotar lo que
haya quedado expuesto. Reescribir el historial solo seria necesario si llega a commitearse un
secreto, que es precisamente lo que evita el orden anterior.
