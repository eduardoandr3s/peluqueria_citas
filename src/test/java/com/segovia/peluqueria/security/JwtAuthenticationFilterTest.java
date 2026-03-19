package com.segovia.peluqueria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;
    private FilterChain filterChain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        filterChain = mock(FilterChain.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();
    }

    @Test
    void request_sinHeader_continuaSinAutenticar() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).extraerEmail(any());
    }

    @Test
    void request_headerSinBearer_continuaSinAutenticar() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic abc123");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void request_conTokenValido_autenticaUsuario() throws ServletException, IOException {
        String token = "token.jwt.valido";
        request.addHeader("Authorization", "Bearer " + token);

        UserDetails userDetails = new User("carlos@test.com", "encriptada",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(jwtService.extraerEmail(token)).thenReturn("carlos@test.com");
        when(userDetailsService.loadUserByUsername("carlos@test.com")).thenReturn(userDetails);
        when(jwtService.esTokenValido(token, "carlos@test.com")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("carlos@test.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void request_conTokenInvalido_continuaSinAutenticar() throws ServletException, IOException {
        String token = "token.jwt.invalido";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.extraerEmail(token)).thenThrow(new RuntimeException("Token invalido"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void request_conTokenExpirado_continuaSinAutenticar() throws ServletException, IOException {
        String token = "token.jwt.expirado";
        request.addHeader("Authorization", "Bearer " + token);

        UserDetails userDetails = new User("carlos@test.com", "encriptada",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(jwtService.extraerEmail(token)).thenReturn("carlos@test.com");
        when(userDetailsService.loadUserByUsername("carlos@test.com")).thenReturn(userDetails);
        when(jwtService.esTokenValido(token, "carlos@test.com")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
