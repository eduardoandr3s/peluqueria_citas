package com.segovia.peluqueria.usuario;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioUpdateDTO;
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

    public List<UsuarioResponseDTO> listarUsuarios() {
        List<Usuario> usuarios = usuarioRepository.findByActivoTrue();
        List<UsuarioResponseDTO> respuesta = new ArrayList<>();

        for (Usuario u : usuarios){
            respuesta.add(mapearAResponseDTO(u));
        }

        return respuesta;
    }

    public UsuarioResponseDTO crearUsuario(UsuarioRequestDTO request){
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con el email: " + request.getEmail());
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setNombre(request.getNombre());
        nuevoUsuario.setEmail(request.getEmail());
        nuevoUsuario.setTelefono(request.getTelefono());

        String passwordEncriptada = passwordEncoder.encode(request.getPassword());
        nuevoUsuario.setPassword(passwordEncriptada);

        nuevoUsuario.setFechaRegistro(LocalDate.now());

        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        return mapearAResponseDTO(usuarioGuardado);
    }

    private UsuarioResponseDTO mapearAResponseDTO(Usuario usuario) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setIdUsuario(usuario.getIdUsuario());
        dto.setNombre(usuario.getNombre());
        dto.setEmail(usuario.getEmail());
        dto.setTelefono(usuario.getTelefono());
        dto.setFechaRegistro(usuario.getFechaRegistro());
        return dto;
    }

    private Usuario obtenerEntidadPorId(Integer id){
        return  usuarioRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }

    public UsuarioResponseDTO obtenerUsuarioPorId(Integer id){
        Usuario usuario = obtenerEntidadPorId(id);
        return mapearAResponseDTO(usuario);
    }

    public UsuarioResponseDTO actualizarUsuario(Integer id, UsuarioUpdateDTO request) {
        Usuario usuarioExistente = obtenerEntidadPorId(id);

        if (request.getNombre() != null && !request.getNombre().isEmpty()) {
            usuarioExistente.setNombre(request.getNombre());
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (usuarioRepository.existsByEmailAndIdUsuarioNot(request.getEmail(), id)) {
                throw new IllegalArgumentException("Ya existe otro usuario registrado con el email: " + request.getEmail());
            }
            usuarioExistente.setEmail(request.getEmail());
        }
        if (request.getTelefono() != null && !request.getTelefono().isEmpty()) {
            usuarioExistente.setTelefono(request.getTelefono());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            usuarioExistente.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        Usuario usuarioGuardado = usuarioRepository.save(usuarioExistente);

        return mapearAResponseDTO(usuarioGuardado);
    }

    public void eliminarUsuario(Integer id){
        Usuario usuarioExistente = obtenerEntidadPorId(id);
        usuarioExistente.setActivo(false);
        usuarioRepository.save(usuarioExistente);
    }
}
