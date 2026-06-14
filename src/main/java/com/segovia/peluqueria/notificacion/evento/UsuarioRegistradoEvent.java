package com.segovia.peluqueria.notificacion.evento;

/**
 * Se publica cuando se registra un usuario nuevo. Dispara el correo de bienvenida.
 */
public record UsuarioRegistradoEvent(
        String nombre,
        String email
) {
}
