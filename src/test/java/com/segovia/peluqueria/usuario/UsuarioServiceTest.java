package com.segovia.peluqueria.usuario;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.notificacion.evento.PasswordCambiadaEvent;
import com.segovia.peluqueria.notificacion.evento.UsuarioRegistradoEvent;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {

    private static final String EMAIL_ADMIN = "admin@test.com";

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher eventPublisher;
    private UsuarioService usuarioService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder, eventPublisher);

        // Por defecto, el usuario autenticado es un ADMIN (acceso total).
        Usuario admin = new Usuario();
        admin.setIdUsuario(99);
        admin.setEmail(EMAIL_ADMIN);
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(admin));
    }

    private Usuario crearUsuarioBase() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail("carlos@test.com");
        usuario.setTelefono("123456789");
        usuario.setPassword("encriptada123");
        usuario.setFechaRegistro(LocalDate.now());
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }

    @Test
    void obtenerUsuarioActual_devuelveLosDatosDelEmailAutenticado() {
        Usuario carlos = crearUsuarioBase();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));

        UsuarioResponseDTO resultado = usuarioService.obtenerUsuarioActual("carlos@test.com");

        assertEquals(1, resultado.getIdUsuario());
        assertEquals("carlos@test.com", resultado.getEmail());
        assertEquals(Rol.USER, resultado.getRol());
    }

    @Test
    void obtenerUsuarioActual_emailInexistente_lanzaNotFound() {
        when(usuarioRepository.findByEmail("fantasma@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.obtenerUsuarioActual("fantasma@test.com"));
    }

    @Test
    void crearUsuario_exitoso() {
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNombre("Carlos");
        request.setEmail("carlos@test.com");
        request.setTelefono("123456789");
        request.setPassword("password123");

        when(usuarioRepository.existsByEmail("carlos@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encriptada123");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario u = invocation.getArgument(0);
            u.setIdUsuario(1);
            return u;
        });

        UsuarioResponseDTO resultado = usuarioService.crearUsuario(request);

        assertEquals("Carlos", resultado.getNombre());
        assertEquals("carlos@test.com", resultado.getEmail());
        verify(passwordEncoder).encode("password123");
        verify(usuarioRepository).save(any(Usuario.class));
        verify(eventPublisher).publishEvent(any(UsuarioRegistradoEvent.class));
    }

    @Test
    void crearUsuario_emailDuplicado_lanzaExcepcion() {
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setEmail("carlos@test.com");

        when(usuarioRepository.existsByEmail("carlos@test.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.crearUsuario(request));
        assertTrue(ex.getMessage().contains("Ya existe un usuario"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void obtenerUsuarioPorId_exitoso() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO resultado = usuarioService.obtenerUsuarioPorId(1, EMAIL_ADMIN);

        assertEquals("Carlos", resultado.getNombre());
        assertEquals("carlos@test.com", resultado.getEmail());
    }

    @Test
    void obtenerUsuarioPorId_noExiste_lanzaExcepcion() {
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.obtenerUsuarioPorId(99, EMAIL_ADMIN));
    }

    @Test
    void obtenerUsuarioPorId_mismoUsuario_noAdmin_exitoso() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO resultado = usuarioService.obtenerUsuarioPorId(1, "carlos@test.com");

        assertEquals("Carlos", resultado.getNombre());
    }

    @Test
    void obtenerUsuarioPorId_otroUsuario_noAdmin_lanzaAccessDenied() {
        Usuario carlos = crearUsuarioBase();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));

        // Carlos (USER, id=1) intenta acceder al usuario id=2
        assertThrows(AccessDeniedException.class,
                () -> usuarioService.obtenerUsuarioPorId(2, "carlos@test.com"));
        verify(usuarioRepository, never()).findById(2);
    }

    @Test
    void listarUsuarios_soloActivos_usaFindByActivoTrue() {
        Usuario u1 = crearUsuarioBase();
        Usuario u2 = crearUsuarioBase();
        u2.setIdUsuario(2);
        u2.setNombre("Ana");
        u2.setEmail("ana@test.com");

        when(usuarioRepository.findByActivoTrue(pageable)).thenReturn(new PageImpl<>(List.of(u1, u2)));

        Page<UsuarioResponseDTO> resultado = usuarioService.listarUsuarios(false, null, pageable);

        assertEquals(2, resultado.getTotalElements());
        assertEquals("Carlos", resultado.getContent().get(0).getNombre());
        assertTrue(resultado.getContent().get(0).getActivo());
        verify(usuarioRepository).findByActivoTrue(pageable);
        verify(usuarioRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listarUsuarios_incluirInactivos_usaFindAll() {
        Usuario activo = crearUsuarioBase();
        Usuario inactivo = crearUsuarioBase();
        inactivo.setIdUsuario(2);
        inactivo.setNombre("Ana");
        inactivo.setActivo(false);

        when(usuarioRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(activo, inactivo)));

        Page<UsuarioResponseDTO> resultado = usuarioService.listarUsuarios(true, null, pageable);

        assertEquals(2, resultado.getTotalElements());
        assertFalse(resultado.getContent().get(1).getActivo());
        verify(usuarioRepository).findAll(pageable);
        verify(usuarioRepository, never()).findByActivoTrue(any(Pageable.class));
    }

    @Test
    void listarUsuarios_conSearch_usaBuscar() {
        Usuario u1 = crearUsuarioBase();
        when(usuarioRepository.buscar("carl", false, pageable))
                .thenReturn(new PageImpl<>(List.of(u1)));

        Page<UsuarioResponseDTO> resultado = usuarioService.listarUsuarios(false, "carl", pageable);

        assertEquals(1, resultado.getTotalElements());
        assertEquals("Carlos", resultado.getContent().get(0).getNombre());
        verify(usuarioRepository).buscar("carl", false, pageable);
        verify(usuarioRepository, never()).findByActivoTrue(any(Pageable.class));
        verify(usuarioRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listarUsuarios_conSearchEIncluirInactivos_usaBuscar() {
        Usuario u1 = crearUsuarioBase();
        when(usuarioRepository.buscar("ana", true, pageable))
                .thenReturn(new PageImpl<>(List.of(u1)));

        usuarioService.listarUsuarios(true, "ana", pageable);

        verify(usuarioRepository).buscar("ana", true, pageable);
    }

    @Test
    void listarUsuarios_searchEnBlanco_usaListadoNormal() {
        when(usuarioRepository.findByActivoTrue(pageable)).thenReturn(new PageImpl<>(List.of()));

        usuarioService.listarUsuarios(false, "   ", pageable);

        // Un search en blanco no debe disparar la busqueda; cae al listado normal.
        verify(usuarioRepository).findByActivoTrue(pageable);
        verify(usuarioRepository, never()).buscar(any(), anyBoolean(), any());
    }

    @Test
    void listarUsuarios_searchConEspacios_seRecorta() {
        when(usuarioRepository.buscar("carlos", false, pageable))
                .thenReturn(new PageImpl<>(List.of(crearUsuarioBase())));

        usuarioService.listarUsuarios(false, "  carlos  ", pageable);

        // El service hace trim() antes de pasar el termino al repositorio.
        verify(usuarioRepository).buscar("carlos", false, pageable);
    }

    @Test
    void actualizarUsuario_soloNombre() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setNombre("Carlos Actualizado");

        UsuarioResponseDTO resultado = usuarioService.actualizarUsuario(1, request, EMAIL_ADMIN);

        assertEquals("Carlos Actualizado", resultado.getNombre());
        assertEquals("carlos@test.com", resultado.getEmail());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void actualizarUsuario_emailEnUso_lanzaExcepcion() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByEmailAndIdUsuarioNot("otro@test.com", 1)).thenReturn(true);

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setEmail("otro@test.com");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.actualizarUsuario(1, request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("Ya existe otro usuario"));
    }

    @Test
    void actualizarUsuario_otroUsuario_noAdmin_lanzaAccessDenied() {
        Usuario carlos = crearUsuarioBase();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setNombre("Hackeado");

        // Carlos (USER, id=1) intenta modificar al usuario id=2
        assertThrows(AccessDeniedException.class,
                () -> usuarioService.actualizarUsuario(2, request, "carlos@test.com"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizarUsuario_conPassword_encripta() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nuevaPassword")).thenReturn("nuevaEncriptada");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setPassword("nuevaPassword");

        usuarioService.actualizarUsuario(1, request, EMAIL_ADMIN);

        assertEquals("nuevaEncriptada", usuario.getPassword());
        verify(passwordEncoder).encode("nuevaPassword");
        verify(eventPublisher).publishEvent(any(PasswordCambiadaEvent.class));
    }

    @Test
    void eliminarUsuario_marcaInactivo() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        usuarioService.eliminarUsuario(1);

        assertFalse(usuario.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void eliminarUsuario_noExiste_lanzaExcepcion() {
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.eliminarUsuario(99));
    }

    @Test
    void activarUsuario_marcaActivo() {
        Usuario usuario = crearUsuarioBase();
        usuario.setActivo(false);
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO resultado = usuarioService.activarUsuario(1);

        assertTrue(usuario.getActivo());
        assertTrue(resultado.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void activarUsuario_noExiste_lanzaExcepcion() {
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.activarUsuario(99));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRol_promueveUsuarioAAdmin_exitoso() {
        Usuario usuario = crearUsuarioBase();
        usuario.setRol(Rol.USER);
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO resultado = usuarioService.cambiarRol(1, Rol.ADMIN);

        assertEquals(Rol.ADMIN, resultado.getRol());
        assertEquals(Rol.ADMIN, usuario.getRol());
        verify(usuarioRepository).save(usuario);
        verify(usuarioRepository, never()).countByRolAndActivoTrue(any());
    }

    @Test
    void cambiarRol_degradaAdmin_conOtrosAdmins_exitoso() {
        Usuario usuario = crearUsuarioBase();
        usuario.setRol(Rol.ADMIN);
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).thenReturn(2L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO resultado = usuarioService.cambiarRol(1, Rol.USER);

        assertEquals(Rol.USER, resultado.getRol());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void cambiarRol_degradaUltimoAdmin_lanzaExcepcion() {
        Usuario usuario = crearUsuarioBase();
        usuario.setRol(Rol.ADMIN);
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.cambiarRol(1, Rol.USER));
        assertTrue(ex.getMessage().contains("único administrador"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRol_usuarioNoExiste_lanzaExcepcion() {
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.cambiarRol(99, Rol.ADMIN));
        verify(usuarioRepository, never()).save(any());
    }
}
