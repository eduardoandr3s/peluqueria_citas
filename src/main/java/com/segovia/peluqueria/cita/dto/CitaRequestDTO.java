package com.segovia.peluqueria.cita.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CitaRequestDTO {

    // Opcional: un USER agenda para sí mismo y el backend deriva la identidad del token,
    // ignorando este campo (ver CitaService.agendarCita). Solo un ADMIN lo usa para
    // agendar a nombre de otro usuario. El cliente móvil lo omite.
    private Integer usuarioId;

    @NotNull(message = "El ID del servicio es obligatorio")
    private Integer servicioId;

    @NotNull(message = "La fecha y hora de la cita es obligatoria")
    private LocalDateTime fechaHora;
}
