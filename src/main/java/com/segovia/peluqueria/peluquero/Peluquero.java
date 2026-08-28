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
}
