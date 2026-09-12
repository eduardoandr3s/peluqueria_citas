-- V16: Modulos activables. Que hace este negocio y que no.
--
-- Un modulo NO es un permiso, y confundirlos rompe el diseno entero:
--
--   permisos_rol  ->  QUIEN puede hacer X. Es por rol, ESTRECHA y nunca abre, y un ADMIN
--                     no se configura: los tiene todos.
--   modulos       ->  si este negocio hace X EN ABSOLUTO. Es global, QUITA, y quita para
--                     todos, el administrador incluido.
--
-- El orden al comprobar es siempre: primero el modulo, despues el rol, despues el permiso.
-- Un modulo apagado corta antes de llegar al rol; uno encendido no concede nada.
--
-- Igual que permisos_rol, la tabla guarda el ESTADO y no el catalogo: que modulos existen
-- lo dice el enum Modulo del codigo. Por eso no se siembra ninguna fila y la ausencia
-- significa "el valor por defecto del modulo", que es ENCENDIDO. Es al reves que los
-- permisos, y por el mismo motivo: el valor por defecto tiene que ser el comportamiento de
-- antes, y antes de que esto existiera el negocio hacia de todo.
CREATE TABLE modulos (
    clave  VARCHAR(64) PRIMARY KEY,
    activo BOOLEAN     NOT NULL
);
