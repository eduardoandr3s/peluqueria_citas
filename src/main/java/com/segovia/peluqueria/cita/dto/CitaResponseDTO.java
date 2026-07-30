package com.segovia.peluqueria.cita.dto;

import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.pago.EstadoPago;
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
    // Estado del pago asociado a la cita; null si la cita no tiene ningun pago registrado.
    private EstadoPago estadoPago;
    // Id de ese pago, para pedir su recibo sin tener que consultar el pago aparte. Viaja
    // aqui porque el listado ya trae el pago de cada cita en una sola consulta.
    private Integer idPago;
}
