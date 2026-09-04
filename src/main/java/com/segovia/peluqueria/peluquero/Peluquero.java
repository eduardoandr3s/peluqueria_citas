package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.usuario.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ficha de un profesional.
 *
 * <p>La cuenta es opcional. Una ficha sin usuario es un peluquero por el que agenda el
 * admin y que no entra a la aplicacion; en cuanto se le enlaza una cuenta con rol
 * PELUQUERO, pasa a ver sus citas y su produccion. Al reves tambien vale: una cuenta con
 * rol PELUQUERO sin ficha no ve ninguna cita, porque no hay nada asignado a ella.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "peluqueros")
public class Peluquero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_peluquero")
    private Integer idPeluquero;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean activo = true;

    /** Cuenta con la que entra, o null si es una ficha por la que agenda el admin. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    /**
     * Porcentaje de comision por defecto sobre el importe de los servicios que realiza.
     * Las excepciones por servicio van en {@link ComisionServicio} y ganan a este valor.
     */
    @Column(name = "comision_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal comisionPorcentaje = BigDecimal.ZERO;

    // ---- CV publico ----
    //
    // Material profesional, no datos de la cuenta: es lo que se sirve sin token en
    // GET /api/peluqueros/publicos para que el cliente elija con quien quiere agendar.
    // Todo nullable: una ficha sin CV se presenta solo con el nombre, como antes.

    /** El "sobre mi", texto libre. */
    @Column(columnDefinition = "TEXT")
    private String presentacion;

    /**
     * Especialidades separadas por comas. Se guarda como cadena porque no se filtra ni se
     * agrupa por ellas: solo se pintan como etiquetas. Los DTO las publican ya troceadas,
     * para que ni el panel ni la app tengan que partir la cadena cada uno a su manera.
     */
    @Column(length = 255)
    private String especialidades;

    @Column(name = "anios_experiencia")
    private Integer aniosExperiencia;

    /**
     * Clave del objeto en el bucket, nunca la URL. La URL se monta al leer, asi que
     * cambiar de bucket o de proveedor no obliga a migrar filas.
     */
    @Column(name = "foto_clave", length = 255)
    private String fotoClave;

    /** Usuario de Instagram, sin arroba y sin URL: el servidor lo normaliza al guardar. */
    @Column(length = 100)
    private String instagram;

    /**
     * Orden en que se presenta el equipo al cliente. Con todo a 0 el desempate lo hace el
     * nombre, para que el listado no dependa de lo que devuelva la base de datos.
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer orden = 0;
}
