package com.segovia.peluqueria.security;

import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private UsuarioRepository usuarioRepository;
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        userDetailsService = new CustomUserDetailsService(usuarioRepository);
    }

    private Usuario crearUsuario(boolean activo, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setEmail("carlos@test.com");
        usuario.setPassword("encriptada123");
        usuario.setActivo(activo);
        usuario.setRol(rol);
        return usuario;
    }

    @Test
    void loadUserByUsername_exitoso_conRolCorrecto() {
        Usuario usuario = crearUsuario(true, Rol.ADMIN);
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("carlos@test.com");

        assertEquals("carlos@test.com", userDetails.getUsername());
        assertEquals("encriptada123", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_rolUser_tieneRolCorrecto() {
        Usuario usuario = crearUsuario(true, Rol.USER);
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("carlos@test.com");

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void loadUserByUsername_noExiste_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("noexiste@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("noexiste@test.com"));
    }

    @Test
    void loadUserByUsername_usuarioInactivo_retornaDeshabilitado() {
        Usuario usuario = crearUsuario(false, Rol.USER);
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("carlos@test.com");

        assertFalse(userDetails.isEnabled());
    }
}
