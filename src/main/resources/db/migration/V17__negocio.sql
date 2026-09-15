-- V17: los datos del negocio dejan de ser del despliegue y pasan a la base de datos.
--
-- Hasta aqui el nombre, el contacto y el horario vivian repartidos entre
-- application.properties (peluqueria.horario.*) y constantes escritas en el frontend.
-- Mientras estuvieran ahi no podian ser "de esta peluqueria": cambiarlos era editar el
-- despliegue o recompilar la app, que es exactamente lo que estorba para instalarle esto
-- a un cliente nuevo.
--
-- UNA SOLA FILA, y se fuerza con un CHECK en vez de confiar en que nadie inserte otra.
-- Este backend sirve a una peluqueria: el dia que sirva a varias, esta es la tabla que
-- lleva el negocio_id y el CHECK se cae. Que la fila unica sea explicita es lo que hace
-- que ese dia se note, en vez de que aparezcan dos negocios y gane el primero que salga.
CREATE TABLE negocio (
    id             SMALLINT     PRIMARY KEY,
    nombre         VARCHAR(120) NOT NULL,
    eslogan        VARCHAR(200),
    telefono       VARCHAR(30),
    email          VARCHAR(160),
    direccion      VARCHAR(200),
    localidad      VARCHAR(120),
    logo_url       VARCHAR(500),
    color_primario VARCHAR(7),
    hora_apertura  TIME         NOT NULL,
    hora_cierre    TIME         NOT NULL,
    -- Dias de la semana en los que no se abre nunca, en ingles y separados por comas
    -- (los nombres de java.time.DayOfWeek, igual que la property que sustituye). Vacio
    -- significa que se abre todos los dias. Los festivos y cierres puntuales NO van aqui:
    -- siguen en dias_bloqueados, que es por fecha y no por dia de la semana.
    dias_cerrados  VARCHAR(80)  NOT NULL,
    CONSTRAINT negocio_fila_unica     CHECK (id = 1),
    CONSTRAINT negocio_horario_valido CHECK (hora_apertura < hora_cierre)
);

-- Se siembra con lo que hay hoy: los mismos valores que tenian application.properties y
-- la pantalla de contacto del movil. Desplegar esto no le cambia nada a nadie.
INSERT INTO negocio (
    id, nombre, eslogan, telefono, email, direccion, localidad,
    hora_apertura, hora_cierre, dias_cerrados
) VALUES (
    1,
    'Lalo Segovia · Peluquería',
    NULL,
    '+34 963 12 34 56',
    'hola@lalosegovia.es',
    'Carrer de Colón, 42',
    '46004 València, España',
    '09:00',
    '20:00',
    'SUNDAY'
);
