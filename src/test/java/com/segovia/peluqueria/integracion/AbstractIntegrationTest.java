package com.segovia.peluqueria.integracion;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    // @ServiceConnection cablea automáticamente la datasource al contenedor,
    // sin necesidad de @DynamicPropertySource. Contenedor static = compartido entre ITs.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @LocalServerPort
    protected int port;

    /**
     * Fecha para agendar citas en los tests: el próximo lunes a la hora indicada.
     * Siempre es futura y nunca cae en un día cerrado (por defecto, el domingo), así que
     * el resultado no depende del día de la semana en que se ejecute la suite.
     */
    protected static LocalDateTime proximoLunesALas(int hora) {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(hora, 0);
    }

    protected final RestTemplate rest;

    {
        rest = new RestTemplate();
        // Cliente basado en java.net.http (JDK) y no el de HttpURLConnection que trae
        // RestTemplate por defecto: ese no admite PATCH ("Invalid HTTP method: PATCH"), y la
        // API usa PATCH en el cambio de rol y en el cierre de cita.
        rest.setRequestFactory(new JdkClientHttpRequestFactory());
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
    }
}
