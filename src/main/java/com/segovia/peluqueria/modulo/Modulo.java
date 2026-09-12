package com.segovia.peluqueria.modulo;

/**
 * Catalogo cerrado de modulos activables: las partes del producto que un negocio puede
 * no usar. Que modulos existen lo dice este enum; la tabla {@code modulos} solo guarda
 * cual esta apagado.
 *
 * <p><b>Un modulo no es un permiso.</b> Un {@link com.segovia.peluqueria.permiso.Permiso}
 * responde a "quien puede hacer X" y nunca alcanza al ADMIN. Un modulo responde a "este
 * negocio hace X en absoluto", y apagado <b>tambien desaparece para el administrador</b>.
 * Esa es la diferencia que hay que tener presente al tocar esto: son dos mecanismos con la
 * misma forma y semantica opuesta.
 *
 * <p>El orden de las tres preguntas, y conviene no alterarlo:
 * <ol>
 *   <li>Esta el modulo encendido? Si no, aqui eso no existe (para todos).
 *   <li>Lo permite el rol? Eso lo decide SecurityConfig, como siempre.
 *   <li>Lo permite el permiso? Eso lo decide PermisoService, que solo estrecha.
 * </ol>
 *
 * <p><b>Todos nacen encendidos</b>, al reves que los permisos. El valor por defecto es
 * siempre el comportamiento de antes, y antes de que los modulos existieran el negocio
 * hacia de todo: desplegar esto no debe apagarle nada a nadie.
 */
public enum Modulo {

    /**
     * Comision de los peluqueros: el porcentaje de la ficha, las excepciones por servicio
     * y la columna de comision en produccion.
     *
     * <p>Apagarlo no borra nada ni reescribe el historico: los porcentajes guardados se
     * quedan y una cita ya cerrada conserva su {@code comision_porcentaje_aplicado}. Lo
     * que se apaga es lo que se ofrece y se calcula de aqui en adelante.
     */
    COMISIONES("Comisiones", "Porcentaje por peluquero, excepciones por servicio y comision en produccion"),

    /**
     * Cobrar por la aplicacion. Es el padre de los tres medios de pago: apagarlo los apaga
     * de hecho, y la peluqueria pasa a cobrar solo en el local sin registrarlo aqui.
     *
     * <p><b>Apagarlo cambia lo que significa "produccion".</b> Produccion es "realizada Y
     * cobrada", asi que sin pagos nada llegaria nunca a PAGADO y todos los totales serian
     * cero. Con PAGOS apagado, produccion cuenta las citas COMPLETADA a secas. Ver
     * {@code ProduccionService}.
     */
    PAGOS("Pagos", "Cobrar las citas desde la aplicacion"),

    /** Pasarela de Stripe. El caso realista de apagarlo: cobrar en el local y no online. */
    PAGO_TARJETA("Pago con tarjeta", "Cobro online con tarjeta a traves de Stripe", PAGOS),

    /** Cobro en efectivo, registrado a mano. Quien puede registrarlo lo decide el permiso. */
    PAGO_EFECTIVO("Pago en efectivo", "Registrar cobros en efectivo", PAGOS),

    /** Cobro por transferencia, registrado a mano. */
    PAGO_TRANSFERENCIA("Pago por transferencia", "Registrar cobros por transferencia", PAGOS);

    private final String nombre;
    private final String descripcion;
    private final Modulo padre;

    Modulo(String nombre, String descripcion) {
        this(nombre, descripcion, null);
    }

    Modulo(String nombre, String descripcion, Modulo padre) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.padre = padre;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** El modulo del que cuelga, o null si es de primer nivel. */
    public Modulo getPadre() {
        return padre;
    }

    public boolean esHijo() {
        return padre != null;
    }
}
