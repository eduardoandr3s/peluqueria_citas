package com.segovia.peluqueria.cita;

/**
 * Estados de una cita. Los tres ultimos son estados de cierre: la cita ya no se mueve y
 * queda sellada con quien la cerro y cuando (ver {@link Cita}).
 *
 * <p>COMPLETADA es la que cuenta como trabajo hecho. La produccion y la comision, sin
 * embargo, solo suman las completadas que ADEMAS tienen el pago en PAGADO: el dinero se
 * cuenta cuando esta cobrado, y el efectivo entra por el pago manual.
 */
public enum EstadoCita {
    PENDIENTE,
    CONFIRMADA,
    COMPLETADA,
    NO_ASISTIO,
    ANULADA
}
