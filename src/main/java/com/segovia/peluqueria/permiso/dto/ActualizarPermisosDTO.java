package com.segovia.peluqueria.permiso.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Cambios de la matriz. Se mandan solo las casillas que cambian, no la matriz entera: asi
 * dos administradores tocando pantallas distintas no se pisan el trabajo el uno al otro.
 */
@Data
public class ActualizarPermisosDTO {

    @NotNull(message = "Hay que mandar la lista de cambios.")
    @Valid
    private List<CambioPermisoDTO> cambios;
}
