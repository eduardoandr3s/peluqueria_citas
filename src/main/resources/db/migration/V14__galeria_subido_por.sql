-- V14: cada foto de la galeria sabe quien la subio.
--
-- La columna es NULLABLE a proposito y no se rellena con ningun backfill: una foto sin
-- dueno es "del negocio", que es exactamente lo que son las que ya existen (las subio un
-- administrador cuando esto no existia). Inventarles un dueno seria peor que dejarlas
-- asi: le daria a una persona el derecho a borrar material de la peluqueria.
--
-- ON DELETE SET NULL, no CASCADE: si algun dia se borra la cuenta de un trabajador, sus
-- fotos son trabajo de la peluqueria y se quedan; lo que se pierde es el dueno, no la
-- foto. Borrarlas con la cuenta vaciaria el escaparate sin que nadie lo pidiera.
ALTER TABLE galeria_fotos
    ADD COLUMN subido_por INTEGER REFERENCES usuarios(id_usuario) ON DELETE SET NULL;

-- El filtro real es "las mias", asi que el indice va por dueno.
CREATE INDEX idx_galeria_fotos_subido_por ON galeria_fotos (subido_por);
