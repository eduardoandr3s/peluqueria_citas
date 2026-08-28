package com.segovia.peluqueria.produccion.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Una fila del desglose de produccion. La misma forma sirve para el desglose por servicio
 * y para el mensual: cambia lo que hay en {@code etiqueta} (el nombre del servicio o el
 * mes en formato YYYY-MM), no la aritmetica.
 */
@Data
public class LineaProduccionDTO {

    private String etiqueta;
    private long servicios;
    private BigDecimal importe;
    private BigDecimal comision;

    public LineaProduccionDTO(String etiqueta, long servicios, BigDecimal importe, BigDecimal comision) {
        this.etiqueta = etiqueta;
        this.servicios = servicios;
        this.importe = importe;
        this.comision = comision;
    }
}
