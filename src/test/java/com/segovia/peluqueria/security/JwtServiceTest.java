package com.segovia.peluqueria.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // Clave de 256 bits (32 caracteres) para HMAC-SHA256
    private static final String TEST_SECRET = "claveSecretaDePruebaConAlMenos32Caracteres!";
    private static final long TEST_EXPIRATION = 86400000L; // 24 horas
    private static final long EXPIRED_EXPIRATION = 0L; // expira inmediatamente

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        setField(jwtService, "secret", TEST_SECRET);
        setField(jwtService, "expiration", TEST_EXPIRATION);
    }

    // Inyecta valores en campos privados (simula @Value de Spring)
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // --- generarToken y extraer datos ---

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

    // --- validacion ---

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
        // Crear un JwtService con expiracion 0 (expira inmediatamente)
        JwtService jwtServiceExpirado = new JwtService();
        setField(jwtServiceExpirado, "secret", TEST_SECRET);
        setField(jwtServiceExpirado, "expiration", EXPIRED_EXPIRATION);

        String token = jwtServiceExpirado.generarToken("carlos@test.com", "USER", 1);

        // Esperar un momento para asegurar que expire
        Thread.sleep(10);

        assertThrows(Exception.class,
                () -> jwtServiceExpirado.esTokenValido(token, "carlos@test.com"));
    }

    @Test
    void tokenManipulado_lanzaExcepcion() {
        String token = jwtService.generarToken("carlos@test.com", "USER", 1);

        // Manipular el token (cambiar un caracter en el payload)
        String tokenManipulado = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(Exception.class,
                () -> jwtService.esTokenValido(tokenManipulado, "carlos@test.com"));
    }

    @Test
    void tokenConOtraFirma_lanzaExcepcion() throws Exception {
        // Generar token con una clave diferente
        JwtService otroJwtService = new JwtService();
        setField(otroJwtService, "secret", "otraClaveSecretaMuyDiferenteYLarga!!");
        setField(otroJwtService, "expiration", TEST_EXPIRATION);

        String tokenConOtraFirma = otroJwtService.generarToken("carlos@test.com", "USER", 1);

        // Intentar validar con la clave original
        assertThrows(Exception.class,
                () -> jwtService.esTokenValido(tokenConOtraFirma, "carlos@test.com"));
    }
}
