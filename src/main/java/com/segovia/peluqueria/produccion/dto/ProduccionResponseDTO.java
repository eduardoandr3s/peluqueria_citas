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

    /**
     * Citas completadas y, si el negocio cobra por la aplicacion, tambien cobradas. Lo que
     * cuenta exactamente lo dice {@code exigeCobro}.
     */
    private long serviciosRealizados;
    private BigDecimal importeVendido;

    /**
     * Comision del periodo, o <b>null si el modulo de comisiones esta apagado</b>. Null no
     * es cero: cero seria "trabaja al 0 %" y esto es "aqui no se comisiona". Los
     * porcentajes que ya quedaron congelados en citas cerradas siguen en la base de datos;
     * lo que desaparece es la columna.
     */
    private BigDecimal comision;

    /**
     * Si la produccion exige que la cita este cobrada. Sale del modulo PAGOS: apagado, esto
     * viene en falso y cuentan las citas COMPLETADA a secas. La pantalla lo necesita para
     * decir con que criterio esta sumando, que si no el mismo numero significa dos cosas.
     */
    private boolean exigeCobro;

    /**
     * Trabajo hecho que todavia no esta cobrado. No suma en el importe vendido; se muestra
     * aparte para que no se pierda de vista (tipicamente, efectivo sin registrar).
     *
     * <p>Con los pagos apagados viene a cero, porque ahi no hay nada "sin cobrar": esas
     * citas ya estan contadas arriba.
     */
    private long serviciosSinCobrar;
    private BigDecimal importeSinCobrar;

    private List<LineaProduccionDTO> porServicio;
    private List<LineaProduccionDTO> porMes;
}
