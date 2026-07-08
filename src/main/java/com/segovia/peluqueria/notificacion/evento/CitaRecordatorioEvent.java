package com.segovia.peluqueria.notificacion.evento;

import java.time.LocalDateTime;

public record CitaRecordatorioEvent(
        String clienteNombre,
        String clienteEmail,
        String servicioNombre,
        LocalDateTime fechaHora
) {
}
