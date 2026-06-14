package com.segovia.peluqueria.notificacion.evento;

import java.time.LocalDateTime;

/**
 * Se publica cuando un cliente reserva una cita. El listener envia el aviso por correo
 * despues de que la transaccion haga commit.
 */
public record CitaAgendadaEvent(
        String clienteNombre,
        String clienteEmail,
        String servicioNombre,
        LocalDateTime fechaHora
) {
}
