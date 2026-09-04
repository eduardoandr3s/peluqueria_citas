-- V15: el CV publico del peluquero.
--
-- Todo lo que se anade es material PROFESIONAL y publico: presentacion, especialidades,
-- anios de oficio, una foto de trabajo y el Instagram profesional. Nada sale de la cuenta,
-- asi que GET /api/peluqueros/publicos se puede servir sin token sin filtrar datos
-- personales (el email, el telefono y el usuario_id no viven en esta tabla).
--
-- Todas las columnas son NULLABLE y sin backfill: una ficha sin CV es una ficha que
-- todavia no lo ha rellenado, y hasta que alguien escriba algo el equipo se presenta solo
-- con el nombre, que es lo que ya hacia. Desplegar esto no cambia lo que ve nadie.
ALTER TABLE peluqueros
    ADD COLUMN presentacion       TEXT,
    -- Lista corta separada por comas. Una tabla aparte seria sobreingenieria: no se filtra
    -- ni se agrupa por especialidad, solo se pintan como etiquetas debajo del nombre.
    ADD COLUMN especialidades     VARCHAR(255),
    ADD COLUMN anios_experiencia  INTEGER,
    -- La CLAVE del objeto en el bucket, nunca la URL: la URL se monta al leer, asi que
    -- cambiar de bucket o de proveedor no obliga a migrar ni una fila. Es la convencion del
    -- proyecto entero (servicios, avatares y galeria hacen lo mismo).
    ADD COLUMN foto_clave         VARCHAR(255),
    -- Solo el usuario de Instagram, sin URL y sin arroba: la normaliza el servidor.
    ADD COLUMN instagram          VARCHAR(100);

-- El orden en que se presenta el equipo. NOT NULL con defecto 0 porque siempre hay que
-- poder ordenar: con todo a 0 el desempate lo hace el nombre, que es un orden estable y no
-- el que devuelva la base de datos por casualidad.
ALTER TABLE peluqueros
    ADD COLUMN orden INTEGER NOT NULL DEFAULT 0;
