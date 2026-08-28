package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.servicio.Servicio;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Excepcion a la comision por defecto de un peluquero para un servicio concreto.
 *
 * <p>Existe porque en una peluqueria no se comisiona igual un corte que un tinte, y
 * porque la alternativa —un porcentaje unico por persona— obligaria a inventar medias.
 * Si no hay fila para el par (peluquero, servicio) se aplica
 * {@link Peluquero#getComisionPorcentaje()}.
 */
@Getter
@Setter
@Entity
@Table(name = "comisiones_servicio")
public class ComisionServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comision")
    private Integer idComision;

    @ManyToOne(optional = false)
    @JoinColumn(name = "peluquero_id", nullable = false)
    private Peluquero peluquero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;
}
