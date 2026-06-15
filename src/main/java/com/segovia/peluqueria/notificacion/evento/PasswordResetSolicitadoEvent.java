package com.segovia.peluqueria.notificacion.evento;

/**
 * Se publica cuando un usuario solicita restablecer su contrasena. Dispara el correo
 * con el enlace de recuperacion. Lleva primitivos (no entidades) para el envio async.
 */
public record PasswordResetSolicitadoEvent(
        String nombre,
        String email,
        String enlace
) {
}
