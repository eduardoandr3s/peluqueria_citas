package com.segovia.peluqueria.pago.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CrearPaymentIntentDTO {
    @NotNull
    private Integer citaId;
}
