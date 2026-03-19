package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.auth.dto.AuthResponseDTO;
import com.segovia.peluqueria.auth.dto.LoginRequestDTO;
import com.segovia.peluqueria.security.JwtService;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.segovia.peluqueria.usuario.UsuarioService;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private UsuarioService usuarioService;
    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authController = new AuthController(usuarioService, usuarioRepository, passwordEncoder, jwtService);
    }

    private Usuario crearUsuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail("carlos@test.com");
        usuario.setPassword("encriptada123");
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }

    @Test
    void registro_delegaAUsuarioService() {
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNombre("Carlos");
        request.setEmail("carlos@test.com");

        UsuarioResponseDTO respuestaEsperada = new UsuarioResponseDTO();
        respuestaEsperada.setNombre("Carlos");
        respuestaEsperada.setEmail("carlos@test.com");

        when(usuarioService.crearUsuario(request)).thenReturn(respuestaEsperada);

        UsuarioResponseDTO resultado = authController.registro(request);

        assertEquals("Carlos", resultado.getNombre());
        verify(usuarioService).crearUsuario(request);
    }

    @Test
    void login_exitoso() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("carlos@test.com");
        request.setPassword("password123");

        Usuario usuario = crearUsuarioActivo();

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "encriptada123")).thenReturn(true);
        when(jwtService.generarToken("carlos@test.com", "USER", 1)).thenReturn("token.jwt.generado");

        AuthResponseDTO resultado = authController.login(request);

        assertEquals("token.jwt.generado", resultado.getToken());
        assertEquals("carlos@test.com", resultado.getEmail());
        assertEquals("Carlos", resultado.getNombre());
        assertEquals("USER", resultado.getRol());
    }

    @Test
    void login_emailNoExiste_lanzaExcepcion() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("noexiste@test.com");
        request.setPassword("password123");

        when(usuarioRepository.findByEmail("noexiste@test.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authController.login(request));
        assertTrue(ex.getMessage().contains("Credenciales incorrectas"));
    }

    @Test
    void login_usuarioInactivo_lanzaExcepcion() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("carlos@test.com");
        request.setPassword("password123");

        Usuario usuario = crearUsuarioActivo();
        usuario.setActivo(false);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authController.login(request));
        assertTrue(ex.getMessage().contains("desactivada"));
    }

    @Test
    void login_passwordIncorrecta_lanzaExcepcion() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("carlos@test.com");
        request.setPassword("passwordMal");

        Usuario usuario = crearUsuarioActivo();

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("passwordMal", "encriptada123")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authController.login(request));
        assertTrue(ex.getMessage().contains("Credenciales incorrectas"));
    }
}
