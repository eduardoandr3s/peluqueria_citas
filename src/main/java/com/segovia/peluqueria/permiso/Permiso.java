package com.segovia.peluqueria.permiso;

import com.segovia.peluqueria.usuario.Rol;

import java.util.Set;

/**
 * Catalogo cerrado de permisos configurables. Que permisos existen lo dice este enum; la
 * tabla permisos_rol solo guarda cual esta encendido. Es a proposito: con claves sueltas
 * en base de datos, una fila mal escrita seria un permiso que nadie concede nunca y nadie
 * detecta.
 *
 * <p><b>La regla que sujeta todo esto: un permiso ESTRECHA, nunca abre.</b> Antes de
 * consultarlo ya ha pasado la regla de rol de SecurityConfig, asi que encender un flag no
 * puede conceder nada que el rol tenga prohibido. Por eso {@link #aplicableA} nunca
 * incluye a ADMIN: un administrador no se configura, los tiene todos por rol.
 *
 * <p>El valor por defecto es el comportamiento de antes de que el permiso existiera. Asi
 * anadir uno nuevo y desplegarlo no cambia lo que puede hacer nadie hasta que un
 * administrador lo encienda a mano.
 */
public enum Permiso {

    /**
     * Cobrar en efectivo una cita de su agenda. Apagado por defecto porque hasta ahora la
     * caja era solo del ADMIN, y un despliegue no debe cambiar quien la toca. Encenderlo
     * es lo que permite al peluquero cerrar su circuito: sin cobro no hay produccion.
     */
    PAGO_MANUAL_REGISTRAR(
            "Registrar cobros en efectivo de sus propias citas",
            Set.of(Rol.PELUQUERO),
            false),

    /**
     * Mover de fecha una cita de su agenda. Apagado por defecto: reprogramar descuadra el
     * hueco de otro companero, asi que por defecto lo sigue haciendo quien ve la agenda
     * entera.
     */
    CITA_REPROGRAMAR(
            "Cambiar la fecha de las citas de su agenda",
            Set.of(Rol.PELUQUERO),
            false),

    /**
     * Subir fotos a la galeria de trabajos. Apagado por defecto, como todos: la galeria era
     * del ADMIN y desplegar esto no cambia quien publica en el escaparate.
     */
    GALERIA_SUBIR(
            "Subir fotos a la galeria de trabajos",
            Set.of(Rol.PELUQUERO),
            false),

    /**
     * Cambiar el titulo o borrar las fotos que subio el mismo. No alcanza a las de otro ni
     * a las del negocio (las que no tienen dueno), que necesitan
     * {@link #GALERIA_EDITAR_AJENA}.
     */
    GALERIA_EDITAR_PROPIA(
            "Editar y borrar sus propias fotos de la galeria",
            Set.of(Rol.PELUQUERO),
            false),

    /**
     * Tocar fotos que no son suyas, incluidas las del negocio. Es el que conviene dejar
     * apagado: fue justo lo que se pidio evitar, que un trabajador quite el trabajo de
     * otro.
     */
    GALERIA_EDITAR_AJENA(
            "Editar y borrar fotos de la galeria subidas por otros",
            Set.of(Rol.PELUQUERO),
            false),

    /**
     * Cambiar el sitio de una foto en la rejilla. Va aparte de los dos anteriores porque
     * reordenar no es editar "una" foto: mover la propia renumera las de todos, asi que el
     * permiso vale para cualquier foto y no depende del dueno.
     */
    GALERIA_ORDENAR(
            "Reordenar la rejilla de la galeria",
            Set.of(Rol.PELUQUERO),
            false);

    private final String descripcion;
    private final Set<Rol> aplicableA;
    private final boolean porDefecto;

    Permiso(String descripcion, Set<Rol> aplicableA, boolean porDefecto) {
        this.descripcion = descripcion;
        this.aplicableA = aplicableA;
        this.porDefecto = porDefecto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Roles a los que se les puede configurar. Nunca contiene ADMIN ni USER. */
    public Set<Rol> getAplicableA() {
        return aplicableA;
    }

    public boolean isPorDefecto() {
        return porDefecto;
    }

    public boolean aplicaA(Rol rol) {
        return aplicableA.contains(rol);
    }
}
