-- V6: Recordatorio de cita (24h antes). Flag para evitar duplicados en el envio.
ALTER TABLE citas ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;
