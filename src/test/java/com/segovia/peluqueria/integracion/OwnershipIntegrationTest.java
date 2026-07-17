package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private Integer idServicio;

    @BeforeEach
    void setUp() {
        tokenAdmin = crearAdmin("ownership_admin@test.com");
        idServicio = crearServicio(tokenAdmin);
    }

    @Test
    void ownershipCitas() {
        // Registrar user A (cliente)
        String emailA = "own_usera@test.com";
        String passA = "PassA1234!";
        registrarUsuario(emailA, passA);
        String tokenA = (String) login(emailA, passA).get("token");

        // Registrar user B
        String emailB = "own_userb@test.com";
        String passB = "PassB5678!";
        registrarUsuario(emailB, passB);
        String tokenB = (String) login(emailB, passB).get("token");

        // User A crea una cita
        LocalDateTime maniana = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);
        ResponseEntity<Map> citaResp = rest.exchange(url("/api/citas"), HttpMethod.POST,
                new HttpEntity<>(Map.of("servicioId", idServicio,
                        "fechaHora", maniana.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), headersA),
                Map.class);
        assertEquals(HttpStatus.OK, citaResp.getStatusCode());
        Integer idCita = (Integer) citaResp.getBody().get("idCita");

        // Verificar que ninguna respuesta contiene "password"
        String bodyStr = citaResp.getBody().toString();
        assertFalse(bodyStr.toLowerCase().contains("password"),
                "La respuesta no debe contener el campo password");

        // User B intenta leer la cita de User A → 403
        var headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenB);
        ResponseEntity<String> citaBResp = rest.exchange(url("/api/citas/" + idCita),
                HttpMethod.GET, new HttpEntity<>(headersB), String.class);
        assertEquals(HttpStatus.FORBIDDEN, citaBResp.getStatusCode(),
                "User B no debe poder ver la cita de User A");

        // User B intenta EDITAR (PUT) la cita de User A → 403
        LocalDateTime otraHora = maniana.plusHours(1);
        ResponseEntity<String> editBResp = rest.exchange(url("/api/citas/" + idCita),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("fechaHora", otraHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), headersB),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, editBResp.getStatusCode(),
                "User B no debe poder editar la cita de User A");

        // ADMIN puede leer cualquier cita
        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> citaAdminResp = rest.exchange(url("/api/citas/" + idCita),
                HttpMethod.GET, new HttpEntity<>(headersAdmin), Map.class);
        assertEquals(HttpStatus.OK, citaAdminResp.getStatusCode());
        assertEquals(idCita, citaAdminResp.getBody().get("idCita"));

        // Ninguna respuesta de usuario debe filtrar el password: ni el login ni /usuarios/me
        ResponseEntity<String> meResp = rest.exchange(url("/api/usuarios/me"),
                HttpMethod.GET, new HttpEntity<>(headersA), String.class);
        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertFalse(meResp.getBody().toLowerCase().contains("password"),
                "La respuesta de /usuarios/me no debe contener el campo password");
    }

    private void registrarUsuario(String email, String password) {
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", email.split("@")[0], "email", email, "password", password, "telefono", "600000099"),
                Map.class);
    }

    private Map<String, Object> login(String email, String password) {
        return rest.postForEntity(url("/api/auth/login"),
                Map.of("email", email, "password", password), Map.class).getBody();
    }

    private String crearAdmin(String email) {
        String password = "Admin123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Admin OWN", "email", email, "password", password, "telefono", "600000098"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", email);
        var loginResp = rest.postForEntity(url("/api/auth/login"),
                Map.of("email", email, "password", password), Map.class);
        return (String) loginResp.getBody().get("token");
    }

    private Integer crearServicio(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.exchange(url("/api/servicios"), HttpMethod.POST,
                new HttpEntity<>(Map.of("nombre", "Corte OWN", "descripcion", "Test",
                        "precio", 25.0, "duracion", 30), headers), Map.class);
        return (Integer) resp.getBody().get("idServicio");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
