package com.segovia.peluqueria.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private static final String RECUPERAR = "/api/auth/recuperar";
    private static final String ASISTENTE = "/api/asistente";

    private RateLimitFilter filtro;
    private FilterChain cadena;

    @BeforeEach
    void setUp() {
        // Cupos pequenos y distintos entre si, para que un cruce de buckets se vea.
        filtro = new RateLimitFilter(2, 15, 3, 60);
        cadena = mock(FilterChain.class);
    }

    private MockHttpServletRequest peticion(String ruta, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ruta);
        request.setRemoteAddr(ip);
        return request;
    }

    private int enviar(String ruta, String ip) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(peticion(ruta, ip), response, cadena);
        return response.getStatus();
    }

    @Test
    void dentroDelCupoDejaPasar() throws Exception {
        assertEquals(HttpStatus.OK.value(), enviar(ASISTENTE, "1.1.1.1"));
        verify(cadena).doFilter(any(), any());
    }

    @Test
    void alAgotarElCupoResponde429YNoSigueLaCadena() throws Exception {
        enviar(ASISTENTE, "1.1.1.1");
        enviar(ASISTENTE, "1.1.1.1");
        enviar(ASISTENTE, "1.1.1.1");

        reset(cadena);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(peticion(ASISTENTE, "1.1.1.1"), response, cadena);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("Demasiadas solicitudes"));
        verifyNoInteractions(cadena);
    }

    /**
     * La razon de ser del cambio a cupos por ruta: quemar el del asistente (que es publico y
     * cuesta tokens) no puede dejar a nadie sin poder recuperar su contrasena.
     */
    @Test
    void elCupoDelAsistenteNoConsumeElDeRecuperarContrasena() throws Exception {
        for (int i = 0; i < 5; i++) {
            enviar(ASISTENTE, "1.1.1.1");
        }

        assertEquals(HttpStatus.OK.value(), enviar(RECUPERAR, "1.1.1.1"));
    }

    @Test
    void cadaRutaAplicaSuPropioCupo() throws Exception {
        // Recuperar tiene cupo 2; la tercera se corta.
        assertEquals(HttpStatus.OK.value(), enviar(RECUPERAR, "2.2.2.2"));
        assertEquals(HttpStatus.OK.value(), enviar(RECUPERAR, "2.2.2.2"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), enviar(RECUPERAR, "2.2.2.2"));

        // El asistente tiene cupo 3: la tercera todavia pasa.
        assertEquals(HttpStatus.OK.value(), enviar(ASISTENTE, "2.2.2.2"));
        assertEquals(HttpStatus.OK.value(), enviar(ASISTENTE, "2.2.2.2"));
        assertEquals(HttpStatus.OK.value(), enviar(ASISTENTE, "2.2.2.2"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), enviar(ASISTENTE, "2.2.2.2"));
    }

    @Test
    void cadaIpTieneSuPropioCupo() throws Exception {
        for (int i = 0; i < 4; i++) {
            enviar(ASISTENTE, "3.3.3.3");
        }

        assertEquals(HttpStatus.OK.value(), enviar(ASISTENTE, "4.4.4.4"));
    }

    /** Tras el proxy de Render la IP real viene en la cabecera, no en remoteAddr. */
    @Test
    void distingueClientesPorXForwardedFor() throws Exception {
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest request = peticion(ASISTENTE, "10.0.0.1");
            request.addHeader("X-Forwarded-For", "5.5.5.5, 10.0.0.1");
            filtro.doFilter(request, new MockHttpServletResponse(), cadena);
        }

        MockHttpServletRequest otro = peticion(ASISTENTE, "10.0.0.1");
        otro.addHeader("X-Forwarded-For", "6.6.6.6");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(otro, response, cadena);

        assertEquals(HttpStatus.OK.value(), response.getStatus(),
                "dos clientes tras el mismo proxy no deben compartir cupo");
    }

    @Test
    void noLimitaRutasAjenas() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.OK.value(), enviar("/api/citas", "7.7.7.7"));
        }
    }

    @Test
    void noLimitaOtrosMetodos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ASISTENTE);
        request.setRemoteAddr("8.8.8.8");
        assertTrue(filtro.shouldNotFilter(request));
    }
}
