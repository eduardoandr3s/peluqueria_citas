package com.segovia.peluqueria.permiso;

import com.segovia.peluqueria.usuario.Rol;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta de {@link PermisoRol}. */
public class PermisoRolId implements Serializable {

    private Rol rol;
    private String clave;

    public PermisoRolId() {
    }

    public PermisoRolId(Rol rol, String clave) {
        this.rol = rol;
        this.clave = clave;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PermisoRolId otro)) {
            return false;
        }
        return rol == otro.rol && Objects.equals(clave, otro.clave);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rol, clave);
    }
}
