package com.segovia.peluqueria.modulo.dto;

import lombok.Data;

import java.util.Set;

/**
 * Lo que este negocio tiene encendido. Es la respuesta del endpoint publico, asi que solo
 * lleva claves: dice que hace la peluqueria, nunca quien puede hacerlo.
 */
@Data
public class ModulosActivosDTO {

    private Set<String> modulos;

    public ModulosActivosDTO(Set<String> modulos) {
        this.modulos = modulos;
    }
}
