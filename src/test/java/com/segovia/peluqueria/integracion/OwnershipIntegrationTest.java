package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
        LocalDateTime fechaCita = proximoLunesALas(10);
        var headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);
        ResponseEntity<Map> citaResp = rest.exchange(url("/api/citas"), HttpMethod.POST,
                new HttpEntity<>(Map.of("servicioId", idServicio,
                        "fechaHora", fechaCita.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), headersA),
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
        LocalDateTime otraHora = fechaCita.plusHours(1);
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

    /**
     * El avatar no se protege por rol (cada usuario sube el suyo), asi que la unica
     * garantia de que la feature no ha abierto un agujero es esta: A no puede tocar
     * el de B.
     */
    @Test
    void ownershipAvatar() {
        String emailA = "own_avatar_a@test.com";
        String passA = "PassA1234!";
        registrarUsuario(emailA, passA);
        String tokenA = (String) login(emailA, passA).get("token");

        String emailB = "own_avatar_b@test.com";
        String passB = "PassB5678!";
        registrarUsuario(emailB, passB);
        String tokenB = (String) login(emailB, passB).get("token");

        Integer idA = idDeUsuario(tokenA);
        Integer idB = idDeUsuario(tokenB);

        // A sube su propio avatar → 200, y la respuesta ya trae la URL.
        ResponseEntity<Map> propio = rest.exchange(url("/api/usuarios/" + idA + "/avatar"),
                HttpMethod.POST, peticionAvatar(tokenA), Map.class);
        assertEquals(HttpStatus.OK, propio.getStatusCode());
        assertNotNull(propio.getBody().get("urlAvatar"), "Subir el avatar debe devolver su URL");

        // A intenta subirle un avatar a B → 403.
        ResponseEntity<String> ajeno = rest.exchange(url("/api/usuarios/" + idB + "/avatar"),
                HttpMethod.POST, peticionAvatar(tokenA), String.class);
        assertEquals(HttpStatus.FORBIDDEN, ajeno.getStatusCode(),
                "User A no debe poder subir el avatar de User B");

        // Y tampoco borrarlo.
        var headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);
        ResponseEntity<String> borradoAjeno = rest.exchange(url("/api/usuarios/" + idB + "/avatar"),
                HttpMethod.DELETE, new HttpEntity<>(headersA), String.class);
        assertEquals(HttpStatus.FORBIDDEN, borradoAjeno.getStatusCode(),
                "User A no debe poder borrar el avatar de User B");

        // El ADMIN si puede con el de cualquiera.
        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> borradoAdmin = rest.exchange(url("/api/usuarios/" + idA + "/avatar"),
                HttpMethod.DELETE, new HttpEntity<>(headersAdmin), Map.class);
        assertEquals(HttpStatus.OK, borradoAdmin.getStatusCode());
        assertNull(borradoAdmin.getBody().get("urlAvatar"));

        // El listado no firma avatares (una firma por fila seria una llamada por fila).
        ResponseEntity<String> listado = rest.exchange(url("/api/usuarios"),
                HttpMethod.GET, new HttpEntity<>(headersAdmin), String.class);
        assertEquals(HttpStatus.OK, listado.getStatusCode());
        assertFalse(listado.getBody().contains("\"urlAvatar\":\"http"),
                "El listado de usuarios no debe traer URLs de avatar");
    }

    private Integer idDeUsuario(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.exchange(url("/api/usuarios/me"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (Integer) resp.getBody().get("idUsuario");
    }

    /** Multipart con un JPEG minimo: lo que valida el servidor son los primeros bytes. */
    private HttpEntity<MultiValueMap<String, Object>> peticionAvatar(String token) {
        byte[] datos = new byte[64];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;

        var recurso = new ByteArrayResource(datos) {
            @Override
            public String getFilename() {
                return "avatar.jpg";
            }
        };

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("imagen", recurso);

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(cuerpo, headers);
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
