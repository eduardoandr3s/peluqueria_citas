package com.segovia.peluqueria.dto;

import lombok.Data;

@Data
public class UsuarioRequestDTO {
    private String nombre;
    private String email;
    private String telefono;
    private String password;
}
