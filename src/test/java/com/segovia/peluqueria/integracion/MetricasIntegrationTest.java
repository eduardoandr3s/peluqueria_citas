package com.segovia.peluqueria.integracion;

import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

/**
 * Quién puede leer qué de Actuator. Es la mitad del trabajo de observabilidad que puede
 * salir mal en silencio: unas métricas mal protegidas no dan ningún error, simplemente
 * quedan publicadas en internet, y el endpoint que las sirve no lo escribí yo.
 *
 * <p>El token se fija aquí y no en {@code application-test.properties} a propósito: así el
 * resto de la suite corre <strong>sin</strong> token, que es el caso por defecto, y el último
 * test de esta clase puede comprobar que sin token no se autoriza a nadie.
 */
@TestPropertySource(properties = "peluqueria.metricas.token=token-de-prueba")
class MetricasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager txManager;

    /**
     * Este test vale por lo que evita, y lo cazo al escribirlo: Actuator activa un indicador
     * de correo solo por tener el starter de mail, y en cuanto Gmail no contesta pone el
     * health global en DOWN, que se sirve como <strong>503</strong>. El health check de Render
     * lee este endpoint, asi que un hipo del SMTP daria el backend por caido y lo reiniciaria
     * en bucle con las citas y los pagos funcionando. Aqui el correo esta roto de verdad (el
     * perfil de test no tiene credenciales de SMTP), asi que si alguien vuelve a activar ese
     * indicador, este test se pone rojo en lugar de la produccion.
     */
    @Test
    void healthEsPublicoNoCuentaNadaDeDentroYNoLoTumbaUnFalloDeCorreo() {
        ResponseEntity<String> respuesta = rest.getForEntity(url("/actuator/health"), String.class);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode(), "un SMTP caido no puede tumbar el health");
        assertTrue(respuesta.getBody().contains("UP"));
        // Lo que importa es lo que NO sale: el health check de Render solo necesita saber
        // que la aplicacion vive, no que hay Postgres detras ni cuanto disco queda.
        assertFalse(respuesta.getBody().contains("components"), "health no debe enumerar componentes");
        assertFalse(respuesta.getBody().contains("diskSpace"));
        assertFalse(respuesta.getBody().contains("db"));
    }

    @Test
    void lasMetricasNoSonPublicas() {
        ResponseEntity<String> respuesta = rest.getForEntity(url("/actuator/prometheus"), String.class);

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
    }

    @Test
    void unTokenEquivocadoTampocoValeAunqueEmpiecePorLoMismo() {
        ResponseEntity<String> respuesta = pedirMetricas("token-de-prueba-pero-no");

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
    }

    @Test
    void conElTokenCorrectoSalenLasMetricasEnFormatoPrometheus() {
        ResponseEntity<String> respuesta = pedirMetricas("token-de-prueba");

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        String cuerpo = respuesta.getBody();
        // Metricas tecnicas, que las publica Actuator sin que nadie las escriba.
        assertTrue(cuerpo.contains("jvm_memory_used_bytes"), "faltan las metricas de la JVM");
        assertTrue(cuerpo.contains("http_server_requests"), "faltan las de peticiones HTTP");
        // Y la etiqueta comun, que es la que permite filtrar por aplicacion en el dashboard.
        assertTrue(cuerpo.contains("application=\"peluqueria\""), "falta la etiqueta application");
    }

    /**
     * La regla que de verdad protege: {@code env}, {@code beans} y {@code configprops}
     * volcarian la configuracion entera, con la clave de Stripe y la de Gemini dentro. No
     * estan en la lista blanca de {@code exposure.include}, y ademas Security los tapa con un
     * {@code denyAll}. Este test comprueba la segunda barrera, que es la que sigue en pie si
     * alguien anade un endpoint a la lista sin pensarlo.
     */
    @Test
    void elRestoDeActuatorEstaCerradoInclusoConElTokenBueno() {
        for (String endpoint : new String[]{"/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/loggers", "/actuator"}) {
            ResponseEntity<String> respuesta = pedirMetricas("token-de-prueba", endpoint);

            assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode(), endpoint + " deberia estar cerrado");
        }
    }

    /**
     * El puente entre el contador y el dashboard, que es donde se rompe todo sin avisar: en
     * el código la métrica se llama {@code peluqueria.citas} y en Prometheus
     * {@code peluqueria_citas_total}, porque Micrometer traduce los puntos y añade el sufijo
     * de los contadores. Las consultas del dashboard usan el nombre traducido, así que este
     * test es el único sitio donde los dos nombres se comprueban juntos.
     *
     * <p>El evento se publica dentro de una transacción a propósito: el listener escucha en
     * {@code AFTER_COMMIT} y sin transacción no se ejecutaría nunca.
     */
    @Test
    void unaCitaAgendadaSaleEnElScrapeConElNombreQueConsultaElDashboard() {
        new TransactionTemplate(txManager).executeWithoutResult(estado ->
                publisher.publishEvent(new CitaAgendadaEvent(
                        "Ana", "ana@ejemplo.com", "Corte", LocalDateTime.now().plusDays(3))));

        String cuerpo = pedirMetricas("token-de-prueba").getBody();

        assertTrue(cuerpo.contains("peluqueria_citas_total"), "el dashboard consulta este nombre exacto");
        assertTrue(cuerpo.contains("estado=\"agendada\""));
        assertTrue(cuerpo.contains("servicio=\"Corte\""));
        // Y la regla que no se puede relajar, comprobada donde de verdad importa: en lo que
        // sale por el cable hacia Prometheus.
        assertFalse(cuerpo.contains("ana@ejemplo.com"), "un correo jamas puede salir en una metrica");
        assertFalse(cuerpo.contains("Ana"), "ni un nombre de cliente");
    }

    private ResponseEntity<String> pedirMetricas(String token) {
        return pedirMetricas(token, "/actuator/prometheus");
    }

    private ResponseEntity<String> pedirMetricas(String token, String endpoint) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("X-Metrics-Token", token);
        return rest.exchange(url(endpoint), HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
