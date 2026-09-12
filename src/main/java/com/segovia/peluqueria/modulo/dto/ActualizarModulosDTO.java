package com.segovia.peluqueria.modulo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Cambios de la pantalla de modulos. Se mandan solo los que cambian y no el catalogo
 * entero, igual que en los permisos: asi dos administradores en pantallas distintas no se
 * pisan el trabajo.
 */
@Data
public class ActualizarModulosDTO {

    @NotNull(message = "Hay que mandar la lista de cambios.")
    @Valid
    private List<CambioModuloDTO> cambios;
}
