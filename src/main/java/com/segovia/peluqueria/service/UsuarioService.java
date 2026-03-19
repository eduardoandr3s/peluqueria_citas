package com.segovia.peluqueria.service;

import com.segovia.peluqueria.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.dto.UsuarioUpdateDTO;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Obtener los usuarios activos mapeando a DTO
    public List<UsuarioResponseDTO> listarUsuarios() {
        List<Usuario> usuarios = usuarioRepository.findByActivoTrue();
        List<UsuarioResponseDTO> respuesta = new ArrayList<>();

        for (Usuario u : usuarios){
            respuesta.add(mapearAResponseDTO(u));
        }

        return respuesta;
    }

    public UsuarioResponseDTO crearUsuario(UsuarioRequestDTO request){
        // 1. Validar que el email no esté registrado
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con el email: " + request.getEmail());
        }

        // 2. Convertir el DTO a entidad
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setNombre(request.getNombre());
        nuevoUsuario.setEmail(request.getEmail());
        nuevoUsuario.setTelefono(request.getTelefono());

        // Encriptar la contraseña antes de guardarla en la base de datos
        String passwordEncriptada = passwordEncoder.encode(request.getPassword());
        nuevoUsuario.setPassword(passwordEncriptada);


        // fecha_registro con LocalDate.now()
        nuevoUsuario.setFechaRegistro(LocalDate.now());

        //2. Guardar la entidad en la base de datos
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // 3. Convertir la entidad guardada a DTO de respuesta
        return mapearAResponseDTO(usuarioGuardado);
    }

    // Método privado para mapear de Usuario a UsuarioResponseDTO
    private UsuarioResponseDTO mapearAResponseDTO(Usuario usuario) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setIdUsuario(usuario.getIdUsuario());
        dto.setNombre(usuario.getNombre());
        dto.setEmail(usuario.getEmail());
        dto.setTelefono(usuario.getTelefono());
        dto.setFechaRegistro(usuario.getFechaRegistro());
        return dto;
    }

    // método privado para obtener la entidad Usuario por su ID, lanzando una excepción si no se encuentra
    private Usuario obtenerEntidadPorId(Integer id){
        return  usuarioRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }

    // Método público para obtener un usuario por su ID, devolviendo un DTO de respuesta
    public UsuarioResponseDTO obtenerUsuarioPorId(Integer id){
        Usuario usuario = obtenerEntidadPorId(id);
        return mapearAResponseDTO(usuario);
    }

    // Método público para actualizar un usuario existente por su ID, recibiendo un DTO de solicitud y devolviendo un DTO de respuesta
    public UsuarioResponseDTO actualizarUsuario(Integer id, UsuarioUpdateDTO request) {
        // 1. Obtener la entidad existente por su ID, lanzando una excepción si no se encuentra
        Usuario usuarioExistente = obtenerEntidadPorId(id);

        // 2. Actualizar los campos de la entidad con los datos del DTO de solicitud

        if (request.getNombre() != null && !request.getNombre().isEmpty()) {
            usuarioExistente.setNombre(request.getNombre());
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            // Validar que el nuevo email no esté en uso por otro usuario
            if (usuarioRepository.existsByEmailAndIdUsuarioNot(request.getEmail(), id)) {
                throw new IllegalArgumentException("Ya existe otro usuario registrado con el email: " + request.getEmail());
            }
            usuarioExistente.setEmail(request.getEmail());
        }
        if (request.getTelefono() != null && !request.getTelefono().isEmpty()) {
            usuarioExistente.setTelefono(request.getTelefono());
        }

        // Encriptar la nueva contraseña antes de actualizarla en la base de datos
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            usuarioExistente.setPassword(passwordEncoder.encode(request.getPassword()));
        }



        // 3. Guardar la entidad actualizada en la base de datos
        Usuario usuarioGuardado = usuarioRepository.save(usuarioExistente);

        // 4. Convertir la entidad guardada a DTO de respuesta y devolverlo
        return mapearAResponseDTO(usuarioGuardado);
    }

    // Soft delete: marca al usuario como inactivo en vez de eliminarlo
    public void eliminarUsuario(Integer id){
        Usuario usuarioExistente = obtenerEntidadPorId(id);
        usuarioExistente.setActivo(false);
        usuarioRepository.save(usuarioExistente);
    }


}
