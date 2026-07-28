-- V9: Foto del catalogo para cada servicio.
-- Se guarda la CLAVE del objeto en el almacen (p.ej. "12/uuid.jpg"), no la URL
-- completa: asi cambiar de bucket, de proveedor o pasar el bucket a privado es
-- un cambio de configuracion y no una migracion de datos.
ALTER TABLE servicios
    ADD COLUMN imagen_clave VARCHAR(255);
