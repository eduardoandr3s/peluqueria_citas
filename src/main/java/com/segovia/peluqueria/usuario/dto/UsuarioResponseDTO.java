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
    private Boolean activo;

    /**
     * URL firmada del avatar, o null si no tiene o si quien lee no la necesita.
     *
     * <p>Se rellena solo donde se muestra un usuario concreto ({@code /me} y
     * {@code /{id}}): firmar es una llamada al almacen, y hacerlo por cada fila de
     * un listado paginado seria una por usuario. El listado devuelve null a
     * proposito.
     */
    private String urlAvatar;

    /** Sin avatar. Es el que usan los listados y los DTO que anidan un usuario. */
    public static UsuarioResponseDTO desde(Usuario usuario) {
        return desde(usuario, null);
    }

    public static UsuarioResponseDTO desde(Usuario usuario, String urlAvatar) {
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
        dto.setActivo(usuario.getActivo());
        dto.setUrlAvatar(urlAvatar);
        return dto;
    }
}
