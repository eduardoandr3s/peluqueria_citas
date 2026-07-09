package com.segovia.peluqueria.peluquero.dto;

import com.segovia.peluqueria.peluquero.Peluquero;
import lombok.Data;

@Data
public class PeluqueroResponseDTO {
    private Integer idPeluquero;
    private String nombre;
    private Boolean activo;

    public static PeluqueroResponseDTO desde(Peluquero peluquero) {
        if (peluquero == null) return null;
        PeluqueroResponseDTO dto = new PeluqueroResponseDTO();
        dto.setIdPeluquero(peluquero.getIdPeluquero());
        dto.setNombre(peluquero.getNombre());
        dto.setActivo(peluquero.getActivo());
        return dto;
    }
}
