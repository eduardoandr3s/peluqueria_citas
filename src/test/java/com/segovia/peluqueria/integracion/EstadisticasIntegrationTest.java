package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EstadisticasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenUser;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        String emailUser = "est_user@test.com";
        String passUser = "User1234!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "User EST", "email", emailUser, "password", passUser, "telefono", "600000001"),
                Map.class);
        tokenUser = (String) rest.postForEntity(url("/api/auth/login"),
                Map.of("email", emailUser, "password", passUser), Map.class).getBody().get("token");

        String emailAdmin = "est_admin@test.com";
        String passAdmin = "Admin123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Admin EST", "email", emailAdmin, "password", passAdmin, "telefono", "600000002"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", emailAdmin);
        tokenAdmin = (String) rest.postForEntity(url("/api/auth/login"),
                Map.of("email", emailAdmin, "password", passAdmin), Map.class).getBody().get("token");
    }

    @Test
    void adminPuedeObtenerEstadisticas() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> resp = rest.exchange(url("/api/estadisticas?desde=2026-01-01&hasta=2026-12-31"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody().get("citasPorEstado"));
        assertNotNull(resp.getBody().get("ingresos"));
        assertNotNull(resp.getBody().get("topServicios"));
        assertNotNull(resp.getBody().get("nuevosClientes"));
    }

    @Test
    void userNoAdminRecibe403() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenUser);
        ResponseEntity<String> resp = rest.exchange(url("/api/estadisticas?desde=2026-01-01&hasta=2026-12-31"),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void fechaInvalidaDevuelve400() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> resp = rest.exchange(url("/api/estadisticas?desde=2026-12-31&hasta=2026-01-01"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
