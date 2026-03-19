package com.segovia.peluqueria.cita.dto;

import com.segovia.peluqueria.cita.EstadoCita;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CitaUpdateDTO {

    private Integer usuarioId;

    private Integer servicioId;

    private LocalDateTime fechaHora;

    private EstadoCita estado;
}
