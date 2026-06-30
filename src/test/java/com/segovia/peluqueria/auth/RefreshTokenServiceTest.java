package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.exception.InvalidRefreshTokenException;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository tokenRepository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(tokenRepository, 30L);
    }

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail("carlos@test.com");
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        usuario.setTokenVersion(1);
        return usuario;
    }

    @Test
    void emitirNuevaFamilia_guardaTokenConFamiliaNuevaYTokenVersionDelUsuario() {
        Usuario usuario = usuarioActivo();

        String plano = service.emitirNuevaFamilia(usuario);

        assertNotNull(plano);
        assertFalse(plano.isBlank());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository).save(captor.capture());
        RefreshToken guardado = captor.getValue();
        assertEquals(usuario, guardado.getUsuario());
        assertEquals(1, guardado.getTokenVersion());
        assertNotNull(guardado.getFamilia());
        assertFalse(guardado.isRevocado());
        assertTrue(guardado.getExpiraEn().isAfter(LocalDateTime.now()));
        // El hash almacenado no debe ser el valor en claro.
        assertNotEquals(plano, guardado.getTokenHash());
    }

    @Test
    void rotar_tokenValido_invalidaElViejoYEmiteUnoNuevoEnLaMismaFamilia() {
        Usuario usuario = usuarioActivo();
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setFamilia("familia-1");
        token.setTokenVersion(1);
        token.setRevocado(false);
        token.setExpiraEn(LocalDateTime.now().plusDays(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        RefreshTokenService.RotacionResult resultado = service.rotar("refresh-en-claro");

        assertTrue(token.isRevocado(), "El token presentado debe quedar revocado tras rotar");
        assertEquals(usuario, resultado.usuario());
        assertNotNull(resultado.refreshTokenPlano());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository, times(2)).save(captor.capture());
        RefreshToken nuevo = captor.getAllValues().get(1);
        assertEquals("familia-1", nuevo.getFamilia(), "El nuevo token conserva la familia");
        assertEquals(1, nuevo.getTokenVersion());
        assertFalse(nuevo.isRevocado());
    }

    @Test
    void rotar_tokenInexistente_lanzaInvalidRefreshToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotar("desconocido"));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void rotar_tokenYaRevocado_revocaLaFamiliaYLanza() {
        Usuario usuario = usuarioActivo();
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setFamilia("familia-comprometida");
        token.setTokenVersion(1);
        token.setRevocado(true);
        token.setExpiraEn(LocalDateTime.now().plusDays(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotar("refresh-reusado"));
        verify(tokenRepository).revocarFamilia("familia-comprometida");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void rotar_tokenCaducado_lanza() {
        Usuario usuario = usuarioActivo();
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setFamilia("familia-1");
        token.setTokenVersion(1);
        token.setRevocado(false);
        token.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotar("refresh-caducado"));
        verify(tokenRepository, never()).save(any());
        verify(tokenRepository, never()).revocarFamilia(anyString());
    }

    @Test
    void rotar_tokenVersionDesfasada_lanza() {
        // El usuario cambio su password/rol (tokenVersion subio) tras emitir este refresh.
        Usuario usuario = usuarioActivo();
        usuario.setTokenVersion(2);
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setFamilia("familia-1");
        token.setTokenVersion(1);
        token.setRevocado(false);
        token.setExpiraEn(LocalDateTime.now().plusDays(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotar("refresh-obsoleto"));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void revocar_tokenExistente_loMarcaRevocado() {
        RefreshToken token = new RefreshToken();
        token.setRevocado(false);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.revocar("refresh-a-revocar");

        assertTrue(token.isRevocado());
        verify(tokenRepository).save(token);
    }

    @Test
    void revocar_tokenInexistente_noLanza() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.revocar("desconocido"));
        verify(tokenRepository, never()).save(any());
    }
}
