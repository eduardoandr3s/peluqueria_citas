package com.segovia.peluqueria.modulo.dto;

import com.segovia.peluqueria.modulo.Modulo;
import com.segovia.peluqueria.modulo.PerfilArranque;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * Un perfil de arranque en la pantalla de modulos. Lleva las dos listas ya resueltas
 * —lo que enciende y lo que apaga— para que el panel pueda avisar de lo que se pierde
 * antes de aplicarlo, sin recalcularlo por su cuenta y arriesgarse a discrepar.
 */
@Data
public class PerfilArranqueDTO {

    private String clave;
    private String nombre;
    private String descripcion;
    private List<String> enciende;
    private List<String> apaga;

    public PerfilArranqueDTO(PerfilArranque perfil) {
        this.clave = perfil.name();
        this.nombre = perfil.getNombre();
        this.descripcion = perfil.getDescripcion();
        this.enciende = Arrays.stream(Modulo.values())
                .filter(modulo -> perfil.getEncendidos().contains(modulo))
                .map(Modulo::name)
                .toList();
        this.apaga = Arrays.stream(Modulo.values())
                .filter(modulo -> !perfil.getEncendidos().contains(modulo))
                .map(Modulo::name)
                .toList();
    }
}
