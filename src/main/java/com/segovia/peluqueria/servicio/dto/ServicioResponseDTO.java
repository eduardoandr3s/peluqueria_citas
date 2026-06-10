package com.segovia.peluqueria.servicio.dto;

import com.segovia.peluqueria.servicio.Servicio;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServicioResponseDTO {
    private Integer idServicio;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Integer duracion;
    private Boolean activo;

    public static ServicioResponseDTO desde(Servicio servicio) {
        if (servicio == null) {
            return null;
        }
        ServicioResponseDTO dto = new ServicioResponseDTO();
        dto.setIdServicio(servicio.getIdServicio());
        dto.setNombre(servicio.getNombre());
        dto.setDescripcion(servicio.getDescripcion());
        dto.setPrecio(servicio.getPrecio());
        dto.setDuracion(servicio.getDuracion());
        dto.setActivo(servicio.getActivo());
        return dto;
    }
}
