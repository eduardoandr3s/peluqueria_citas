package com.segovia.peluqueria.cita.dto;

import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CitaResponseDTO {
    private Integer idCita;
    private LocalDateTime fechaHora;
    private EstadoCita estado;
    private UsuarioResponseDTO usuario;
    private ServicioResponseDTO servicio;
    private PeluqueroResponseDTO peluquero;
}
