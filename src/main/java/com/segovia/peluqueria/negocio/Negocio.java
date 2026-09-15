package com.segovia.peluqueria.negocio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Quien es esta peluqueria: como se llama, como se la localiza, que aspecto tiene y a que
 * horas abre. Es la fila unica de la tabla {@code negocio}.
 *
 * <p>Todo esto vivia antes fuera de la base de datos —el horario en
 * {@code application.properties} y el nombre y el contacto escritos en el frontend—, y por
 * eso cambiarlo obligaba a tocar el despliegue o a recompilar la app. Aqui dentro es un
 * formulario del panel, que es lo que hace falta para instalarle el producto a un cliente
 * nuevo sin reconstruir nada.
 *
 * <p><b>El horario de esta tabla es el fijo semanal</b>: a que hora abre y que dias de la
 * semana no abre nunca. Los festivos y los cierres de un dia concreto siguen siendo
 * {@code dias_bloqueados}, que es otra pregunta.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "negocio")
public class Negocio {

    /**
     * Siempre 1. Hay un CHECK en la tabla que lo exige: este backend sirve a una sola
     * peluqueria y conviene que eso este escrito en algun sitio y no solo sobreentendido.
     */
    public static final short ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    /** Frase corta bajo el nombre. Opcional: un negocio puede no tener eslogan. */
    @Column(name = "eslogan", length = 200)
    private String eslogan;

    @Column(name = "telefono", length = 30)
    private String telefono;

    /**
     * Correo de contacto que se le enseña al cliente. No es el remitente de los avisos
     * (eso es {@code MAIL_FROM}, que lo impone el proveedor de correo y no el negocio).
     */
    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "direccion", length = 200)
    private String direccion;

    /** Codigo postal, ciudad y pais, en una linea, tal y como se pinta debajo de la calle. */
    @Column(name = "localidad", length = 120)
    private String localidad;

    /** URL publica del logo. Vacio = se usa el que trae la app empaquetado. */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Color de marca en hexadecimal ({@code #RRGGBB}). Vacio = el del tema por defecto. */
    @Column(name = "color_primario", length = 7)
    private String colorPrimario;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    /**
     * Dias de la semana en los que no se abre nunca: nombres de {@link java.time.DayOfWeek}
     * separados por comas, o cadena vacia si se abre todos los dias. Se guarda como texto
     * porque es una lista corta que solo lee este dominio; quien la necesita pregunta a
     * {@link NegocioService}, que la devuelve ya convertida.
     */
    @Column(name = "dias_cerrados", length = 80, nullable = false)
    private String diasCerrados;
}
