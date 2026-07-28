package com.segovia.peluqueria.almacen;

/**
 * Fallo al hablar con el almacen de ficheros. Es un problema del servicio de
 * arriba, no de la peticion del cliente, por eso se traduce a 502 y no a 400.
 */
public class AlmacenException extends RuntimeException {

    public AlmacenException(String mensaje) {
        super(mensaje);
    }

    public AlmacenException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
