package com.segovia.peluqueria.produccion.dto;

import lombok.Data;

import java.math.BigDecimal;

/** Una fila de la comparativa de la plantilla. */
@Data
public class ProduccionPeluqueroDTO {

    private Integer idPeluquero;
    private String nombre;
    private long serviciosRealizados;
    private BigDecimal importeVendido;
    private BigDecimal comision;

    public ProduccionPeluqueroDTO(Integer idPeluquero, String nombre, long serviciosRealizados,
                                  BigDecimal importeVendido, BigDecimal comision) {
        this.idPeluquero = idPeluquero;
        this.nombre = nombre;
        this.serviciosRealizados = serviciosRealizados;
        this.importeVendido = importeVendido;
        this.comision = comision;
    }
}
