package com.segovia.peluqueria.exception;

/**
 * Se lanza cuando un refresh token no es valido: inexistente, caducado, ya rotado (reuso)
 * o invalidado por un cambio de credenciales. El handler global la mapea a 401.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
