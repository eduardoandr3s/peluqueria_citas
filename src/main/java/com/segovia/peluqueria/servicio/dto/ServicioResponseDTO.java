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

    /**
     * URL de la foto del servicio, o null si no tiene. Se calcula al leer a partir
     * de la clave guardada: los clientes nunca ven ni manejan la clave del objeto.
     */
    private String urlImagen;

    public static ServicioResponseDTO desde(Servicio servicio) {
        return desde(servicio, null);
    }

    public static ServicioResponseDTO desde(Servicio servicio, String urlImagen) {
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
        dto.setUrlImagen(urlImagen);
        return dto;
    }
}
