package com.segovia.peluqueria.peluquero.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PeluqueroUpdateDTO {

    private String nombre;

    private Boolean activo;

    @DecimalMin(value = "0.00", message = "La comision no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La comision no puede pasar del 100%")
    private BigDecimal comisionPorcentaje;

    /**
     * Cuenta con la que entra el peluquero. Se envia el id para vincular, y
     * {@code desvincularUsuario} a true para dejar la ficha sin cuenta: un null aqui
     * significa "no lo toques", como en el resto de los campos de este DTO.
     */
    private Integer usuarioId;

    private Boolean desvincularUsuario;
}
