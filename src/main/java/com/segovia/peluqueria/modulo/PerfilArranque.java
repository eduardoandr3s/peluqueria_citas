package com.segovia.peluqueria.modulo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Juegos de modulos preparados para dar de alta una peluqueria nueva.
 *
 * <p>Un perfil no es un estado que se guarde: es un <b>atajo</b> que escribe de una vez lo
 * que si no habria que ir marcando modulo a modulo. Despues de aplicarlo, el negocio no
 * "esta en el perfil X", esta con esos modulos encendidos y se le cambia cualquiera sin
 * salir de nada. Por eso no hay columna que recuerde cual se aplico: seria un dato que
 * empieza a mentir en cuanto alguien toque una casilla.
 *
 * <p>Existen porque los modulos <b>nacen todos encendidos</b>, que es lo correcto para una
 * peluqueria que ya venia funcionando —desplegar un modulo nuevo no debe quitarle nada— y
 * es justo lo contrario de lo que quiere una que acaba de entrar: a esa se le enseña de
 * golpe el producto entero y hay que ir apagandole nueve cosas a mano.
 *
 * <p><b>El pago con tarjeta no entra en ningun perfil salvo TODO</b>, y no es un descuido:
 * Stripe necesita una cuenta y unas claves propias de cada negocio, asi que el primer dia
 * de un cliente nuevo no hay con que cobrar online. Dejarlo encendido seria ofrecerle al
 * cliente final una pasarela que devuelve error.
 */
public enum PerfilArranque {

    /**
     * Agendar y avisar, nada mas. Ni caja, ni escaparate, ni nomina: la peluqueria cobra en
     * el local como siempre y esto solo le lleva la agenda. Es el arranque mas probable de
     * un negocio pequeño que hasta ahora iba con una libreta.
     */
    SOLO_AGENDA(
            "Solo agenda",
            "Citas y recordatorios. Sin cobros, sin escaparate y sin nomina",
            EnumSet.of(Modulo.RECORDATORIOS_EMAIL)),

    /**
     * Lo anterior mas registrar lo que se cobra, y por tanto la produccion de cada
     * profesional. Sin comisiones: el porcentaje es un acuerdo del negocio y ponerle uno
     * por defecto seria inventarselo.
     */
    AGENDA_Y_CAJA(
            "Agenda y caja",
            "Citas, recordatorios, cobro en efectivo o transferencia y produccion por profesional",
            EnumSet.of(
                    Modulo.RECORDATORIOS_EMAIL,
                    Modulo.PAGOS,
                    Modulo.PAGO_EFECTIVO,
                    Modulo.PAGO_TRANSFERENCIA,
                    Modulo.PRODUCCION)),

    /** El producto entero, que es como esta hoy una instalacion recien migrada. */
    TODO(
            "Todo",
            "Todos los modulos encendidos, pasarela de tarjeta incluida",
            EnumSet.allOf(Modulo.class));

    private final String nombre;
    private final String descripcion;
    private final Set<Modulo> encendidos;

    PerfilArranque(String nombre, String descripcion, Set<Modulo> encendidos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.encendidos = Collections.unmodifiableSet(encendidos);
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Los modulos que deja encendidos. <b>Todo lo que no este aqui se apaga</b>: un perfil
     * describe el estado completo y no una suma sobre lo que hubiera antes, que es lo que
     * hace que aplicarlo dos veces de el mismo resultado.
     */
    public Set<Modulo> getEncendidos() {
        return encendidos;
    }
}
