package com.segovia.peluqueria.pago.dto;

import com.segovia.peluqueria.pago.EstadoPago;
import com.segovia.peluqueria.pago.MetodoPago;
import com.segovia.peluqueria.pago.Pago;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagoResponseDTO {
    private Integer idPago;
    private Integer citaId;
    private BigDecimal monto;
    private MetodoPago metodoPago;
    private EstadoPago estadoPago;
    private String referenciaExterna;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPago;

    public static PagoResponseDTO desde(Pago pago) {
        PagoResponseDTO dto = new PagoResponseDTO();
        dto.setIdPago(pago.getIdPago());
        dto.setCitaId(pago.getCita().getIdCita());
        dto.setMonto(pago.getMonto());
        dto.setMetodoPago(pago.getMetodoPago());
        dto.setEstadoPago(pago.getEstadoPago());
        dto.setReferenciaExterna(pago.getReferenciaExterna());
        dto.setFechaCreacion(pago.getFechaCreacion());
        dto.setFechaPago(pago.getFechaPago());
        return dto;
    }
}
