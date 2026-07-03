package com.segovia.peluqueria.pago.dto;

import lombok.Data;

@Data
public class PaymentIntentResponseDTO {
    private String clientSecret;
    private String paymentIntentId;
    private Integer pagoId;
}
