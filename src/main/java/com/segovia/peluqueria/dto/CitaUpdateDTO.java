package com.segovia.peluqueria.dto;

import com.segovia.peluqueria.model.EstadoCita;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CitaUpdateDTO {

    private Integer usuarioId;

    private Integer servicioId;

    private LocalDateTime fechaHora;

    private EstadoCita estado;
}
