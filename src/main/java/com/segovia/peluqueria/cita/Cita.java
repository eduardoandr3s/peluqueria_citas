package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.usuario.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una cita.
 *
 * <p>Los campos de cierre (todo lo que va de {@code precioAplicado} hacia abajo) solo se
 * rellenan cuando la cita llega a un estado final. El precio y el porcentaje de comision
 * se COPIAN aqui en ese momento en vez de leerse del servicio y de la ficha del
 * peluquero: si no, subir la tarifa de un corte cambiaria la produccion y las comisiones
 * ya liquidadas de los meses anteriores.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "citas")
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Integer idCita;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @ManyToOne
    @JoinColumn(name = "peluquero_id")
    private Peluquero peluquero;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private EstadoCita estado;

    @Column(name = "recordatorio_enviado", nullable = false)
    private Boolean recordatorioEnviado = false;

    /** Importe cobrado por el servicio, congelado al cerrar la cita. */
    @Column(name = "precio_aplicado", precision = 10, scale = 2)
    private BigDecimal precioAplicado;

    /** Comision del peluquero sobre ese importe, congelada al cerrar la cita. */
    @Column(name = "comision_porcentaje_aplicado", precision = 5, scale = 2)
    private BigDecimal comisionPorcentajeAplicado;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** Que paso, en palabras de quien cerro la cita. */
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    /**
     * Si al anular se avisó al cliente por telefono o en persona. El email automatico se
     * manda igual: esto registra que hubo contacto humano, que es lo que el negocio
     * necesita saber cuando el cliente reclama.
     */
    @Column(name = "cliente_contactado", nullable = false)
    private Boolean clienteContactado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrada_por")
    private Usuario cerradaPor;
}
