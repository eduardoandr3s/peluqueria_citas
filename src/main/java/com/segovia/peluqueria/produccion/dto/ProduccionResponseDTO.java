package com.segovia.peluqueria.produccion.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Produccion de un peluquero en un rango de fechas. */
@Data
public class ProduccionResponseDTO {

    private Integer idPeluquero;
    private String nombre;
    private LocalDate desde;
    private LocalDate hasta;

    /** Citas completadas Y cobradas: es lo que cuenta como vendido. */
    private long serviciosRealizados;
    private BigDecimal importeVendido;
    private BigDecimal comision;

    /**
     * Trabajo hecho que todavia no esta cobrado. No suma en el importe vendido; se muestra
     * aparte para que no se pierda de vista (tipicamente, efectivo sin registrar).
     */
    private long serviciosSinCobrar;
    private BigDecimal importeSinCobrar;

    private List<LineaProduccionDTO> porServicio;
    private List<LineaProduccionDTO> porMes;
}
