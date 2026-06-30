package com.segovia.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Cuerpo de las peticiones que reciben un refresh token: {@code POST /api/auth/refresh}
 * (rotacion) y {@code POST /api/auth/logout} (revocacion).
 */
@Data
public class RefreshRequestDTO {

    @NotBlank(message = "El refresh token es obligatorio")
    private String refreshToken;
}
