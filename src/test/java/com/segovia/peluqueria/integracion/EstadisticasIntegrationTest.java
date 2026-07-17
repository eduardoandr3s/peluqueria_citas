package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void sinParametrosUsaUltimos30DiasPorDefecto() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> resp = rest.exchange(url("/api/estadisticas"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody().get("ingresos"));
        assertNotNull(resp.getBody().get("citasPorEstado"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingresosExcluyenReembolsadosYDesglosanPorMetodo() {
        Integer usuarioId = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuarios WHERE email = ?", Integer.class, "est_admin@test.com");
        Integer servCorte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) VALUES ('Corte EST', 30, 30, true) RETURNING id_servicio",
                Integer.class);
        Integer servTinte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) VALUES ('Tinte EST', 50, 60, true) RETURNING id_servicio",
                Integer.class);

        // Citas de junio 2026: Corte tiene 2 no anuladas + 1 anulada; Tinte 1 no anulada.
        Integer c1 = insertarCita(usuarioId, servCorte, "2026-06-10T10:00:00", "CONFIRMADA");
        Integer c2 = insertarCita(usuarioId, servCorte, "2026-06-11T10:00:00", "CONFIRMADA");
        insertarCita(usuarioId, servCorte, "2026-06-12T10:00:00", "ANULADA");
        Integer c4 = insertarCita(usuarioId, servTinte, "2026-06-13T10:00:00", "CONFIRMADA");

        // 2 pagos PAGADO (TARJETA 30 + EFECTIVO 20) y 1 REEMBOLSADO (TARJETA 50, no debe contar).
        insertarPago(c1, "30.00", "TARJETA", "PAGADO", "2026-06-10T11:00:00");
        insertarPago(c2, "20.00", "EFECTIVO", "PAGADO", "2026-06-11T11:00:00");
        insertarPago(c4, "50.00", "TARJETA", "REEMBOLSADO", "2026-06-13T11:00:00");

        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> resp = rest.exchange(url("/api/estadisticas?desde=2026-06-01&hasta=2026-06-30"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Map<String, Object> ingresos = (Map<String, Object>) resp.getBody().get("ingresos");
        assertEquals(0, new BigDecimal("50.00").compareTo(new BigDecimal(ingresos.get("total").toString())),
                "El total debe excluir el pago reembolsado (30 + 20 = 50)");
        Map<String, Object> porMetodo = (Map<String, Object>) ingresos.get("porMetodoPago");
        assertEquals(0, new BigDecimal("30.00").compareTo(new BigDecimal(porMetodo.get("TARJETA").toString())),
                "TARJETA debe sumar solo el pago PAGADO, no el reembolsado");
        assertEquals(0, new BigDecimal("20.00").compareTo(new BigDecimal(porMetodo.get("EFECTIVO").toString())));

        // topServicios excluye la cita ANULADA: Corte queda primero con 2 citas no anuladas.
        List<Map<String, Object>> top = (List<Map<String, Object>>) resp.getBody().get("topServicios");
        assertEquals("Corte EST", top.get(0).get("nombre"));
        assertEquals(2, ((Number) top.get(0).get("total")).intValue());
    }

    private Integer insertarCita(Integer usuarioId, Integer servicioId, String fechaHora, String estado) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, fecha_hora, estado) VALUES (?, ?, CAST(? AS TIMESTAMP), ?) RETURNING id_cita",
                Integer.class, usuarioId, servicioId, fechaHora, estado);
    }

    private void insertarPago(Integer citaId, String monto, String metodo, String estado, String fechaPago) {
        jdbcTemplate.update(
                "INSERT INTO pagos (cita_id, monto, metodo_pago, estado_pago, fecha_creacion, fecha_pago) "
                        + "VALUES (?, CAST(? AS NUMERIC), ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))",
                citaId, monto, metodo, estado, fechaPago, fechaPago);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
