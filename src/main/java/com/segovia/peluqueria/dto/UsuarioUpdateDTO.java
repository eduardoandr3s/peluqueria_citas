package com.segovia.peluqueria.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioUpdateDTO {

    private String nombre;
    @Email(message = "El email debe ser válido")
    private String email;

    private String telefono;

    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

}
