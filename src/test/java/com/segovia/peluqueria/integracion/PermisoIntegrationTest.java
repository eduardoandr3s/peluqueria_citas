package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los permisos por rol, de punta a punta.
 *
 * <p>Se prueba por HTTP porque el reparto esta en dos sitios que un test unitario no
 * junta: SecurityConfig deja llegar al servicio a quien PODRIA tener el permiso, y el
 * servicio decide si de verdad lo tiene. Lo que importa demostrar es que encender un flag
 * no abre nada que el rol no permitiera ya, y que apagarlo devuelve las cosas a como
 * estaban.
 *
 * <p>Los cambios se hacen SIEMPRE por el endpoint y nunca con un UPDATE directo: el
 * servicio cachea el estado en memoria y una fila escrita por detras no lo invalidaria.
 */
class PermisoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenLaura;
    private String tokenCliente;
    private Integer clienteId;
    private Integer fichaLaura;
    private Integer fichaAjena;
    private Integer servCorte;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM pagos");
        jdbcTemplate.update("DELETE FROM citas");
        jdbcTemplate.update("UPDATE peluqueros SET usuario_id = NULL");

        tokenAdmin = registrarConRol("perm_admin@test.com", "Admin123!", "ADMIN");
        tokenLaura = registrarConRol("perm_laura@test.com", "Laura123!", "PELUQUERO");
        tokenCliente = registrarConRol("perm_cliente@test.com", "Cliente123!", "USER");
        clienteId = idUsuario("perm_cliente@test.com");

        fichaLaura = crearFicha("Laura PERM", idUsuario("perm_laura@test.com"));
        fichaAjena = crearFicha("Companero PERM", null);

        servCorte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Corte PERM', 30, 30, true) RETURNING id_servicio", Integer.class);

        // La cache vive en el bean, no en la tabla: se deja todo apagado por el endpoint
        // para que un test no herede lo que encendio el anterior.
        apagarTodo();
    }

    @Test
    void conElPermisoApagadoUnPeluqueroNoPuedeCobrarEnEfectivo() {
        Integer cita = citaPasada(fichaLaura);

        ResponseEntity<Map> resp = cobrar(cita, tokenLaura);

        // 403 y no 401: la ruta le deja llegar por su rol, es el permiso el que le para.
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        // Y el admin sigue pudiendo, con el flag apagado: los permisos no le aplican.
        assertEquals(HttpStatus.OK, cobrar(cita, tokenAdmin).getStatusCode());
    }

    @Test
    void encendidoElPeluqueroCobraLaSuyaPeroNoLaDeUnCompanero() {
        encender("PAGO_MANUAL_REGISTRAR");

        Integer suya = citaPasada(fichaLaura);
        Integer ajena = citaPasada(fichaAjena);

        assertEquals(HttpStatus.OK, cobrar(suya, tokenLaura).getStatusCode());
        // El permiso dice que su rol puede cobrar, no que pueda cobrar lo de otro.
        assertEquals(HttpStatus.FORBIDDEN, cobrar(ajena, tokenLaura).getStatusCode());
    }

    @Test
    void unClienteNoCobraNadaAunqueSeEnciendaElPermiso() {
        encender("PAGO_MANUAL_REGISTRAR");
        Integer cita = citaPasada(fichaLaura);

        // La regla de oro: el flag ESTRECHA, nunca abre. A un USER lo para SecurityConfig
        // antes de que el permiso llegue siquiera a consultarse.
        assertEquals(HttpStatus.FORBIDDEN, cobrar(cita, tokenCliente).getStatusCode());
    }

    @Test
    void apagarloOtraVezLeQuitaElCobro() {
        encender("PAGO_MANUAL_REGISTRAR");
        assertEquals(HttpStatus.OK, cobrar(citaPasada(fichaLaura), tokenLaura).getStatusCode());

        apagar("PAGO_MANUAL_REGISTRAR");

        // Sin esto, la cache convertiria un permiso en irrevocable hasta reiniciar.
        assertEquals(HttpStatus.FORBIDDEN, cobrar(citaPasada(fichaLaura), tokenLaura).getStatusCode());
    }

    @Test
    void elPermisoDeReprogramarTambienSeRespeta() {
        Integer cita = citaPasada(fichaLaura);
        Map<String, Object> nuevaFecha = Map.of("fechaHora", proximoLunesALas(11).toString());

        assertEquals(HttpStatus.FORBIDDEN, reprogramar(cita, tokenLaura, nuevaFecha).getStatusCode());

        encender("CITA_REPROGRAMAR");
        assertEquals(HttpStatus.OK, reprogramar(cita, tokenLaura, nuevaFecha).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cadaUnoVeSusPermisosYSoloElAdminVeLaMatriz() {
        encender("PAGO_MANUAL_REGISTRAR");

        ResponseEntity<Map> mios = rest.exchange(url("/api/permisos/mios"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenLaura)), Map.class);
        assertEquals(HttpStatus.OK, mios.getStatusCode());
        assertEquals("PELUQUERO", mios.getBody().get("rol"));
        assertEquals(List.of("PAGO_MANUAL_REGISTRAR"), mios.getBody().get("permisos"));

        // El cliente tambien puede preguntar por los suyos: no tiene ninguno.
        ResponseEntity<Map> delCliente = rest.exchange(url("/api/permisos/mios"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenCliente)), Map.class);
        assertTrue(((List<?>) delCliente.getBody().get("permisos")).isEmpty());

        // La matriz completa es solo del admin.
        assertEquals(HttpStatus.FORBIDDEN, get("/api/permisos", tokenLaura));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/permisos", tokenCliente));
        assertEquals(HttpStatus.OK, get("/api/permisos", tokenAdmin));
    }

    @Test
    @SuppressWarnings("unchecked")
    void laMatrizNoOfreceCasillasParaElAdminNiParaElCliente() {
        ResponseEntity<List> resp = rest.exchange(url("/api/permisos"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenAdmin)), List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Map<String, Object>> matriz = resp.getBody();
        assertFalse(matriz.isEmpty());
        for (Map<String, Object> fila : matriz) {
            Map<String, Object> roles = (Map<String, Object>) fila.get("roles");
            assertEquals(java.util.Set.of("PELUQUERO"), roles.keySet(),
                    "Un ADMIN los tiene todos por rol y un USER ninguno: no se configuran.");
        }
    }

    @Test
    void escribirUnPermisoParaUnRolQueNoSeConfiguraDevuelve400() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/permisos"), HttpMethod.PUT,
                new HttpEntity<>(cambios("ADMIN", "PAGO_MANUAL_REGISTRAR", false), cabecera(tokenAdmin)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void escribirUnaClaveQueNoExisteDevuelve400() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/permisos"), HttpMethod.PUT,
                new HttpEntity<>(cambios("PELUQUERO", "INVENTADO", true), cabecera(tokenAdmin)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // ---- Helpers ----

    private void encender(String clave) {
        assertEquals(HttpStatus.OK, escribir(clave, true).getStatusCode());
    }

    private void apagar(String clave) {
        assertEquals(HttpStatus.OK, escribir(clave, false).getStatusCode());
    }

    private void apagarTodo() {
        apagar("PAGO_MANUAL_REGISTRAR");
        apagar("CITA_REPROGRAMAR");
    }

    private ResponseEntity<List> escribir(String clave, boolean habilitado) {
        return rest.exchange(url("/api/permisos"), HttpMethod.PUT,
                new HttpEntity<>(cambios("PELUQUERO", clave, habilitado), cabecera(tokenAdmin)), List.class);
    }

    private Map<String, Object> cambios(String rol, String clave, boolean habilitado) {
        return Map.of("cambios", List.of(Map.of("rol", rol, "clave", clave, "habilitado", habilitado)));
    }

    private ResponseEntity<Map> cobrar(Integer citaId, String token) {
        return rest.exchange(url("/api/pagos/manual"), HttpMethod.POST,
                new HttpEntity<>(Map.of("citaId", citaId, "metodoPago", "EFECTIVO"), cabecera(token)), Map.class);
    }

    private ResponseEntity<Map> reprogramar(Integer citaId, String token, Map<String, Object> cuerpo) {
        return rest.exchange(url("/api/citas/" + citaId), HttpMethod.PUT,
                new HttpEntity<>(cuerpo, cabecera(token)), Map.class);
    }

    private HttpStatusCode get(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(cabecera(token)), String.class)
                .getStatusCode();
    }

    private Integer citaPasada(Integer fichaId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, peluquero_id, fecha_hora, estado) "
                        + "VALUES (?, ?, ?, CAST('2026-05-04T10:00:00' AS TIMESTAMP), 'CONFIRMADA') RETURNING id_cita",
                Integer.class, clienteId, servCorte, fichaId);
    }

    private Integer crearFicha(String nombre, Integer usuarioId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO peluqueros (nombre, activo, usuario_id, comision_porcentaje) "
                        + "VALUES (?, true, ?, 10.00) RETURNING id_peluquero",
                Integer.class, nombre, usuarioId);
    }

    private String registrarConRol(String email, String password, String rol) {
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "PERM " + rol, "email", email, "password", password, "telefono", "600000009"),
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
