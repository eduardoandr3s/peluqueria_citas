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
 * El cierre de citas y lo que el rol PELUQUERO puede tocar, de punta a punta.
 *
 * <p>Las reglas de quien llega a que cita NO estan en SecurityConfig ({@code /api/citas/**}
 * es "cualquiera autenticado") sino en CitaService, porque dependen de la ficha vinculada a
 * la cuenta. Por eso se comprueban por HTTP: un test unitario del servicio no demuestra que
 * la cadena de filtros deje pasar exactamente lo mismo.
 */
class CierreCitaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenLalo;
    private String tokenPepe;
    private String tokenCliente;
    private Integer clienteId;
    private Integer fichaLalo;
    private Integer fichaPepe;
    private Integer servCorte;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM pagos");
        jdbcTemplate.update("DELETE FROM citas");
        jdbcTemplate.update("UPDATE peluqueros SET usuario_id = NULL");

        tokenAdmin = registrarConRol("cierre_admin@test.com", "Admin123!", "ADMIN");
        tokenLalo = registrarConRol("cierre_lalo@test.com", "Lalo1234!", "PELUQUERO");
        tokenPepe = registrarConRol("cierre_pepe@test.com", "Pepe1234!", "PELUQUERO");
        tokenCliente = registrarConRol("cierre_cliente@test.com", "Cliente123!", "USER");
        clienteId = idUsuario("cierre_cliente@test.com");

        fichaLalo = crearFicha("Lalo CIERRE", idUsuario("cierre_lalo@test.com"), "20.00");
        fichaPepe = crearFicha("Pepe CIERRE", idUsuario("cierre_pepe@test.com"), "10.00");

        servCorte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Corte CIERRE', 30, 30, true) RETURNING id_servicio", Integer.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void elPeluqueroCierraSuCitaYSeCongelaElImporte() {
        Integer citaId = citaPasada(fichaLalo);

        ResponseEntity<Map> resp = cerrar(citaId, tokenLalo,
                Map.of("estado", "COMPLETADA", "observaciones", "Corte y lavado", "clienteContactado", false));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertEquals("COMPLETADA", body.get("estado"));
        assertEquals("Corte y lavado", body.get("observaciones"));
        assertEquals(0, new BigDecimal("30.00").compareTo(new BigDecimal(body.get("precioAplicado").toString())));
        assertEquals(0, new BigDecimal("20.00")
                .compareTo(new BigDecimal(body.get("comisionPorcentajeAplicado").toString())));
        assertNotNull(body.get("fechaCierre"));
        assertEquals("PROD PELUQUERO", body.get("cerradaPor"));
    }

    @Test
    void unPeluqueroNoPuedeCerrarLaCitaDeUnCompanero() {
        Integer citaDePepe = citaPasada(fichaPepe);

        assertEquals(HttpStatus.FORBIDDEN,
                cerrar(citaDePepe, tokenLalo, Map.of("estado", "COMPLETADA")).getStatusCode());
        // Ni verla.
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange(url("/api/citas/" + citaDePepe),
                HttpMethod.GET, new HttpEntity<>(cabecera(tokenLalo)), String.class).getStatusCode());
    }

    @Test
    void elClienteAnulaLaSuyaPeroNoLaPuedeDarPorRealizada() {
        Integer citaId = citaPasada(fichaLalo);

        assertEquals(HttpStatus.FORBIDDEN,
                cerrar(citaId, tokenCliente, Map.of("estado", "COMPLETADA")).getStatusCode());

        ResponseEntity<Map> anulada = cerrar(citaId, tokenCliente,
                Map.of("estado", "ANULADA", "observaciones", "Me surgio un viaje"));
        assertEquals(HttpStatus.OK, anulada.getStatusCode());
        assertEquals("ANULADA", anulada.getBody().get("estado"));
        // Las notas internas y la comision no viajan al cliente aunque la cita sea suya.
        assertNull(anulada.getBody().get("observaciones"));
        assertNull(anulada.getBody().get("comisionPorcentajeAplicado"));
    }

    @Test
    void marcarCompletadaPorElPutDeSiempreDevuelve400() {
        Integer citaId = citaPasada(fichaLalo);

        ResponseEntity<Map> resp = rest.exchange(url("/api/citas/" + citaId), HttpMethod.PUT,
                new HttpEntity<>(Map.of("estado", "COMPLETADA"), cabecera(tokenAdmin)), Map.class);

        // Si el PUT dejara completar, quedarian citas completadas sin importe congelado.
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void unCierreYaHechoSoloLoCorrigeElAdmin() {
        Integer citaId = citaPasada(fichaLalo);
        assertEquals(HttpStatus.OK, cerrar(citaId, tokenLalo, Map.of("estado", "COMPLETADA")).getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN,
                cerrar(citaId, tokenLalo, Map.of("estado", "NO_ASISTIO")).getStatusCode());

        ResponseEntity<Map> corregida = cerrar(citaId, tokenAdmin,
                Map.of("estado", "NO_ASISTIO", "observaciones", "Me confundi de cita"));
        assertEquals(HttpStatus.OK, corregida.getStatusCode());
        // Un cierre corregido deja de sumar en la produccion.
        assertNull(corregida.getBody().get("precioAplicado"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void elListadoDelPeluqueroEsSuAgendaYNoLaDeLaCasa() {
        citaPasada(fichaLalo);
        citaPasada(fichaPepe);
        citaPasada(fichaPepe);

        ResponseEntity<Map> resp = rest.exchange(url("/api/citas?size=50"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenLalo)), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Map<String, Object>> citas = (List<Map<String, Object>>) resp.getBody().get("content");
        assertEquals(1, citas.size());
        assertEquals("Lalo CIERRE", ((Map<String, Object>) citas.get(0).get("peluquero")).get("nombre"));

        // El admin sigue viendo las tres.
        ResponseEntity<Map> comoAdmin = rest.exchange(url("/api/citas?size=50"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenAdmin)), Map.class);
        assertEquals(3, ((List<Map<String, Object>>) comoAdmin.getBody().get("content")).size());
    }

    @Test
    void unPeluqueroNoLlegaALoQueEsDelAdmin() {
        assertEquals(HttpStatus.FORBIDDEN, get("/api/usuarios", tokenLalo));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/estadisticas", tokenLalo));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/pagos", tokenLalo));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/peluqueros/gestion", tokenLalo));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/peluqueros/" + fichaPepe + "/comisiones", tokenLalo));
        // Pero si a la lista de peluqueros de siempre, que es de cualquier autenticado.
        assertEquals(HttpStatus.OK, get("/api/peluqueros", tokenLalo));
    }

    // ---- Helpers ----

    private Integer citaPasada(Integer fichaId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, peluquero_id, fecha_hora, estado) "
                        + "VALUES (?, ?, ?, CAST('2026-05-04T10:00:00' AS TIMESTAMP), 'CONFIRMADA') RETURNING id_cita",
                Integer.class, clienteId, servCorte, fichaId);
    }

    private ResponseEntity<Map> cerrar(Integer citaId, String token, Map<String, Object> cuerpo) {
        return rest.exchange(url("/api/citas/" + citaId + "/cierre"), HttpMethod.PATCH,
                new HttpEntity<>(cuerpo, cabecera(token)), Map.class);
    }

    private HttpStatusCode get(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(cabecera(token)), String.class)
                .getStatusCode();
    }

    private Integer crearFicha(String nombre, Integer usuarioId, String comision) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO peluqueros (nombre, activo, usuario_id, comision_porcentaje) "
                        + "VALUES (?, true, ?, CAST(? AS NUMERIC)) RETURNING id_peluquero",
                Integer.class, nombre, usuarioId, comision);
    }

    private String registrarConRol(String email, String password, String rol) {
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "PROD " + rol, "email", email, "password", password, "telefono", "600000008"),
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
