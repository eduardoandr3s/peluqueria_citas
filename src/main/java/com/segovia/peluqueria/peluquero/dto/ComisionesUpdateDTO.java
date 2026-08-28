package com.segovia.peluqueria.peluquero.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Conjunto completo de excepciones de comision de un peluquero. Se reemplaza entero en vez
 * de tener un endpoint por fila: la pantalla del panel edita la tabla como un bloque y asi
 * borrar una excepcion es simplemente no mandarla.
 */
@Data
public class ComisionesUpdateDTO {

    @NotNull(message = "La lista de comisiones es obligatoria (vacia para borrar todas)")
    private List<@Valid ComisionServicioDTO> comisiones;
}
