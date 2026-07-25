package com.segovia.peluqueria.calendario.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DiaBloqueadoRequestDTO {

    @NotNull(message = "La fecha a bloquear es obligatoria")
    private LocalDate fecha;

    @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
    private String motivo;
}
