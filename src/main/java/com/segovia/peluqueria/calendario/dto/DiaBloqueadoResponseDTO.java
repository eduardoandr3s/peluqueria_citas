package com.segovia.peluqueria.calendario.dto;

import com.segovia.peluqueria.calendario.DiaBloqueado;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DiaBloqueadoResponseDTO {
    private Integer idDiaBloqueado;
    private LocalDate fecha;
    private String motivo;

    public static DiaBloqueadoResponseDTO desde(DiaBloqueado dia) {
        if (dia == null) return null;
        DiaBloqueadoResponseDTO dto = new DiaBloqueadoResponseDTO();
        dto.setIdDiaBloqueado(dia.getIdDiaBloqueado());
        dto.setFecha(dia.getFecha());
        dto.setMotivo(dia.getMotivo());
        return dto;
    }
}
