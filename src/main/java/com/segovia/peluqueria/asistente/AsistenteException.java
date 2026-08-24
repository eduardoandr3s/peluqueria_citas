package com.segovia.peluqueria.asistente;

/**
 * Fallo al hablar con el proveedor del modelo. Es un problema del servicio de arriba, no de
 * la peticion del cliente, por eso se traduce a 503 y no a 400.
 *
 * <p>El caso mas probable en produccion no es una caida: es <strong>haber agotado la cuota
 * gratuita</strong> del dia. Se distingue con {@link #isCuotaAgotada()} porque son dos
 * mensajes distintos para el cliente: uno pide reintentar, el otro dice que el asistente no
 * esta disponible hoy y remite al telefono.
 */
public class AsistenteException extends RuntimeException {

    private final boolean cuotaAgotada;

    public AsistenteException(String mensaje, Throwable causa, boolean cuotaAgotada) {
        super(mensaje, causa);
        this.cuotaAgotada = cuotaAgotada;
    }

    public boolean isCuotaAgotada() {
        return cuotaAgotada;
    }
}
