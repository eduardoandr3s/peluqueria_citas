package com.segovia.peluqueria.calendario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Dia concreto en el que la peluqueria no abre (festivo, vacaciones, cierre puntual).
 * Los cierres fijos por dia de la semana (domingo) viven en la configuracion del
 * horario, no en esta tabla.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dias_bloqueados")
public class DiaBloqueado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dia_bloqueado")
    private Integer idDiaBloqueado;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    /** Texto libre que se muestra al cliente ("Reyes", "Vacaciones"). Opcional. */
    @Column(length = 200)
    private String motivo;
}
