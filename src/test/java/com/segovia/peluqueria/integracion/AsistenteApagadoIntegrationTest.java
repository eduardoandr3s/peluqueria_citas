package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Con el asistente apagado ({@code spring.ai.model.chat=none}, que es el valor por defecto y
 * el que usan los tests) su dominio no se registra. Este test fija las dos mitades de ese
 * comportamiento, que son justo las que se rompieron al probarlo a mano:
 *
 * <ol>
 *   <li>La aplicación <strong>arranca igual</strong> sin API key. Si el contexto no cargara,
 *       este test ni llegaría a ejecutarse.</li>
 *   <li>La ruta responde <strong>404 y no 500</strong>. Sin el handler de
 *       {@code NoResourceFoundException} la tragaba el handler genérico de {@code Exception}
 *       y salía «error interno del servidor», que es mentira: el servidor está perfectamente,
 *       es la ruta la que no existe. El cliente distingue por el estado si el asistente no
 *       está desplegado (404) o si ha fallado (503), y mostraba el mensaje equivocado.</li>
 * </ol>
 */
class AsistenteApagadoIntegrationTest extends AbstractIntegrationTest {

    @Test
    void conElAsistenteApagadoLaRutaResponde404YNo500() {
        ResponseEntity<Map> respuesta = rest.postForEntity(
                url("/api/asistente"),
                Map.of("mensaje", "cuanto vale un corte?", "historial", java.util.List.of()),
                Map.class);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertTrue(respuesta.getBody().containsKey("error"));
    }

    /**
     * El handler es general, no algo del asistente: cualquier ruta inexistente que la
     * seguridad deje pasar responde 404. Se usa uno de los prefijos publicos porque
     * {@code anyRequest().authenticated()} corta antes las rutas no permitidas.
     */
    @Test
    void otraRutaInexistenteQueLaSeguridadPermiteTambienResponde404() {
        ResponseEntity<Map> respuesta = rest.getForEntity(url("/api/auth/no-existe-esto"), Map.class);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
    }

    /**
     * Contrapunto del anterior, y no es un descuido: una ruta inexistente que ademas no es
     * publica responde 403 y no 404, porque Spring Security la rechaza antes de llegar al
     * dispatcher. Es lo que interesa — a un anonimo no se le confirma que rutas existen.
     */
    @Test
    void unaRutaInexistenteNoPublicaResponde403YNoRevelaSiExiste() {
        ResponseEntity<Map> respuesta = rest.getForEntity(url("/api/no-existe-esto"), Map.class);

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
