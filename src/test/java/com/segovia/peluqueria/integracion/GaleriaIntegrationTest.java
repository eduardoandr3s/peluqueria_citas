package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La galeria es el unico recurso que se escribe con multipart y se lee sin cuenta,
 * asi que lo que se comprueba aqui es justo esa asimetria: que el escaparate esta
 * abierto y que subir, ordenar y borrar es solo del ADMIN.
 */
class GaleriaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM galeria_fotos");
        tokenAdmin = crearAdmin("galeria_admin@test.com");
        tokenCliente = crearCliente("galeria_cliente@test.com");
    }

    @Test
    void galeriaSeLeeSinCuentaYSoloElAdminEscribe() {
        // El escaparate se ve sin token: es el caso de uso, no un descuido.
        ResponseEntity<List> anonimo = rest.getForEntity(url("/api/galeria"), List.class);
        assertEquals(HttpStatus.OK, anonimo.getStatusCode());

        // Subir sin token y como cliente: cerrado.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria"), HttpMethod.POST, multipart(null, true), String.class)
                        .getStatusCode(),
                "Subir a la galeria sin token debe estar cerrado");
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria"), HttpMethod.POST, multipart(tokenCliente, true), String.class)
                        .getStatusCode(),
                "Un cliente no debe poder subir a la galeria");

        // El ADMIN sube, y la respuesta ya trae las dos URLs.
        ResponseEntity<Map> subida = rest.exchange(url("/api/galeria"), HttpMethod.POST,
                multipart(tokenAdmin, true), Map.class);
        assertEquals(HttpStatus.OK, subida.getStatusCode());
        Integer idFoto = (Integer) subida.getBody().get("idFoto");
        assertNotNull(subida.getBody().get("urlImagen"));
        assertNotNull(subida.getBody().get("urlMiniatura"));
        assertNotEquals(subida.getBody().get("urlImagen"), subida.getBody().get("urlMiniatura"),
                "Con miniatura, la rejilla no debe acabar sirviendo la imagen grande");
        assertEquals(0, subida.getBody().get("orden"));

        // Y ya se ve desde fuera.
        assertEquals(1, rest.getForEntity(url("/api/galeria"), List.class).getBody().size());

        // Ordenar: cerrado al cliente, permitido al ADMIN.
        var headersCliente = new HttpHeaders();
        headersCliente.setBearerAuth(tokenCliente);
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.PUT,
                        new HttpEntity<>(Map.of("orden", 5), headersCliente), String.class).getStatusCode());

        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> editada = rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.PUT,
                new HttpEntity<>(Map.of("titulo", "Degradado con barba", "orden", 5), headersAdmin), Map.class);
        assertEquals(HttpStatus.OK, editada.getStatusCode());
        assertEquals("Degradado con barba", editada.getBody().get("titulo"));
        assertEquals(5, editada.getBody().get("orden"));

        // Borrar: igual.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.DELETE,
                        new HttpEntity<>(headersCliente), String.class).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT,
                rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.DELETE,
                        new HttpEntity<>(headersAdmin), String.class).getStatusCode());
        assertTrue(rest.getForEntity(url("/api/galeria"), List.class).getBody().isEmpty());
    }

    @Test
    void sinMiniaturaLaRejillaCaeALaImagenGrandeYUnFicheroFalsoSeRechaza() {
        ResponseEntity<Map> sinMiniatura = rest.exchange(url("/api/galeria"), HttpMethod.POST,
                multipart(tokenAdmin, false), Map.class);
        assertEquals(HttpStatus.OK, sinMiniatura.getStatusCode());
        assertEquals(sinMiniatura.getBody().get("urlImagen"), sinMiniatura.getBody().get("urlMiniatura"),
                "Sin miniatura el cliente debe recibir la grande, no un hueco");

        // Un ejecutable renombrado a .jpg: se mira el contenido, no la extension.
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("imagen", recurso(new byte[] { 0x4D, 0x5A, 0x00, 0x00 }, "trabajo.jpg"));
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        assertEquals(HttpStatus.BAD_REQUEST,
                rest.exchange(url("/api/galeria"), HttpMethod.POST,
                        new HttpEntity<>(cuerpo, headers), String.class).getStatusCode());
    }

    /** Multipart con JPEG minimo: lo que valida el servidor son los primeros bytes. */
    private HttpEntity<MultiValueMap<String, Object>> multipart(String token, boolean conMiniatura) {
        byte[] datos = new byte[64];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("imagen", recurso(datos, "trabajo.jpg"));
        if (conMiniatura) {
            cuerpo.add("miniatura", recurso(datos, "trabajo-mini.jpg"));
        }
        cuerpo.add("titulo", "Degradado");

        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(cuerpo, headers);
    }

    private ByteArrayResource recurso(byte[] datos, String nombre) {
        return new ByteArrayResource(datos) {
            @Override
            public String getFilename() {
                return nombre;
            }
        };
    }

    private String crearAdmin(String email) {
        String password = "Admin123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Admin GAL", "email", email, "password", password, "telefono", "600000097"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", email);
        return login(email, password);
    }

    private String crearCliente(String email) {
        String password = "Cliente123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Cliente GAL", "email", email, "password", password, "telefono", "600000096"),
                Map.class);
        return login(email, password);
    }

    private String login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(url("/api/auth/login"),
                Map.of("email", email, "password", password), Map.class);
        return (String) resp.getBody().get("token");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
