package com.segovia.peluqueria.service;

import com.segovia.peluqueria.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.dto.UsuarioUpdateDTO;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    private Usuario crearUsuarioBase() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail("carlos@test.com");
        usuario.setTelefono("123456789");
        usuario.setPassword("encriptada123");
        usuario.setFechaRegistro(LocalDate.now());
        usuario.setActivo(true);
        return usuario;
    }

    // --- crearUsuario ---

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

    // --- obtenerUsuarioPorId ---

    @Test
    void obtenerUsuarioPorId_exitoso() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO resultado = usuarioService.obtenerUsuarioPorId(1);

        assertEquals("Carlos", resultado.getNombre());
        assertEquals("carlos@test.com", resultado.getEmail());
    }

    @Test
    void obtenerUsuarioPorId_noExiste_lanzaExcepcion() {
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.obtenerUsuarioPorId(99));
    }

    // --- listarUsuarios ---

    @Test
    void listarUsuarios_devuelveListaDTOs() {
        Usuario u1 = crearUsuarioBase();
        Usuario u2 = crearUsuarioBase();
        u2.setIdUsuario(2);
        u2.setNombre("Ana");
        u2.setEmail("ana@test.com");

        when(usuarioRepository.findByActivoTrue()).thenReturn(List.of(u1, u2));

        List<UsuarioResponseDTO> resultado = usuarioService.listarUsuarios();

        assertEquals(2, resultado.size());
        assertEquals("Carlos", resultado.get(0).getNombre());
        assertEquals("Ana", resultado.get(1).getNombre());
    }

    // --- actualizarUsuario ---

    @Test
    void actualizarUsuario_soloNombre() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setNombre("Carlos Actualizado");

        UsuarioResponseDTO resultado = usuarioService.actualizarUsuario(1, request);

        assertEquals("Carlos Actualizado", resultado.getNombre());
        assertEquals("carlos@test.com", resultado.getEmail()); // no cambio
        verify(passwordEncoder, never()).encode(any()); // no toco password
    }

    @Test
    void actualizarUsuario_emailEnUso_lanzaExcepcion() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByEmailAndIdUsuarioNot("otro@test.com", 1)).thenReturn(true);

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setEmail("otro@test.com");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.actualizarUsuario(1, request));
        assertTrue(ex.getMessage().contains("Ya existe otro usuario"));
    }

    @Test
    void actualizarUsuario_conPassword_encripta() {
        Usuario usuario = crearUsuarioBase();
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nuevaPassword")).thenReturn("nuevaEncriptada");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioUpdateDTO request = new UsuarioUpdateDTO();
        request.setPassword("nuevaPassword");

        usuarioService.actualizarUsuario(1, request);

        assertEquals("nuevaEncriptada", usuario.getPassword());
        verify(passwordEncoder).encode("nuevaPassword");
    }

    // --- eliminarUsuario ---

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
}
