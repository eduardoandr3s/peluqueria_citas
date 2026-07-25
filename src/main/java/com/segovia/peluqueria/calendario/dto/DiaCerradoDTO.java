package com.segovia.peluqueria.calendario.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/**
 * Un dia en el que no se puede agendar, con el motivo listo para mostrar al cliente.
 * Unifica los dos origenes de cierre: el dia de la semana (domingo) y los dias
 * bloqueados a mano por el administrador (festivos, cierres puntuales).
 */
@Data
@AllArgsConstructor
public class DiaCerradoDTO {
    private LocalDate fecha;
    private String motivo;
}
