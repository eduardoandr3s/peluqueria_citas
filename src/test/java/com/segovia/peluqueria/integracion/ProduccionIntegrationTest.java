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

/**
 * Produccion y comision contra Postgres de verdad.
 *
 * <p>Existe porque las consultas de {@code ProduccionRepository} son SQL nativo con
 * {@code DATE_TRUNC}, {@code TO_CHAR} y cuatro JOIN: los tests unitarios las mockean, asi
 * que sin este fichero nadie comprueba que el SQL sea correcto ni que las reglas del
 * negocio (solo COMPLETADA, solo cobrada, importe congelado) esten bien escritas.
 */
class ProduccionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenPeluquero;
    private String tokenCliente;
    private Integer clienteId;
    private Integer peluqueroLalo;
    private Integer peluqueroPepe;
    private Integer servCorte;
    private Integer servTinte;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM pagos");
        jdbcTemplate.update("DELETE FROM citas");
        jdbcTemplate.update("DELETE FROM comisiones_servicio");
        jdbcTemplate.update("UPDATE peluqueros SET usuario_id = NULL");

        tokenAdmin = registrarConRol("prod_admin@test.com", "Admin123!", "ADMIN");
        tokenPeluquero = registrarConRol("prod_lalo@test.com", "Lalo1234!", "PELUQUERO");
        tokenCliente = registrarConRol("prod_cliente@test.com", "Cliente123!", "USER");
        clienteId = idUsuario("prod_cliente@test.com");

        // Lalo tiene cuenta y comisiona al 20%, con excepcion del tinte al 10%.
        peluqueroLalo = jdbcTemplate.queryForObject(
                "INSERT INTO peluqueros (nombre, activo, usuario_id, comision_porcentaje) "
                        + "VALUES ('Lalo PROD', true, ?, 20.00) RETURNING id_peluquero",
                Integer.class, idUsuario("prod_lalo@test.com"));
        peluqueroPepe = jdbcTemplate.queryForObject(
                "INSERT INTO peluqueros (nombre, activo, comision_porcentaje) "
                        + "VALUES ('Pepe PROD', true, 10.00) RETURNING id_peluquero",
                Integer.class);

        servCorte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Corte PROD', 30, 30, true) RETURNING id_servicio", Integer.class);
        servTinte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Tinte PROD', 50, 60, true) RETURNING id_servicio", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO comisiones_servicio (peluquero_id, servicio_id, porcentaje) VALUES (?, ?, 10.00)",
                peluqueroLalo, servTinte);
    }

    @Test
    @SuppressWarnings("unchecked")
    void produccionSumaSoloLoCompletadoYCobrado() {
        // Dos cortes de 30 al 20% y un tinte de 50 al 10%: 110 vendidos y 17 de comision.
        cerrada(servCorte, peluqueroLalo, "2026-05-04T10:00:00", "COMPLETADA", "30.00", "20.00", true);
        cerrada(servCorte, peluqueroLalo, "2026-05-05T10:00:00", "COMPLETADA", "30.00", "20.00", true);
        cerrada(servTinte, peluqueroLalo, "2026-06-08T10:00:00", "COMPLETADA", "50.00", "10.00", true);
        // Completada pero SIN cobrar: no suma en lo vendido, sale como pendiente.
        cerrada(servCorte, peluqueroLalo, "2026-06-09T10:00:00", "COMPLETADA", "30.00", "20.00", false);
        // No asistio y anulada: no cuentan de ninguna manera.
        cerrada(servCorte, peluqueroLalo, "2026-06-10T10:00:00", "NO_ASISTIO", null, null, false);
        cerrada(servCorte, peluqueroLalo, "2026-06-11T10:00:00", "ANULADA", null, null, false);
        // De otro peluquero: tampoco.
        cerrada(servCorte, peluqueroPepe, "2026-06-12T10:00:00", "COMPLETADA", "30.00", "10.00", true);

        Map<String, Object> body = getMap("/api/produccion/mia?desde=2026-05-01&hasta=2026-06-30", tokenPeluquero);

        assertEquals(3, ((Number) body.get("serviciosRealizados")).intValue());
        assertImporte("110.00", body.get("importeVendido"));
        // 30*0,20 + 30*0,20 + 50*0,10 = 6 + 6 + 5
        assertImporte("17.00", body.get("comision"));
        assertEquals(1, ((Number) body.get("serviciosSinCobrar")).intValue());
        assertImporte("30.00", body.get("importeSinCobrar"));

        // Desglose por servicio: el corte manda por importe (60 frente a 50).
        List<Map<String, Object>> porServicio = (List<Map<String, Object>>) body.get("porServicio");
        assertEquals(2, porServicio.size());
        assertEquals("Corte PROD", porServicio.get(0).get("etiqueta"));
        assertImporte("60.00", porServicio.get(0).get("importe"));
        assertImporte("12.00", porServicio.get(0).get("comision"));
        assertEquals("Tinte PROD", porServicio.get(1).get("etiqueta"));
        assertImporte("5.00", porServicio.get(1).get("comision"));

        // Desglose mensual, en orden y con la etiqueta YYYY-MM que arma DATE_TRUNC.
        List<Map<String, Object>> porMes = (List<Map<String, Object>>) body.get("porMes");
        assertEquals(List.of("2026-05", "2026-06"), porMes.stream().map(m -> m.get("etiqueta")).toList());
        assertImporte("60.00", porMes.get(0).get("importe"));
        assertImporte("50.00", porMes.get(1).get("importe"));
    }

    @Test
    void elImporteCongeladoManda_subirLaTarifaNoCambiaLoYaLiquidado() {
        cerrada(servCorte, peluqueroLalo, "2026-05-04T10:00:00", "COMPLETADA", "30.00", "20.00", true);

        // El corte pasa de 30 a 45 euros DESPUES de haberse liquidado esa cita.
        jdbcTemplate.update("UPDATE servicios SET precio = 45 WHERE id_servicio = ?", servCorte);

        Map<String, Object> body = getMap("/api/produccion/mia?desde=2026-05-01&hasta=2026-05-31", tokenPeluquero);

        assertImporte("30.00", body.get("importeVendido"));
        assertImporte("6.00", body.get("comision"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void comparativaDeLaPlantillaOrdenadaPorImporte() {
        cerrada(servCorte, peluqueroLalo, "2026-05-04T10:00:00", "COMPLETADA", "30.00", "20.00", true);
        cerrada(servTinte, peluqueroPepe, "2026-05-05T10:00:00", "COMPLETADA", "50.00", "10.00", true);

        ResponseEntity<List> resp = rest.exchange(url("/api/produccion?desde=2026-05-01&hasta=2026-05-31"),
                HttpMethod.GET, new HttpEntity<>(cabecera(tokenAdmin)), List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        List<Map<String, Object>> filas = resp.getBody();
        assertEquals(2, filas.size());
        assertEquals("Pepe PROD", filas.get(0).get("nombre"));
        assertImporte("50.00", filas.get(0).get("importeVendido"));
        assertEquals("Lalo PROD", filas.get(1).get("nombre"));
    }

    @Test
    void unPeluqueroNoVeLaProduccionDeOtroNiLaComparativa() {
        assertEquals(HttpStatus.FORBIDDEN, estado("/api/produccion/peluquero/" + peluqueroPepe, tokenPeluquero));
        assertEquals(HttpStatus.FORBIDDEN, estado("/api/produccion", tokenPeluquero));
        // Y el cliente no llega ni a la suya.
        assertEquals(HttpStatus.FORBIDDEN, estado("/api/produccion/mia", tokenCliente));
    }

    @Test
    void cuentaConRolPeluqueroYSinFichaVinculada_devuelve404() {
        jdbcTemplate.update("UPDATE peluqueros SET usuario_id = NULL WHERE id_peluquero = ?", peluqueroLalo);

        assertEquals(HttpStatus.NOT_FOUND, estado("/api/produccion/mia", tokenPeluquero));
    }

    @Test
    void unAdminConFichaVeSuPropiaProduccion() {
        // El caso del dueno que ademas corta pelo. No hace falta ningun sub-rol: el rol dice
        // que puede hacer y la ficha dice quien hace el trabajo, y se unen por usuario_id.
        // /produccion/mia resuelve la ficha desde la cuenta autenticada, no desde el rol.
        jdbcTemplate.update("UPDATE peluqueros SET usuario_id = ? WHERE id_peluquero = ?",
                idUsuario("prod_admin@test.com"), peluqueroPepe);
        cerrada(servTinte, peluqueroPepe, "2026-05-05T10:00:00", "COMPLETADA", "50.00", "10.00", true);

        Map<String, Object> body = getMap("/api/produccion/mia?desde=2026-05-01&hasta=2026-05-31", tokenAdmin);

        assertEquals("Pepe PROD", body.get("nombre"));
        assertImporte("50.00", body.get("importeVendido"));
        assertImporte("5.00", body.get("comision"));
    }

    @Test
    void adminVeLaProduccionDeCualquiera() {
        cerrada(servTinte, peluqueroPepe, "2026-05-05T10:00:00", "COMPLETADA", "50.00", "10.00", true);

        Map<String, Object> body = getMap(
                "/api/produccion/peluquero/" + peluqueroPepe + "?desde=2026-05-01&hasta=2026-05-31", tokenAdmin);

        assertEquals("Pepe PROD", body.get("nombre"));
        assertImporte("50.00", body.get("importeVendido"));
        assertImporte("5.00", body.get("comision"));
    }

    // ---- Helpers ----

    /** Cita ya cerrada, con el importe y la comision congelados como los deja el cierre. */
    private void cerrada(Integer servicioId, Integer peluqueroId, String fechaHora, String estado,
                         String precioAplicado, String comision, boolean cobrada) {
        Integer citaId = jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, peluquero_id, fecha_hora, estado, "
                        + "precio_aplicado, comision_porcentaje_aplicado, fecha_cierre) "
                        + "VALUES (?, ?, ?, CAST(? AS TIMESTAMP), ?, CAST(? AS NUMERIC), CAST(? AS NUMERIC), "
                        + "CAST(? AS TIMESTAMP)) RETURNING id_cita",
                Integer.class, clienteId, servicioId, peluqueroId, fechaHora, estado,
                precioAplicado, comision, fechaHora);
        if (cobrada) {
            jdbcTemplate.update(
                    "INSERT INTO pagos (cita_id, monto, metodo_pago, estado_pago, fecha_creacion, fecha_pago) "
                            + "VALUES (?, CAST(? AS NUMERIC), 'EFECTIVO', 'PAGADO', CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))",
                    citaId, precioAplicado, fechaHora, fechaHora);
        }
    }

    private String registrarConRol(String email, String password, String rol) {
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "PROD " + rol, "email", email, "password", password, "telefono", "600000009"),
                Map.class);
        if (!"USER".equals(rol)) {
            jdbcTemplate.update("UPDATE usuarios SET rol = ? WHERE email = ?", rol, email);
        }
        return (String) rest.postForEntity(url("/api/auth/login"),
                Map.of("email", email, "password", password), Map.class).getBody().get("token");
    }

    private Integer idUsuario(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuarios WHERE email = ?", Integer.class, email);
    }

    private HttpHeaders cabecera(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map> resp = rest.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(cabecera(token)), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "Respuesta inesperada de " + path);
        return resp.getBody();
    }

    private HttpStatusCode estado(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(cabecera(token)), String.class)
                .getStatusCode();
    }

    private void assertImporte(String esperado, Object real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(new BigDecimal(real.toString())),
                "Se esperaba " + esperado + " y llego " + real);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
