package com.segovia.peluqueria.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = "claveSecretaDePruebaConAlMenos32Caracteres!";
    private static final long TEST_EXPIRATION = 86400000L;
    private static final long EXPIRED_EXPIRATION = 0L;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        setField(jwtService, "secret", TEST_SECRET);
        setField(jwtService, "expiration", TEST_EXPIRATION);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void generarToken_yExtraerEmail_correcto() {
        String token = jwtService.generarToken("carlos@test.com", "USER", 1);

        String email = jwtService.extraerEmail(token);

        assertEquals("carlos@test.com", email);
    }

    @Test
    void generarToken_yExtraerRol_correcto() {
        String token = jwtService.generarToken("carlos@test.com", "ADMIN", 1);

        String rol = jwtService.extraerRol(token);

        assertEquals("ADMIN", rol);
    }

    @Test
    void generarToken_yExtraerIdUsuario_correcto() {
        String token = jwtService.generarToken("carlos@test.com", "USER", 42);

        Integer idUsuario = jwtService.extraerIdUsuario(token);

        assertEquals(42, idUsuario);
    }

    @Test
    void tokenValido_conEmailCorrecto_retornaTrue() {
        String token = jwtService.generarToken("carlos@test.com", "USER", 1);

        assertTrue(jwtService.esTokenValido(token, "carlos@test.com"));
    }

    @Test
    void tokenValido_conEmailIncorrecto_retornaFalse() {
        String token = jwtService.generarToken("carlos@test.com", "USER", 1);

        assertFalse(jwtService.esTokenValido(token, "otro@test.com"));
    }

    @Test
    void tokenExpirado_noEsValido() throws Exception {
        JwtService jwtServiceExpirado = new JwtService();
        setField(jwtServiceExpirado, "secret", TEST_SECRET);
        setField(jwtServiceExpirado, "expiration", EXPIRED_EXPIRATION);

        String token = jwtServiceExpirado.generarToken("carlos@test.com", "USER", 1);

        Thread.sleep(10);

        assertThrows(Exception.class,
                () -> jwtServiceExpirado.esTokenValido(token, "carlos@test.com"));
    }

    @Test
    void tokenManipulado_lanzaExcepcion() {
        String token = jwtService.generarToken("carlos@test.com", "USER", 1);

        String tokenManipulado = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(Exception.class,
                () -> jwtService.esTokenValido(tokenManipulado, "carlos@test.com"));
    }

    @Test
    void tokenConOtraFirma_lanzaExcepcion() throws Exception {
        JwtService otroJwtService = new JwtService();
        setField(otroJwtService, "secret", "otraClaveSecretaMuyDiferenteYLarga!!");
        setField(otroJwtService, "expiration", TEST_EXPIRATION);

        String tokenConOtraFirma = otroJwtService.generarToken("carlos@test.com", "USER", 1);

        assertThrows(Exception.class,
                () -> jwtService.esTokenValido(tokenConOtraFirma, "carlos@test.com"));
    }
}
