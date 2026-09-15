package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La ficha del negocio, de punta a punta.
 *
 * <p>Lo que hay que demostrar aqui, y no se puede demostrar con un test unitario, son las
 * dos cosas que justifican esta tabla: que la fila la <b>siembra la migracion</b> (o sea que
 * una instalacion recien levantada ya tiene horario y no hay que acordarse de nada), y que
 * <b>cambiar el horario desde el panel mueve los huecos que se ofrecen al agendar</b>, que
 * es lo que antes obligaba a editar el despliegue y reiniciar.
 *
 * <p>Los cambios se hacen SIEMPRE por el endpoint y nunca con un UPDATE directo: el servicio
 * cachea la ficha en memoria y una fila escrita por detras no lo invalidaria.
 */
class NegocioIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenCliente;
    private Map<String, Object> sembrada;
    private Integer servCorte;

    @BeforeEach
    void setUp() {
        tokenAdmin = registrarConRol("neg_admin@test.com", "Admin123!", "ADMIN");
        tokenCliente = registrarConRol("neg_cliente@test.com", "Cliente123!", "USER");
        sembrada = ficha();
        servCorte = jdbcTemplate.queryForObject(
                "INSERT INTO servicios (nombre, precio, duracion, activo) "
                        + "VALUES ('Corte NEG', 30, 30, true) RETURNING id_servicio", Integer.class);
    }

    /**
     * Se deja la ficha como estaba. El contenedor de Postgres es el MISMO para toda la suite
     * y un horario cambiado por este test sobreviviria a la clase: otro IT se encontraria una
     * peluqueria que abre a las once y fallaria por algo que no esta probando.
     */
    @AfterEach
    void restaurar() {
        guardar(sembrada, tokenAdmin);
    }

    @Test
    void laMigracionDejaLaInstalacionConHorario() {
        // Una instalacion recien levantada tiene que poder agendar sin que nadie rellene
        // nada: si esta fila no estuviera sembrada, el primer /api/citas seria un 500.
        Map<String, Object> ficha = ficha();

        assertNotNull(ficha.get("nombre"));
        assertEquals("09:00:00", ficha.get("horaApertura"));
        assertEquals("20:00:00", ficha.get("horaCierre"));
        assertEquals(List.of("SUNDAY"), ficha.get("diasCerrados"));
    }

    @Test
    void laFichaSeLeeSinToken() {
        // Las dos apps pintan el nombre y el contacto antes del login, igual que los
        // modulos activos. No hay nada privado aqui dentro.
        ResponseEntity<Map> resp = rest.getForEntity(url("/api/negocio"), Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody().get("nombre"));
    }

    @Test
    void editarlaEsSoloDelAdmin() {
        // El horario decide los huecos de todo el mundo: no lo toca un cliente.
        assertEquals(HttpStatus.FORBIDDEN, guardar(sembrada, tokenCliente).getStatusCode());
        assertEquals(HttpStatus.OK, guardar(sembrada, tokenAdmin).getStatusCode());
    }

    @Test
    void cambiarElHorarioMueveLosHuecosQueSeOfrecen() {
        // Es el motivo entero de sacar el horario de application.properties: antes esto
        // era editar el despliegue y reiniciar.
        assertTrue(huecos().contains("09:00"));

        Map<String, Object> nueva = new HashMap<>(sembrada);
        nueva.put("horaApertura", "11:00");
        assertEquals(HttpStatus.OK, guardar(nueva, tokenAdmin).getStatusCode());

        List<String> despues = huecos();
        assertTrue(despues.stream().noneMatch(hora -> hora.compareTo("11:00") < 0),
                "Con apertura a las 11:00 no deberia ofrecerse ningun hueco antes: " + despues);
    }

    @Test
    void unHorarioAlRevesSeRechazaConUn400YNoConUn500() {
        // La tabla tiene su CHECK, pero dejar que salte convertiria un error del formulario
        // en un 500 sin nada que leer.
        Map<String, Object> nueva = new HashMap<>(sembrada);
        nueva.put("horaApertura", "20:00");
        nueva.put("horaCierre", "09:00");

        assertEquals(HttpStatus.BAD_REQUEST, guardar(nueva, tokenAdmin).getStatusCode());
    }

    @Test
    void elNombreEsObligatorioYElRestoNo() {
        Map<String, Object> sinNombre = new HashMap<>(sembrada);
        sinNombre.put("nombre", "  ");
        assertEquals(HttpStatus.BAD_REQUEST, guardar(sinNombre, tokenAdmin).getStatusCode());

        // Una peluqueria recien dada de alta puede no tener todavia ni telefono ni web.
        Map<String, Object> soloNombre = new HashMap<>(sembrada);
        soloNombre.put("telefono", "");
        soloNombre.put("email", "");
        assertEquals(HttpStatus.OK, guardar(soloNombre, tokenAdmin).getStatusCode());
        assertNull(ficha().get("telefono"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarSeVeEnLaSiguienteLecturaSinReiniciar() {
        // La ficha se cachea en memoria: sin tirar la cache al escribir, el panel cambiaria
        // el nombre y la app seguiria con el viejo hasta el siguiente arranque.
        Map<String, Object> nueva = new HashMap<>(sembrada);
        nueva.put("nombre", "Peluqueria Renombrada");
        nueva.put("diasCerrados", List.of("SUNDAY", "MONDAY"));
        guardar(nueva, tokenAdmin);

        Map<String, Object> ficha = ficha();
        assertEquals("Peluqueria Renombrada", ficha.get("nombre"));
        assertEquals(List.of("MONDAY", "SUNDAY"), ficha.get("diasCerrados"));
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> ficha() {
        return rest.getForEntity(url("/api/negocio"), Map.class).getBody();
    }

    private ResponseEntity<Map> guardar(Map<String, Object> ficha, String token) {
        return rest.exchange(url("/api/negocio"), HttpMethod.PUT,
                new HttpEntity<>(ficha, cabecera(token)), Map.class);
    }

    /** Los huecos de un lunes cualquiera, que es lo que mueve el horario. */
    private List<String> huecos() {
        String fecha = proximoLunesALas(10).toLocalDate().toString();
        return rest.exchange(
                url("/api/citas/disponibilidad?fecha=" + fecha + "&idServicio=" + servCorte),
                HttpMethod.GET, new HttpEntity<>(cabecera(tokenCliente)),
                new ParameterizedTypeReference<List<String>>() {}).getBody();
    }

    private String registrarConRol(String email, String password, String rol) {
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "NEG " + rol, "email", email, "password", password, "telefono", "600000009"),
                Map.class);
        if (!"USER".equals(rol)) {
            jdbcTemplate.update("UPDATE usuarios SET rol = ? WHERE email = ?", rol, email);
        }
        return (String) rest.postForEntity(url("/api/auth/login"),
                Map.of("email", email, "password", password), Map.class).getBody().get("token");
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
