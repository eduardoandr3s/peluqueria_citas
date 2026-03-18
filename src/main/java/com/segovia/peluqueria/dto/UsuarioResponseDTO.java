package com.segovia.peluqueria.dto;

import lombok.Data;

import java.time.LocalDate;
@Data
public class UsuarioResponseDTO {
    private Integer idUsuario;
    private String nombre;
    private String email;
    private String telefono;
    private LocalDate fechaRegistro;
}
