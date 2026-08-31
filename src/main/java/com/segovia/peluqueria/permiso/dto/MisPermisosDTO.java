package com.segovia.peluqueria.permiso.dto;

import com.segovia.peluqueria.usuario.Rol;
import lombok.Data;

import java.util.Set;

/**
 * Lo que la cuenta autenticada tiene concedido, para que el frontend pinte u oculte.
 * Ocultar un boton no es seguridad: quien decide sigue siendo el servicio.
 */
@Data
public class MisPermisosDTO {

    private Rol rol;
    private Set<String> permisos;

    public MisPermisosDTO(Rol rol, Set<String> permisos) {
        this.rol = rol;
        this.permisos = permisos;
    }
}
