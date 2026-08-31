package com.segovia.peluqueria.permiso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.segovia.peluqueria.usuario.Rol;

/** Una casilla de la matriz. La clave se valida contra el enum en el servicio. */
@Data
public class CambioPermisoDTO {

    @NotNull(message = "El rol es obligatorio.")
    private Rol rol;

    @NotBlank(message = "La clave del permiso es obligatoria.")
    private String clave;

    @NotNull(message = "Hay que decir si el permiso queda habilitado.")
    private Boolean habilitado;
}
