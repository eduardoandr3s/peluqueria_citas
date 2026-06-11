package com.segovia.peluqueria.usuario;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponseDTO> listarUsuarios(boolean incluirInactivos, String search, Pageable pageable) {
        Page<Usuario> usuarios;
        if (search == null || search.isBlank()) {
            usuarios = incluirInactivos
                    ? usuarioRepository.findAll(pageable)
                    : usuarioRepository.findByActivoTrue(pageable);
        } else {
            usuarios = usuarioRepository.buscar(search.trim(), incluirInactivos, pageable);
        }
        return usuarios.map(UsuarioResponseDTO::desde);
    }

    @Transactional
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

        return UsuarioResponseDTO.desde(usuarioGuardado);
    }

    private Usuario obtenerEntidadPorId(Integer id){
        return  usuarioRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }

    // Solo el propio usuario o un ADMIN pueden acceder a los datos de un usuario concreto.
    private void verificarAcceso(Integer idObjetivo, String emailAutenticado) {
        Usuario actual = usuarioRepository.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + emailAutenticado));

        if (actual.getRol() != Rol.ADMIN && !actual.getIdUsuario().equals(idObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para acceder a este recurso.");
        }
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerUsuarioPorId(Integer id, String emailAutenticado){
        verificarAcceso(id, emailAutenticado);
        Usuario usuario = obtenerEntidadPorId(id);
        return UsuarioResponseDTO.desde(usuario);
    }

    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Integer id, UsuarioUpdateDTO request, String emailAutenticado) {
        verificarAcceso(id, emailAutenticado);
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
            // Al cambiar la password invalidamos los tokens emitidos antes (posible robo de sesion).
            usuarioExistente.setTokenVersion(usuarioExistente.getTokenVersion() + 1);
        }

        Usuario usuarioGuardado = usuarioRepository.save(usuarioExistente);

        return UsuarioResponseDTO.desde(usuarioGuardado);
    }

    @Transactional
    public UsuarioResponseDTO cambiarRol(Integer id, Rol nuevoRol) {
        Usuario usuario = obtenerEntidadPorId(id);

        // Evita dejar el sistema sin administradores: no se puede degradar al último ADMIN activo.
        if (usuario.getRol() == Rol.ADMIN && nuevoRol == Rol.USER
                && usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new IllegalArgumentException("No se puede quitar el rol ADMIN al único administrador activo.");
        }

        Rol rolAnterior = usuario.getRol();
        usuario.setRol(nuevoRol);
        // Invalida los tokens vigentes para que el cambio de rol obligue a renovar la sesion.
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        log.info("Cambio de rol del usuario id={} ({}): {} -> {}",
                usuario.getIdUsuario(), usuario.getEmail(), rolAnterior, nuevoRol);

        return UsuarioResponseDTO.desde(usuarioGuardado);
    }

    @Transactional
    public void eliminarUsuario(Integer id){
        Usuario usuarioExistente = obtenerEntidadPorId(id);
        usuarioExistente.setActivo(false);
        usuarioRepository.save(usuarioExistente);
    }

    @Transactional
    public UsuarioResponseDTO activarUsuario(Integer id) {
        // findById directo (no obtenerEntidadPorId): el usuario a reactivar esta inactivo por definicion.
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        usuario.setActivo(true);
        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        log.info("Usuario reactivado id={} ({})", usuario.getIdUsuario(), usuario.getEmail());
        return UsuarioResponseDTO.desde(usuarioGuardado);
    }
}
