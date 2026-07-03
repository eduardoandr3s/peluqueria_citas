-- Idempotencia de los webhooks de Stripe: cada evento procesado queda registrado por su id
-- (evt_...). Stripe reintenta la entrega y puede enviar el mismo evento varias veces; si el id
-- ya existe, la aplicacion responde 200 sin reprocesar.
CREATE TABLE stripe_evento (
    id_evento       VARCHAR(255) PRIMARY KEY,
    tipo            VARCHAR(100) NOT NULL,
    fecha_recepcion TIMESTAMP    NOT NULL
);
