package com.segovia.peluqueria.usuario.dto;

import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UsuarioResponseDTO {
    private Integer idUsuario;
    private String nombre;
    private String email;
    private String telefono;
    private LocalDate fechaRegistro;
    private Rol rol;

    public static UsuarioResponseDTO desde(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setIdUsuario(usuario.getIdUsuario());
        dto.setNombre(usuario.getNombre());
        dto.setEmail(usuario.getEmail());
        dto.setTelefono(usuario.getTelefono());
        dto.setFechaRegistro(usuario.getFechaRegistro());
        dto.setRol(usuario.getRol());
        return dto;
    }
}
