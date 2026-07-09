package com.segovia.peluqueria.peluquero.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PeluqueroRequestDTO {

    @NotBlank(message = "El nombre del peluquero es obligatorio")
    private String nombre;
}
