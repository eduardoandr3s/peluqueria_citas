-- V13: Permisos por rol configurables desde el panel.
--
-- El rol sigue decidiendo QUE PUEDE HACER una cuenta; esta tabla solo afina dentro de lo
-- que el rol ya consiente. La regla es que un permiso ESTRECHA y nunca abre: encenderlo
-- no concede nada que la regla de SecurityConfig prohiba. Si algun dia un flag es lo
-- unico que separa a un rol de una accion, el diseno se rompio.
--
-- La tabla guarda el ESTADO, no el catalogo: que permisos existen lo dice el enum Permiso
-- del codigo. Por eso no se siembra ninguna fila aqui. La ausencia de fila significa "el
-- valor por defecto del permiso", asi que anadir un permiso nuevo al enum no necesita
-- migracion y desplegarlo no cambia el comportamiento de nadie.
--
-- 'rol' es el nombre del enum, no una FK, y mantiene el VARCHAR(10) de usuarios.rol.
CREATE TABLE permisos_rol (
    rol        VARCHAR(10) NOT NULL,
    clave      VARCHAR(64) NOT NULL,
    habilitado BOOLEAN     NOT NULL,
    PRIMARY KEY (rol, clave)
);
