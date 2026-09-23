# Tasks

Las rutas `mobile/...` y los README del frontend son del repositorio del frontend. Las rutas
`src/...` y los README del backend son de este repositorio. Los comandos de tests salen de
`AGENTS.md`.

## 1. Backend: fijar que el personal lee el catalogo

- [x] 1.1 En `src/test/java/com/segovia/peluqueria/integracion/PermisoIntegrationTest.java`, anadir un test que pida `GET /api/servicios` con `tokenLaura` (PELUQUERO, con la matriz apagada por el `setUp`) y compruebe 200 y que `servCorte` esta en la respuesta. Se verifica con `./mvnw test -Dtest=PermisoIntegrationTest` en verde.
- [x] 1.2 En la misma clase, anadir un test que dé de baja un servicio con `DELETE /api/servicios/{id}` y `tokenAdmin`, pida el listado con `tokenLaura` y compruebe que ese servicio ya no esta y `servCorte` si. Se verifica con el mismo comando en verde.

## 2. App movil: la pantalla del catalogo en modo consulta

- [x] 2.1 En `mobile/src/app/servicios/servicios.page.spec.ts`, anadir al `setup` y a `etiquetas` un doble de `AuthService` con `isStaff` (por defecto `false`, sesion de cliente) y un parametro para cambiarlo. Se verifica con `npm test -w @peluqueria/mobile -- --watch=false` desde la raiz del monorepo: los tests que ya habia siguen en verde tras el paso 2.2.
- [x] 2.2 En `mobile/src/app/servicios/servicios.page.ts`, inyectar `AuthService` y exponer `esStaff = computed(() => auth.isStaff())`, con un comentario que diga que la pantalla sirve a las dos areas y que lo que lleve al area de cliente va dentro de `@if (!esStaff())`. En `servicios.page.html`, envolver en ese `@if` el boton «Agendar» y los dos botones de la cabecera. Se verifica con la suite del movil en verde.
- [x] 2.3 Anadir en `servicios.page.spec.ts`, sobre la plantilla, los tests del spec: con sesion de personal no hay ningun «Agendar» ni ningun icono en la cabecera aunque la galeria y el equipo esten encendidos; con sesion de personal cada tarjeta sigue mostrando nombre, descripcion, precio y duracion; con sesion de cliente sigue habiendo «Agendar». Se verifica con la suite del movil en verde, y viendo que los tests del personal fallan si se quita el `@if`.

## 3. App movil: la ruta del area de trabajo

- [x] 3.1 En `mobile/src/app/app.routes.ts`, anadir dentro de `admin` la hija `catalogo`, sin `adminGuard`, que cargue `ServiciosPage` desde `./servicios/servicios.page`, con un comentario que diga por que no es `/admin/servicios` ni `/tabs/servicios`. Se verifica con `npm run build -w @peluqueria/mobile` sin errores.
- [x] 3.2 En `mobile/src/app/app.routes.spec.ts`, anadir tests de que `/admin/catalogo` existe, no lleva `adminGuard`, carga el mismo componente que `/tabs/servicios`, y que `/admin/servicios` sigue llevando `adminGuard`. Se verifica con la suite del movil en verde.

## 4. App movil: la pestana en la barra del peluquero

- [x] 4.1 En `mobile/src/app/admin/admin-tabs.page.html`, anadir entre «Mi produccion» y «Perfil» la pestana «Servicios» con `tab="catalogo"`, `href="/admin/catalogo"` y el icono `cut-outline`, que ya esta registrado, solo cuando `!esAdmin()`. Se verifica con la suite del movil en verde.
- [x] 4.2 En `mobile/src/app/admin/admin-tabs.page.ts` y en su spec, corregir los comentarios que dicen que produccion es la unica pestana de un PELUQUERO aparte de sus citas y su perfil. Se verifica leyendo el diff: no queda ningun comentario que lo afirme.
- [x] 4.3 En `mobile/src/app/admin/admin-tabs.page.spec.ts`, anadir tests sobre la plantilla. La barra de un PELUQUERO es «Citas · Mi produccion · Servicios · Perfil». Sin el modulo de produccion es «Citas · Servicios · Perfil». La de un ADMIN no cambia y lleva un solo «Servicios», que apunta a `/admin/servicios`. La pestana es el unico camino a la pantalla, asi que el test tiene que fallar si se borra. Se verifica con la suite del movil en verde, y viendo que falla si se quita la pestana del HTML.

## 5. Verificacion de conjunto

- [x] 5.1 Ejecutar la suite completa del backend (`./mvnw test`) y anotar el total. Se verifica con 0 fallos.
- [x] 5.2 Ejecutar las dos suites del frontend (`npx ng test --watch=false` y `npm test -w @peluqueria/mobile -- --watch=false`) y la build del movil, y anotar los totales. Se verifica con 0 fallos y la build sin errores.
- [x] 5.3 Comprobar a mano en el navegador, con una sesion de PELUQUERO, que la pestana «Servicios» aparece, se marca al entrar, carga el catalogo sin «Agendar» y la recarga funciona. Con una sesion de ADMIN, comprobar que su barra no ha cambiado. Hace falta el backend en marcha. Si no hay credenciales disponibles, se deja indicado para que lo haga Eduardo. Se verifica con lo observado anotado en la entrega. Hecha a mano por Eduardo el 2026-09-23.

## 6. Documentacion

- [x] 6.1 Actualizar los contadores de tests de los README del backend (`README.md` y `README.es.md`: total, unitarios e integracion) con los totales de 5.1. Se verifica con `grep` de los numeros viejos, que no devuelve nada.
- [x] 6.2 Actualizar los contadores de los README del frontend (`README.md` y `README.es.md`): total de Vitest, fila del movil y la cifra de tests del backend que aparece en la seccion de la API. Se verifica con los totales de 5.1 y 5.2 y un `grep` de los numeros viejos, que no devuelve nada.
- [x] 6.3 En los dos README del frontend, cambiar la frase que dice que un peluquero no ve la pestana de servicios: ahora tiene la de consulta y sigue sin ver la de gestion ni la de usuarios. Anadir a la fila de tests del movil lo que cubren los tests nuevos. Se verifica leyendo las dos versiones, que dicen lo mismo.

## 7. Entrega

- [x] 7.1 No commitear. Entregar a Eduardo la lista de ficheros tocados en cada repositorio y un mensaje propuesto para cada uno, en conventional commits, en espanol sin tildes ni enies y sin `Co-Authored-By`: `test:` para el backend y `feat:` para el frontend. Recordar que los peluqueros no lo veran en la APK hasta reconstruirla. Se verifica con la entrega hecha.
