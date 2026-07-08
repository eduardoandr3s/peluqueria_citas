package com.segovia.peluqueria.estadistica.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CitasPorEstadoDTO {
    private String estado;
    private long total;
}
