package com.segovia.peluqueria.modulo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Un modulo que se enciende o se apaga. La clave se valida contra el enum en el servicio. */
@Data
public class CambioModuloDTO {

    @NotBlank(message = "La clave del modulo es obligatoria.")
    private String clave;

    @NotNull(message = "Hay que decir si el modulo queda activo.")
    private Boolean activo;
}
