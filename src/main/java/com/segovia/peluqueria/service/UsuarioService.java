package com.segovia.peluqueria.service;

import com.segovia.peluqueria.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Obtener los usarios mapeando a DTO
    public List<UsuarioResponseDTO> listarUsuarios() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        List<UsuarioResponseDTO> respuesta = new ArrayList<>();

        for (Usuario u : usuarios){
            respuesta.add(mapearAResponseDTO(u));
        }

        return respuesta;
    }

    public UsuarioResponseDTO crearUsuario(UsuarioRequestDTO request){
        // 1. Convertir el DTO a entidad
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setNombre(request.getNombre());
        nuevoUsuario.setEmail(request.getEmail());
        nuevoUsuario.setTelefono(request.getTelefono());
        nuevoUsuario.setPassword(request.getPassword());
        // fecha_reggistro con LocalDate.now()
        nuevoUsuario.setFecha_registro(LocalDate.now());

        //2. Guardar la entidad en la base de datos
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // 3. Convertir la entidad guardada a DTO de respuesta
        return mapearAResponseDTO(usuarioGuardado);
    }

    // Método privado para mapear de Usuario a UsuarioResponseDTO
    private UsuarioResponseDTO mapearAResponseDTO(Usuario usuario) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setId_usuario(usuario.getId_usuario());
        dto.setNombre(usuario.getNombre());
        dto.setEmail(usuario.getEmail());
        dto.setTelefono(usuario.getTelefono());
        dto.setFecha_registro(usuario.getFecha_registro());
        return dto;
    }




}
