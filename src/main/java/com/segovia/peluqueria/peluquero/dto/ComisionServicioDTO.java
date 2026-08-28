package com.segovia.peluqueria.peluquero.dto;

import com.segovia.peluqueria.peluquero.ComisionServicio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Excepcion de comision para un servicio. Sirve de ida y de vuelta. */
@Data
public class ComisionServicioDTO {

    @NotNull(message = "El servicio es obligatorio")
    private Integer servicioId;

    /** Solo informativo al leer; al escribir se ignora. */
    private String servicioNombre;

    @NotNull(message = "El porcentaje es obligatorio")
    @DecimalMin(value = "0.00", message = "La comision no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La comision no puede pasar del 100%")
    private BigDecimal porcentaje;

    public static ComisionServicioDTO desde(ComisionServicio comision) {
        ComisionServicioDTO dto = new ComisionServicioDTO();
        dto.setServicioId(comision.getServicio().getIdServicio());
        dto.setServicioNombre(comision.getServicio().getNombre());
        dto.setPorcentaje(comision.getPorcentaje());
        return dto;
    }
}
