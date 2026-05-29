package com.segovia.peluqueria.usuario.dto;

import com.segovia.peluqueria.usuario.Rol;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambiarRolRequestDTO {

    @NotNull(message = "El rol es obligatorio")
    private Rol rol;
}
