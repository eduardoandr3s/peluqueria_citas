package com.segovia.peluqueria.exception;

/**
 * La peticion es correcta, pero el estado actual del recurso no permite la operacion.
 * Se traduce a 409 (ver {@code GlobalExceptionHandler}).
 *
 * <p>Existe en vez de reutilizar {@link ConflictoHorarioException} porque esa habla de
 * solapes de agenda: usarla aqui dejaria en el log lineas que mienten, del estilo
 * «Conflicto de horario: el pago 5 no esta cobrado».
 */
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
