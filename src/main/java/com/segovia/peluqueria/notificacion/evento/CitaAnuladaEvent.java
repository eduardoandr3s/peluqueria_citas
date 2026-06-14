package com.segovia.peluqueria.notificacion.evento;

import java.time.LocalDateTime;

/**
 * Se publica cuando se anula una cita (estado ANULADA o eliminacion).
 */
public record CitaAnuladaEvent(
        String clienteNombre,
        String clienteEmail,
        String servicioNombre,
        LocalDateTime fechaHora
) {
}
