package com.segovia.peluqueria.permiso.dto;

import com.segovia.peluqueria.permiso.Permiso;
import com.segovia.peluqueria.usuario.Rol;
import lombok.Data;

import java.util.Map;

/**
 * Una fila de la matriz de permisos. Solo lleva los roles a los que ese permiso se le
 * puede configurar, asi que el panel pinta una casilla por cada entrada de {@code roles}
 * y no necesita saber la regla de que ADMIN no se configura.
 */
@Data
public class PermisoDTO {

    private String clave;
    private String descripcion;
    private Map<Rol, Boolean> roles;

    public PermisoDTO(Permiso permiso, Map<Rol, Boolean> roles) {
        this.clave = permiso.name();
        this.descripcion = permiso.getDescripcion();
        this.roles = roles;
    }
}
