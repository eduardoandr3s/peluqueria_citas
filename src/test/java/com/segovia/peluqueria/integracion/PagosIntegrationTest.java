package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Listado de pagos del panel (GET /api/pagos). Los datos se insertan por SQL en 2020 para no
 * cruzarse con los de otros tests de integracion, que comparten el mismo contenedor.
 */
class PagosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void setUp() {
        // El contenedor es compartido y este setUp corre en cada test: sin limpiar, los pagos
        // se irian acumulando y los conteos del primer test dejarian de cuadrar en el segundo.
        jdbcTemplate.execute("DELETE FROM password_reset_token");
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("DELETE FROM stripe_evento");
        jdbcTemplate.execute("DELETE FROM pagos");
        jdbcTemplate.execute("DELETE FROM citas");
        jdbcTemplate.execute("DELETE FROM servicios");
        jdbcTemplate.execute("DELETE FROM usuarios");

        String emailAdmin = "pagos_admin@test.com";
        String passAdmin = "Admin123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Admin PAGOS", "email", emailAdmin, "password", passAdmin,
                        "telefono", "600000030"), Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", emailAdmin);
        tokenAdmin = (String) rest.postForEntity(url("/api/auth/login"),
                Map.of("email", emailAdmin, "password", passAdmin), Map.class).getBody().get("token");

        String emailCliente = "pagos_cliente@test.com";
        String passCliente = "Pass1234!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Cliente PAGOS", "email", emailCliente, "password", passCliente,
                        "telefono", "600000031"), Map.class);
        tokenCliente = (String) rest.postForEntity(url("/api/auth/login"),
                Map.of("email", emailCliente, "password", passCliente), Map.class).getBody().get("token");

        Integer usuarioId = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuarios WHERE email = ?", Integer.class, emailCliente);
        Integer servicioId = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Corte PAGOS', 30, 30, true) RETURNING id_servicio", Integer.class);

        // Marzo 2020: dos pagos cobrados (tarjeta y efectivo) y uno aun pendiente.
        insertarPago(usuarioId, servicioId, "2020-03-10T10:00:00", "30.00", "TARJETA", "PAGADO",
                "2020-03-10T11:00:00");
        insertarPago(usuarioId, servicioId, "2020-03-11T10:00:00", "20.00", "EFECTIVO", "PAGADO",
                "2020-03-11T11:00:00");
        insertarPago(usuarioId, servicioId, "2020-03-12T10:00:00", "50.00", "TARJETA", "PENDIENTE", null);
        // Abril 2020: fuera del rango que piden los tests.
        insertarPago(usuarioId, servicioId, "2020-04-05T10:00:00", "99.00", "TARJETA", "PAGADO",
                "2020-04-05T11:00:00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listado_filtraPorRangoEstadoYMetodo() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);

        // Todo marzo: los 3 pagos del mes, incluido el pendiente (que se ubica por fecha de creacion).
        ResponseEntity<Map> marzo = rest.exchange(
                url("/api/pagos?desde=2020-03-01&hasta=2020-03-31&size=50"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, marzo.getStatusCode());
        assertEquals(3, ((Number) marzo.getBody().get("totalElements")).intValue());

        // Solo los cobrados: excluye el pendiente.
        ResponseEntity<Map> pagados = rest.exchange(
                url("/api/pagos?desde=2020-03-01&hasta=2020-03-31&estado=PAGADO&size=50"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(2, ((Number) pagados.getBody().get("totalElements")).intValue());

        // Cobrados con tarjeta: solo el de 30 €. Es el desglose que pinta el dashboard.
        ResponseEntity<Map> tarjeta = rest.exchange(
                url("/api/pagos?desde=2020-03-01&hasta=2020-03-31&estado=PAGADO&metodo=TARJETA&size=50"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) tarjeta.getBody().get("content");
        assertEquals(1, content.size());
        assertEquals(0, new java.math.BigDecimal("30.00")
                .compareTo(new java.math.BigDecimal(content.get(0).get("monto").toString())));
    }

    @Test
    void listado_ultimoDiaDelRangoIncluido() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);

        // 'hasta' es inclusive: el pago del dia 11 debe entrar aunque el rango acabe ese mismo dia.
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/pagos?desde=2020-03-11&hasta=2020-03-11&size=50"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, ((Number) resp.getBody().get("totalElements")).intValue());
    }

    @Test
    void listado_rangoInvertidoDevuelve400() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);

        ResponseEntity<String> resp = rest.exchange(
                url("/api/pagos?desde=2020-03-31&hasta=2020-03-01"),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void listado_clienteNoAdminRecibe403() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenCliente);

        ResponseEntity<String> resp = rest.exchange(url("/api/pagos"),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "El listado completo de pagos es solo para ADMIN");
    }

    private void insertarPago(Integer usuarioId, Integer servicioId, String fechaCita, String monto,
                              String metodo, String estado, String fechaPago) {
        Integer citaId = jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, fecha_hora, estado) "
                        + "VALUES (?, ?, CAST(? AS TIMESTAMP), 'CONFIRMADA') RETURNING id_cita",
                Integer.class, usuarioId, servicioId, fechaCita);
        jdbcTemplate.update(
                "INSERT INTO pagos (cita_id, monto, metodo_pago, estado_pago, fecha_creacion, fecha_pago) "
                        + "VALUES (?, CAST(? AS NUMERIC), ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))",
                citaId, monto, metodo, estado, fechaCita, fechaPago);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
