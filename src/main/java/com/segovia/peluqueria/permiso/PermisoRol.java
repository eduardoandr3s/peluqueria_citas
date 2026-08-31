package com.segovia.peluqueria.permiso;

import com.segovia.peluqueria.usuario.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Estado de un permiso para un rol. La clave primaria es (rol, clave): un rol no puede
 * tener dos veces el mismo permiso, y no hace falta un id sintetico para una tabla que
 * solo se lee entera.
 *
 * <p>La clave se guarda como texto y no como enum ordinal para que reordenar
 * {@link Permiso} no reasigne permisos a otra cosa. Una fila cuya clave ya no exista en
 * el enum se ignora al leer (ver {@code PermisoService}).
 */
@Entity
@Table(name = "permisos_rol")
@IdClass(PermisoRolId.class)
public class PermisoRol {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 10, nullable = false)
    private Rol rol;

    @Id
    @Column(name = "clave", length = 64, nullable = false)
    private String clave;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    protected PermisoRol() {
    }

    public PermisoRol(Rol rol, Permiso permiso, boolean habilitado) {
        this.rol = rol;
        this.clave = permiso.name();
        this.habilitado = habilitado;
    }

    public Rol getRol() {
        return rol;
    }

    public String getClave() {
        return clave;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }
}
