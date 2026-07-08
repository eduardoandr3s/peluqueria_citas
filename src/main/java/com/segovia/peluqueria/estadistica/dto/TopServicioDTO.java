package com.segovia.peluqueria.estadistica.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopServicioDTO {
    private String nombre;
    private long total;
}
