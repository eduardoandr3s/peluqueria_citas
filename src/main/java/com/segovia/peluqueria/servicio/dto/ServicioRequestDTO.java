package com.segovia.peluqueria.servicio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServicioRequestDTO {

    @NotBlank(message = "El nombre del servicio es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a 0")
    private BigDecimal precio;

    @NotNull(message = "La duracion es obligatoria")
    @Positive(message = "La duracion debe ser mayor a 0 minutos")
    private Integer duracion;
}
