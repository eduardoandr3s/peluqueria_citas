package com.segovia.peluqueria.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "citas")
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_cita;

    //muchas citas pueden ser de un mismo usuario, pero una cita solo puede ser de un usuario
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // muchas citas pueden ser de un mismo servicio, pero una cita solo puede ser de un servicio
    @ManyToOne
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    // fecha y hora de la cita
    @Column(nullable = false)
    private LocalDateTime fecha_hora;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private EstadoCita estado; // PENDIENTE, CONFIRMADA, ANULADA
}
