package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los modulos activables, de punta a punta.
 *
 * <p>Lo que hay que demostrar aqui, y no se puede demostrar con un test unitario, es lo que
 * separa un modulo de un permiso: <b>un modulo apagado tambien deja fuera al ADMIN</b>, y
 * responde 409 y no 403. Y ademas que apagar los pagos no pone la produccion a cero, que es
 * la trampa que este diseno tenia que evitar.
 *
 * <p>Los cambios se hacen SIEMPRE por el endpoint y nunca con un UPDATE directo: el
 * servicio cachea el estado en memoria y una fila escrita por detras no lo invalidaria.
 */
class ModuloIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenLaura;
    private String tokenCliente;
    private Integer clienteId;
    private Integer fichaLaura;
    private Integer servCorte;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM pagos");
        jdbcTemplate.update("DELETE FROM citas");
        jdbcTemplate.update("UPDATE peluqueros SET usuario_id = NULL");

        tokenAdmin = registrarConRol("mod_admin@test.com", "Admin123!", "ADMIN");
        tokenLaura = registrarConRol("mod_laura@test.com", "Laura123!", "PELUQUERO");
        tokenCliente = registrarConRol("mod_cliente@test.com", "Cliente123!", "USER");
        clienteId = idUsuario("mod_cliente@test.com");
        fichaLaura = crearFicha("Laura MOD", idUsuario("mod_laura@test.com"));

        servCorte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Corte MOD', 30, 30, true) RETURNING id_servicio", Integer.class);

        // Todo encendido por el endpoint, no por la tabla: la cache vive en el bean y un
        // test no puede heredar lo que apago el anterior.
        encenderTodo();
    }

    /**
     * La tabla se vacia al salir. El contenedor de Postgres es el MISMO para toda la suite y
     * los modulos apagados por un test sobrevivirian a su clase: el siguiente IT se
     * encontraria un negocio sin pagos y fallaria por algo que no esta probando. Sin fila
     * significa encendido, asi que vaciar es dejarlo como estaba.
     */
    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM modulos");
    }

    @Test
    @SuppressWarnings("unchecked")
    void losModulosActivosSeLeenSinToken() {
        // La app tiene pantallas publicas y necesita saber que pintar antes del login.
        ResponseEntity<Map> resp = rest.getForEntity(url("/api/modulos/activos"), Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<String> claves = (List<String>) resp.getBody().get("modulos");
        assertTrue(claves.containsAll(List.of("COMISIONES", "PAGOS", "PAGO_TARJETA", "PAGO_EFECTIVO")));
    }

    @Test
    void elCatalogoYSuEscrituraSonSoloDelAdmin() {
        assertEquals(HttpStatus.FORBIDDEN, get("/api/modulos", tokenLaura));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/modulos", tokenCliente));
        assertEquals(HttpStatus.OK, get("/api/modulos", tokenAdmin));

        // Apagar los pagos es mas grave que anular una cita: no lo toca nadie mas.
        assertEquals(HttpStatus.FORBIDDEN,
                escribir("PAGOS", false, tokenLaura).getStatusCode());
    }

    @Test
    void conLosPagosApagadosNoCOBRANIUNADMIN() {
        Integer cita = citaPasada();
        apagar("PAGOS");

        ResponseEntity<Map> resp = cobrar(cita, tokenAdmin);

        // Esto es lo que un permiso no puede hacer: un ADMIN los tiene todos por rol y aun
        // asi aqui no cobra. Y es 409, no 403: no es "tu no puedes", es que aqui no se cobra.
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("PAGOS", resp.getBody().get("modulo"));
    }

    @Test
    void apagarSoloLaTarjetaDejaEnPieElCobroEnElLocal() {
        // El caso realista: la peluqueria que cobra en mano y no quiere pasarela.
        Integer cita = citaPasada();
        apagar("PAGO_TARJETA");

        ResponseEntity<Map> intent = rest.exchange(url("/api/pagos/crear-intent"), HttpMethod.POST,
                new HttpEntity<>(Map.of("citaId", cita), cabecera(tokenCliente)), Map.class);
        assertEquals(HttpStatus.CONFLICT, intent.getStatusCode());
        assertEquals("PAGO_TARJETA", intent.getBody().get("modulo"));

        assertEquals(HttpStatus.OK, cobrar(cita, tokenAdmin).getStatusCode());
    }

    @Test
    void volverAEncenderloDevuelveElCobro() {
        Integer cita = citaPasada();
        apagar("PAGOS");
        assertEquals(HttpStatus.CONFLICT, cobrar(cita, tokenAdmin).getStatusCode());

        encender("PAGOS");

        // Sin tirar la cache al escribir, apagar un modulo seria irreversible hasta reiniciar.
        assertEquals(HttpStatus.OK, cobrar(cita, tokenAdmin).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void conLosPagosApagadosLaProduccionCuentaLoREALIZADO() {
        // La trampa que este diseno tenia que evitar: produccion es "realizada Y cobrada",
        // asi que sin pagos nada llegaria nunca a PAGADO y TODA la produccion seria cero.
        citaCerradaSinCobrar();

        ResponseEntity<Map> conPagos = produccionDeLaura();
        assertEquals(HttpStatus.OK, conPagos.getStatusCode());
        assertEquals(true, conPagos.getBody().get("exigeCobro"));
        assertEquals(0, ((Number) conPagos.getBody().get("serviciosRealizados")).intValue());
        assertEquals(1, ((Number) conPagos.getBody().get("serviciosSinCobrar")).intValue());

        apagar("PAGOS");

        Map<String, Object> sinPagos = produccionDeLaura().getBody();
        assertEquals(false, sinPagos.get("exigeCobro"));
        assertEquals(1, ((Number) sinPagos.get("serviciosRealizados")).intValue());
        assertEquals(30.0, ((Number) sinPagos.get("importeVendido")).doubleValue());
        // Y ya no hay "sin cobrar": esa cita esta contada arriba, no se cuenta dos veces.
        assertEquals(0, ((Number) sinPagos.get("serviciosSinCobrar")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void apagarLasComisionesQuitaElDineroDeLaFichaYCierraSuEndpoint() {
        apagar("COMISIONES");

        ResponseEntity<Map> comisiones = rest.exchange(url("/api/peluqueros/" + fichaLaura + "/comisiones"),
                HttpMethod.GET, new HttpEntity<>(cabecera(tokenAdmin)), Map.class);
        assertEquals(HttpStatus.CONFLICT, comisiones.getStatusCode());

        ResponseEntity<List> gestion = rest.exchange(url("/api/peluqueros/gestion"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenAdmin)), List.class);
        Map<String, Object> ficha = (Map<String, Object>) gestion.getBody().get(0);
        assertNull(ficha.get("comisionPorcentaje"), "La ficha deja de hablar de dinero.");
        assertTrue(((List<?>) ficha.get("comisionesPorServicio")).isEmpty());

        // Pero el dato no se ha borrado: apagar un modulo no borra nada.
        assertEquals(0, new java.math.BigDecimal("10.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT comision_porcentaje FROM peluqueros WHERE id_peluquero = ?",
                java.math.BigDecimal.class, fichaLaura)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void elHistoricoCongeladoNoSeToca() {
        // Una cita cerrada guarda su porcentaje; apagar el modulo no lo pone a cero ni lo
        // recalcula. Lo que se apaga es lo que se calcula de aqui en adelante.
        Integer cita = citaCerradaSinCobrar();
        apagar("COMISIONES");

        assertEquals(0, new java.math.BigDecimal("10.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT comision_porcentaje_aplicado FROM citas WHERE id_cita = ?",
                java.math.BigDecimal.class, cita)));

        // Y en la pantalla la columna desaparece, que es otra cosa.
        assertNull(produccionDeLaura().getBody().get("comision"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void apagarTodosLosMediosDePagoEsApagarElCobro() {
        apagar("PAGO_TARJETA");
        apagar("PAGO_EFECTIVO");
        apagar("PAGO_TRANSFERENCIA");

        ResponseEntity<Map> resp = rest.getForEntity(url("/api/modulos/activos"), Map.class);
        List<String> claves = (List<String>) resp.getBody().get("modulos");

        // Si no fuera asi quedaria un modulo "encendido" sin ninguna forma de cobrar.
        assertFalse(claves.contains("PAGOS"));
    }

    @Test
    void apagarLaGaleriaSeLLEVAELESCAPARATE() {
        // El listado es publico, asi que se comprueba sin token: un 409 dice «aqui no hay
        // galeria», que no es lo mismo que una lista vacia («no hay fotos todavia»).
        assertEquals(HttpStatus.OK, sinToken("/api/galeria"));

        apagar("GALERIA");

        assertEquals(HttpStatus.CONFLICT, sinToken("/api/galeria"));
        // Y tampoco la ve el ADMIN, que es lo que separa un modulo de un permiso.
        assertEquals(HttpStatus.CONFLICT, get("/api/galeria", tokenAdmin));
    }

    @Test
    void apagarElEquipoSeLLEVAELCVPUBLICO() {
        assertEquals(HttpStatus.OK, sinToken("/api/peluqueros/publicos"));

        apagar("EQUIPO_CV");

        assertEquals(HttpStatus.CONFLICT, sinToken("/api/peluqueros/publicos"));
        // Elegir con quien agendar sigue funcionando: eso sale de /api/peluqueros, que no es
        // el CV. Lo que se apaga es presentar a nadie, no la agenda.
        assertEquals(HttpStatus.OK, get("/api/peluqueros", tokenCliente));
    }

    @Test
    void apagarProduccionCortaTAMBIENALADMIN() {
        assertEquals(HttpStatus.OK, get("/api/produccion/peluquero/" + fichaLaura, tokenAdmin));

        apagar("PRODUCCION");

        assertEquals(HttpStatus.CONFLICT, get("/api/produccion/peluquero/" + fichaLaura, tokenAdmin));
        assertEquals(HttpStatus.CONFLICT, get("/api/produccion/mia", tokenLaura));
    }

    @Test
    void unaClaveQueNoExisteDevuelve400() {
        assertEquals(HttpStatus.BAD_REQUEST, escribir("MODULO_INVENTADO", false, tokenAdmin).getStatusCode());
    }

    // ---- Helpers ----

    private void encender(String clave) {
        assertEquals(HttpStatus.OK, escribir(clave, true, tokenAdmin).getStatusCode());
    }

    private void apagar(String clave) {
        assertEquals(HttpStatus.OK, escribir(clave, false, tokenAdmin).getStatusCode());
    }

    private void encenderTodo() {
        for (String clave : List.of("COMISIONES", "PAGOS", "PAGO_TARJETA", "PAGO_EFECTIVO",
                "PAGO_TRANSFERENCIA", "GALERIA", "EQUIPO_CV", "PRODUCCION", "RECORDATORIOS_EMAIL")) {
            encender(clave);
        }
    }

    /**
     * La respuesta se lee como texto crudo a proposito: en el camino feliz es la lista de
     * modulos y en el de error un objeto con el mensaje, y aqui solo se mira el estado.
     */
    private ResponseEntity<String> escribir(String clave, boolean activo, String token) {
        return rest.exchange(url("/api/modulos"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("cambios", List.of(Map.of("clave", clave, "activo", activo))),
                        cabecera(token)), String.class);
    }

    private ResponseEntity<Map> cobrar(Integer citaId, String token) {
        return rest.exchange(url("/api/pagos/manual"), HttpMethod.POST,
                new HttpEntity<>(Map.of("citaId", citaId, "metodoPago", "EFECTIVO"), cabecera(token)), Map.class);
    }

    private ResponseEntity<Map> produccionDeLaura() {
        return rest.exchange(url("/api/produccion/peluquero/" + fichaLaura + "?desde=2026-05-01&hasta=2026-05-31"),
                HttpMethod.GET, new HttpEntity<>(cabecera(tokenAdmin)), Map.class);
    }

    private Integer citaPasada() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, peluquero_id, fecha_hora, estado) "
                        + "VALUES (?, ?, ?, CAST('2026-05-04T10:00:00' AS TIMESTAMP), 'CONFIRMADA') RETURNING id_cita",
                Integer.class, clienteId, servCorte, fichaLaura);
    }

    /** Cita realizada y cerrada, con sus importes congelados, y sin cobrar. */
    private Integer citaCerradaSinCobrar() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO citas (usuario_id, servicio_id, peluquero_id, fecha_hora, estado, "
                        + "precio_aplicado, comision_porcentaje_aplicado, fecha_cierre) "
                        + "VALUES (?, ?, ?, CAST('2026-05-04T10:00:00' AS TIMESTAMP), 'COMPLETADA', "
                        + "30.00, 10.00, CAST('2026-05-04T11:00:00' AS TIMESTAMP)) RETURNING id_cita",
                Integer.class, clienteId, servCorte, fichaLaura);
    }

    private Integer crearFicha(String nombre, Integer usuarioId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO peluqueros (nombre, activo, usuario_id, comision_porcentaje) "
                        + "VALUES (?, true, ?, 10.00) RETURNING id_peluquero",
                Integer.class, nombre, usuarioId);
    }

    private String registrarConRol(String email, String password, String rol) {
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "MOD " + rol, "email", email, "password", password, "telefono", "600000008"),
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

    /** Una ruta publica, tal y como la ve alguien sin cuenta. */
    private org.springframework.http.HttpStatusCode sinToken(String path) {
        return rest.getForEntity(url(path), String.class).getStatusCode();
    }

    private org.springframework.http.HttpStatusCode get(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(cabecera(token)), String.class)
                .getStatusCode();
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
