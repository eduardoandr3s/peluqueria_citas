package com.segovia.peluqueria.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Cuerpo de POST /api/auth/recuperar: email al que enviar el enlace de restablecimiento. */
@Data
public class RecuperarPasswordRequestDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser valido")
    private String email;
}
