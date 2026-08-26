package com.segovia.peluqueria.galeria.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cambios sobre una foto ya subida: el titulo y su sitio en la rejilla. La imagen
 * no se edita, se borra la foto y se sube otra.
 *
 * <p>Los dos campos son opcionales, pero se distinguen «no lo mando» (null, se
 * queda como esta) de «lo dejo vacio» (cadena vacia en el titulo, se borra).
 */
@Data
public class GaleriaFotoUpdateDTO {

    @Size(max = 120, message = "El titulo no puede superar los 120 caracteres")
    private String titulo;

    private Integer orden;
}
