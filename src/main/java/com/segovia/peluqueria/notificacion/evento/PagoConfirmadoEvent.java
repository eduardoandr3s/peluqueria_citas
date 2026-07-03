package com.segovia.peluqueria.notificacion.evento;

import java.time.LocalDateTime;

/**
 * Se publica cuando se confirma el pago de una cita, tanto si llega por el webhook de Stripe
 * como si lo registra el administrador a mano. El listener envia el aviso por correo despues
 * de que la transaccion haga commit.
 */
public record PagoConfirmadoEvent(
        String clienteNombre,
        String clienteEmail,
        String servicioNombre,
        LocalDateTime fechaHora
) {
}
