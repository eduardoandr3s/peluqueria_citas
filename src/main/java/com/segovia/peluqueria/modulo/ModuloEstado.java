package com.segovia.peluqueria.modulo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Estado guardado de un modulo. La clave primaria es la clave del enum: la tabla guarda
 * el estado, no el catalogo, asi que no hay id sintetico ni nada que mantener al anadir un
 * modulo nuevo.
 *
 * <p>La clave se guarda como texto y no como ordinal para que reordenar {@link Modulo} no
 * reasigne el estado a otra cosa. Una fila cuya clave ya no exista en el enum se ignora al
 * leer (ver {@link ModuloService}).
 */
@Entity
@Table(name = "modulos")
public class ModuloEstado {

    @Id
    @Column(name = "clave", length = 64, nullable = false)
    private String clave;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected ModuloEstado() {
    }

    public ModuloEstado(Modulo modulo, boolean activo) {
        this.clave = modulo.name();
        this.activo = activo;
    }

    public String getClave() {
        return clave;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
