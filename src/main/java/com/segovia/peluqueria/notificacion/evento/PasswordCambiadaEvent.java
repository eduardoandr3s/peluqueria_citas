package com.segovia.peluqueria.notificacion.evento;

/**
 * Se publica cuando un usuario cambia su contrasena. Dispara el aviso de seguridad por correo.
 */
public record PasswordCambiadaEvent(
        String nombre,
        String email
) {
}
