package com.segovia.peluqueria.modulo.dto;

import com.segovia.peluqueria.modulo.Modulo;
import lombok.Data;

/**
 * Una fila de la pantalla de modulos.
 *
 * <p>Lleva dos booleanos a proposito. {@code activo} es lo que decidio el administrador y
 * es lo que pinta la casilla; {@code efectivo} es lo que aplica de verdad una vez contadas
 * las reglas de padre e hijos. Cuando no coinciden hay algo que explicar en la pantalla:
 * un padre encendido sin ningun hijo encendido no hace nada, y un hijo encendido bajo un
 * padre apagado tampoco.
 */
@Data
public class ModuloDTO {

    private String clave;
    private String nombre;
    private String descripcion;
    /** Clave del modulo del que cuelga, o null si es de primer nivel. */
    private String padre;
    private boolean activo;
    private boolean efectivo;

    public ModuloDTO(Modulo modulo, boolean activo, boolean efectivo) {
        this.clave = modulo.name();
        this.nombre = modulo.getNombre();
        this.descripcion = modulo.getDescripcion();
        this.padre = modulo.getPadre() == null ? null : modulo.getPadre().name();
        this.activo = activo;
        this.efectivo = efectivo;
    }
}
