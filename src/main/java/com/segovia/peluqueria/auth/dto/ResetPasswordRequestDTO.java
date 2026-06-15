package com.segovia.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cuerpo de POST /api/auth/reset: token recibido por correo + nueva contrasena. */
@Data
public class ResetPasswordRequestDTO {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
    private String password;
}
