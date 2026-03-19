package com.segovia.peluqueria.cita.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CitaRequestDTO {

    @NotNull(message = "El ID del usuario es obligatorio")
    private Integer usuarioId;

    @NotNull(message = "El ID del servicio es obligatorio")
    private Integer servicioId;

    @NotNull(message = "La fecha y hora de la cita es obligatoria")
    private LocalDateTime fechaHora;
}
