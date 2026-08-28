package com.segovia.peluqueria.usuario;

import com.segovia.peluqueria.almacen.AlmacenException;
import com.segovia.peluqueria.almacen.AlmacenFicheros;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.notificacion.evento.PasswordCambiadaEvent;
import com.segovia.peluqueria.notificacion.evento.UsuarioRegistradoEvent;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final AlmacenFicheros almacen;
    private final ValidadorImagen validadorImagen;
    private final AlmacenProperties almacenProperties;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher eventPublisher,
                          AlmacenFicheros almacen,
                          ValidadorImagen validadorImagen,
                          AlmacenProperties almacenProperties) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.almacen = almacen;
        this.validadorImagen = validadorImagen;
        this.almacenProperties = almacenProperties;
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
        eventPublisher.publishEvent(
                new UsuarioRegistradoEvent(usuarioGuardado.getNombre(), usuarioGuardado.getEmail()));

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

    /**
     * URL firmada del avatar, o null si no tiene. Propaga el fallo del almacen.
     *
     * <p>Solo se resuelve en respuestas de un usuario concreto: el bucket es privado,
     * asi que cada avatar cuesta una llamada al almacen y en un listado paginado
     * seria una por fila.
     */
    private String urlAvatar(Usuario usuario) {
        String clave = usuario.getAvatarClave();
        if (clave == null || clave.isBlank()) {
            return null;
        }
        return almacen.urlFirmada(
                almacenProperties.getBucketAvatares(), clave, almacenProperties.getValidezUrlFirmada());
    }

    /**
     * DTO de un usuario concreto, con su avatar si se puede firmar.
     *
     * <p>Si el almacen falla se devuelve sin foto en vez de propagar el error: estas
     * peticiones (ver el perfil, el detalle, guardar el nombre) no son sobre el
     * avatar, y un 502 dejaria al usuario sin poder ver sus datos por una imagen
     * decorativa. Las que si son sobre el avatar no pasan por aqui.
     */
    private UsuarioResponseDTO conAvatar(Usuario usuario) {
        try {
            return UsuarioResponseDTO.desde(usuario, urlAvatar(usuario));
        } catch (AlmacenException e) {
            log.warn("No se ha podido firmar el avatar del usuario id={}: {}",
                    usuario.getIdUsuario(), e.getMessage());
            return UsuarioResponseDTO.desde(usuario);
        }
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerUsuarioPorId(Integer id, String emailAutenticado){
        verificarAcceso(id, emailAutenticado);
        Usuario usuario = obtenerEntidadPorId(id);
        return conAvatar(usuario);
    }

    /**
     * Sustituye el avatar del usuario. Cada uno sube el suyo y un ADMIN puede subir
     * el de cualquiera, asi que el permiso no es de rol sino de propiedad y se
     * comprueba aqui (igual que en el resto del dominio).
     *
     * <p>La clave la genera el validador a partir del contenido, nunca el cliente, y
     * la anterior se borra para no dejar objetos huerfanos ocupando cuota.
     */
    @Transactional
    public UsuarioResponseDTO subirAvatar(Integer id, MultipartFile imagen, String emailAutenticado) {
        verificarAcceso(id, emailAutenticado);
        Usuario usuario = obtenerEntidadPorId(id);
        ValidadorImagen.ImagenValidada validada = validadorImagen.validar(imagen, String.valueOf(id));
        String bucket = almacenProperties.getBucketAvatares();

        String claveAnterior = usuario.getAvatarClave();
        String clave = almacen.guardar(bucket, validada.clave(), validada.contenido(), validada.contentType());
        usuario.setAvatarClave(clave);
        Usuario guardado = usuarioRepository.save(usuario);

        if (claveAnterior != null && !claveAnterior.isBlank() && !claveAnterior.equals(clave)) {
            almacen.borrar(bucket, claveAnterior);
        }
        // Aqui si se propaga un fallo al firmar (502): la peticion era precisamente
        // para cambiar la foto, y devolverla a null diria que no hay ninguna.
        return UsuarioResponseDTO.desde(guardado, urlAvatar(guardado));
    }

    /** Quita el avatar. Es idempotente: sin avatar, no hace nada. */
    @Transactional
    public UsuarioResponseDTO borrarAvatar(Integer id, String emailAutenticado) {
        verificarAcceso(id, emailAutenticado);
        Usuario usuario = obtenerEntidadPorId(id);
        String clave = usuario.getAvatarClave();
        if (clave == null || clave.isBlank()) {
            return UsuarioResponseDTO.desde(usuario);
        }
        usuario.setAvatarClave(null);
        Usuario guardado = usuarioRepository.save(usuario);
        almacen.borrar(almacenProperties.getBucketAvatares(), clave);
        return UsuarioResponseDTO.desde(guardado);
    }

    // Datos del usuario autenticado (GET /api/usuarios/me): se resuelve por el email del token,
    // sin necesidad de que el cliente conozca su propio id.
    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerUsuarioActual(String emailAutenticado){
        Usuario usuario = usuarioRepository.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + emailAutenticado));
        return conAvatar(usuario);
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

        boolean passwordCambiada = request.getPassword() != null && !request.getPassword().isEmpty();
        if (passwordCambiada) {
            usuarioExistente.setPassword(passwordEncoder.encode(request.getPassword()));
            // Al cambiar la password invalidamos los tokens emitidos antes (posible robo de sesion).
            usuarioExistente.setTokenVersion(usuarioExistente.getTokenVersion() + 1);
        }

        Usuario usuarioGuardado = usuarioRepository.save(usuarioExistente);

        if (passwordCambiada) {
            eventPublisher.publishEvent(
                    new PasswordCambiadaEvent(usuarioGuardado.getNombre(), usuarioGuardado.getEmail()));
        }

        // Con avatar, como el resto de respuestas de un usuario concreto: si no, quien
        // pinte el resultado de guardar el nombre veria desaparecer la foto.
        return conAvatar(usuarioGuardado);
    }

    @Transactional
    public UsuarioResponseDTO cambiarRol(Integer id, Rol nuevoRol) {
        Usuario usuario = obtenerEntidadPorId(id);

        // Evita dejar el sistema sin administradores: no se puede degradar al último ADMIN
        // activo. La condicion es "a cualquier rol que no sea ADMIN" y no "a USER": con
        // PELUQUERO en medio, comparar contra USER dejaria pasar la degradacion a PELUQUERO
        // y el sistema se quedaria sin nadie que pueda volver a dar el rol.
        if (usuario.getRol() == Rol.ADMIN && nuevoRol != Rol.ADMIN
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
