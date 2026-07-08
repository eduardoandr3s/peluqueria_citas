package com.segovia.peluqueria.estadistica.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class EstadisticasResponseDTO {
    private List<CitasPorEstadoDTO> citasPorEstado;
    private IngresosDTO ingresos;
    private List<TopServicioDTO> topServicios;
    private long nuevosClientes;
}
