package com.segovia.peluqueria.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServicioUpdateDTO {

    private String nombre;

    private String descripcion;

    @Positive(message = "El precio debe ser mayor a 0")
    private BigDecimal precio;

    @Positive(message = "La duracion debe ser mayor a 0 minutos")
    private Integer duracion;
}
