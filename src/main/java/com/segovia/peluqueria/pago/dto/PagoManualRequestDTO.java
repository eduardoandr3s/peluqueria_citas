package com.segovia.peluqueria.pago.dto;

import com.segovia.peluqueria.pago.MetodoPago;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PagoManualRequestDTO {
    @NotNull
    private Integer citaId;

    @NotNull
    private MetodoPago metodoPago;
}
