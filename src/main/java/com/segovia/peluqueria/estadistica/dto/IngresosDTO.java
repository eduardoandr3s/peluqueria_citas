package com.segovia.peluqueria.estadistica.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class IngresosDTO {
    private BigDecimal total;
    private Map<String, BigDecimal> porMetodoPago;
}
