package com.segovia.peluqueria.peluquero.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * El CV entero, para reemplazarlo de una vez.
 *
 * <p><b>Aqui un null SI borra</b>, al contrario que en {@link PeluqueroUpdateDTO}, donde un
 * null significa "no lo toques". Es a proposito y es la unica forma de que se pueda vaciar
 * un campo: con la otra convencion no habria manera de quitar una presentacion que ya no
 * gusta. La pantalla edita el bloque completo y lo manda completo, como ya hace
 * {@link ComisionesUpdateDTO} con las excepciones de comision.
 */
@Data
public class PeluqueroCvUpdateDTO {

    @Size(max = 2000, message = "La presentacion no puede pasar de 2000 caracteres")
    private String presentacion;

    /** Se normaliza y se valida en {@link Especialidades}, que es donde vive el formato. */
    private List<String> especialidades;

    @Min(value = 0, message = "Los anios de experiencia no pueden ser negativos")
    @Max(value = 70, message = "Revisa los anios de experiencia")
    private Integer aniosExperiencia;

    /**
     * Usuario de Instagram. Se acepta tal y como lo pegue una persona —con arroba, o la URL
     * entera— y el servidor lo deja en el usuario a secas; lo que no valga se rechaza.
     */
    @Size(max = 100, message = "El usuario de Instagram no puede pasar de 100 caracteres")
    private String instagram;
}
