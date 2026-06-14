package com.segovia.peluqueria.notificacion.evento;

import java.time.LocalDateTime;

/**
 * Se publica cuando se reprograma una cita (cambia fecha/hora o servicio).
 */
public record CitaModificadaEvent(
        String clienteNombre,
        String clienteEmail,
        String servicioNombre,
        LocalDateTime fechaHora
) {
}
