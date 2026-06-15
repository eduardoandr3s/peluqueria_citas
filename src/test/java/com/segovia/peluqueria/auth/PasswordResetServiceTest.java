package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.notificacion.evento.PasswordCambiadaEvent;
import com.segovia.peluqueria.notificacion.evento.PasswordResetSolicitadoEvent;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final String FRONTEND_URL = "http://localhost:4200";

    private PasswordResetTokenRepository tokenRepository;
    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher eventPublisher;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(PasswordResetTokenRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new PasswordResetService(tokenRepository, usuarioRepository,
                passwordEncoder, eventPublisher, FRONTEND_URL, 30L);
    }

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail("carlos@test.com");
        usuario.setPassword("hash-viejo");
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        usuario.setTokenVersion(1);
        return usuario;
    }

    @Test
    void solicitarReset_usuarioExistente_guardaTokenYPublicaEventoConEnlace() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarReset("carlos@test.com");

        verify(tokenRepository).invalidarVigentesDe(usuario);
        verify(tokenRepository).save(any(PasswordResetToken.class));

        ArgumentCaptor<PasswordResetSolicitadoEvent> captor =
                ArgumentCaptor.forClass(PasswordResetSolicitadoEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PasswordResetSolicitadoEvent evento = captor.getValue();
        assertEquals("carlos@test.com", evento.email());
        assertTrue(evento.enlace().startsWith(FRONTEND_URL + "/reset?token="));
    }

    @Test
    void solicitarReset_emailNoExiste_noGuardaNiPublica() {
        when(usuarioRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        // No debe lanzar (anti-enumeracion).
        assertDoesNotThrow(() -> service.solicitarReset("nadie@test.com"));

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void solicitarReset_usuarioInactivo_noGuardaNiPublica() {
        Usuario usuario = usuarioActivo();
        usuario.setActivo(false);
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarReset("carlos@test.com");

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetearPassword_tokenValido_cambiaPasswordSubeTokenVersionYMarcaUsado() {
        Usuario usuario = usuarioActivo();
        PasswordResetToken token = new PasswordResetToken();
        token.setUsuario(usuario);
        token.setUsado(false);
        token.setExpiraEn(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("nuevaClave123")).thenReturn("hash-nuevo");

        service.resetearPassword("el-token-en-claro", "nuevaClave123");

        assertEquals("hash-nuevo", usuario.getPassword());
        assertEquals(2, usuario.getTokenVersion());
        assertTrue(token.isUsado());
        verify(usuarioRepository).save(usuario);
        verify(tokenRepository).save(token);
        verify(eventPublisher).publishEvent(any(PasswordCambiadaEvent.class));
    }

    @Test
    void resetearPassword_tokenCaducado_lanzaExcepcion() {
        Usuario usuario = usuarioActivo();
        PasswordResetToken token = new PasswordResetToken();
        token.setUsuario(usuario);
        token.setUsado(false);
        token.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetearPassword("el-token", "nuevaClave123"));
        assertTrue(ex.getMessage().contains("no es valido o ha caducado"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void resetearPassword_tokenUsado_lanzaExcepcion() {
        Usuario usuario = usuarioActivo();
        PasswordResetToken token = new PasswordResetToken();
        token.setUsuario(usuario);
        token.setUsado(true);
        token.setExpiraEn(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class,
                () -> service.resetearPassword("el-token", "nuevaClave123"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void resetearPassword_tokenInexistente_lanzaExcepcion() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.resetearPassword("token-desconocido", "nuevaClave123"));
        verify(usuarioRepository, never()).save(any());
    }
}
