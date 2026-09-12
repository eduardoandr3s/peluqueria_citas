package com.segovia.peluqueria.modulo;

/**
 * Se ha pedido algo que este negocio no hace porque su modulo esta apagado.
 *
 * <p>Se traduce a <b>409 Conflict</b> y no a 403 ni a 404, y la diferencia importa:
 * 403 es "tu no puedes", que es lo que dicen los roles y los permisos, y aqui no puede
 * nadie; 404 haria pensar que el backend desplegado es viejo y no tiene esa ruta. Un 409
 * que nombra el modulo es ademas lo que permite al frontend decir "esta peluqueria no
 * cobra por la app" en vez de soltar un error generico.
 */
public class ModuloDesactivadoException extends RuntimeException {

    private final transient Modulo modulo;

    public ModuloDesactivadoException(Modulo modulo) {
        super("El modulo " + modulo.getNombre() + " esta desactivado en este negocio.");
        this.modulo = modulo;
    }

    public Modulo getModulo() {
        return modulo;
    }
}
