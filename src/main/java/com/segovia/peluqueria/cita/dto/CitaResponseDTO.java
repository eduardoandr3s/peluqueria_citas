package com.segovia.peluqueria.cita.dto;

import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.pago.EstadoPago;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import lombok.Data;

import java.math.BigDecimal;
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

    // ---- Datos de cierre y gestion ----
    // Se rellenan solo para quien gestiona la cita (ADMIN, o el peluquero que la tiene
    // asignada) y van a null para el cliente, igual que urlAvatar en UsuarioResponseDTO.
    // Las observaciones son notas internas de trabajo y el porcentaje de comision es lo
    // que cobra el profesional: ninguna de las dos cosas se le ensena al cliente.
    private LocalDateTime fechaCierre;
    private String observaciones;
    private Boolean clienteContactado;
    private String cerradaPor;
    private BigDecimal precioAplicado;
    private BigDecimal comisionPorcentajeAplicado;
}
