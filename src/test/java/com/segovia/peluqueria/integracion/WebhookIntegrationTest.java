package com.segovia.peluqueria.integracion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.segovia.peluqueria.pago.EstadoPago;
import com.segovia.peluqueria.pago.MetodoPago;
import com.segovia.peluqueria.pago.PaymentGateway;
import com.segovia.peluqueria.pago.dto.PagoResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebhookIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_key_para_firmar_localmente";
    private static final String FAKE_PI_ID = "pi_test_integration_123";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenCliente;
    private Integer idCita;
    private Integer idServicio;

    @BeforeEach
    void setUp() {
        // Limpiar datos de ejecuciones previas para hacer setUp idempotente
        jdbcTemplate.execute("DELETE FROM password_reset_token");
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("DELETE FROM stripe_evento");
        jdbcTemplate.execute("DELETE FROM pagos");
        jdbcTemplate.execute("DELETE FROM citas");
        jdbcTemplate.execute("DELETE FROM servicios");
        jdbcTemplate.execute("DELETE FROM usuarios");

        // Crear ADMIN directamente en BD
        String emailAdmin = "wh_admin@test.com";
        String passAdmin = "Admin123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Admin WH", "email", emailAdmin, "password", passAdmin, "telefono", "600000020"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", emailAdmin);
        var respAdmin = rest.postForEntity(url("/api/auth/login"),
                Map.of("email", emailAdmin, "password", passAdmin), Map.class);
        tokenAdmin = (String) respAdmin.getBody().get("token");

        // Crear servicio
        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        var servicioResp = rest.exchange(url("/api/servicios"), HttpMethod.POST,
                new HttpEntity<>(Map.of("nombre", "Corte test WH", "descripcion", "Test",
                        "precio", 30.0, "duracion", 45), headersAdmin), Map.class);
        idServicio = (Integer) servicioResp.getBody().get("idServicio");

        // Registrar cliente
        String emailCliente = "cliente_wh@test.com";
        String passCliente = "Pass1234!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Cliente WH", "email", emailCliente, "password", passCliente,
                        "telefono", "600000021"), Map.class);

        var loginCliente = rest.postForEntity(url("/api/auth/login"),
                Map.of("email", emailCliente, "password", passCliente), Map.class);
        tokenCliente = (String) loginCliente.getBody().get("token");

        // Crear cita como cliente
        LocalDateTime maniana = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0);
        var headersCliente = new HttpHeaders();
        headersCliente.setBearerAuth(tokenCliente);
        var citaResp = rest.exchange(url("/api/citas"), HttpMethod.POST,
                new HttpEntity<>(Map.of("servicioId", idServicio,
                        "fechaHora", maniana.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                        headersCliente), Map.class);
        idCita = (Integer) citaResp.getBody().get("idCita");
    }

    @Test
    void firmaInvalida_retorna400() {
        String payload = "{\"id\":\"evt_bad\",\"type\":\"payment_intent.succeeded\"}";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "t=1234567890,v1=signatura_invalida");

        ResponseEntity<String> resp = rest.exchange(url("/api/pagos/webhook"), HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void webhookCompleto_pagoSeConfirmaYCitaSeActualiza() throws Exception {
        // Crear payment intent (usa el stub)
        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        var intentResp = rest.exchange(url("/api/pagos/crear-intent"), HttpMethod.POST,
                new HttpEntity<>(Map.of("citaId", idCita), headersAdmin), Map.class);
        assertEquals(HttpStatus.OK, intentResp.getStatusCode());
        String piId = (String) intentResp.getBody().get("paymentIntentId");
        assertEquals(FAKE_PI_ID, piId);

        // Verificar que el pago está PENDIENTE antes del webhook
        ResponseEntity<PagoResponseDTO> pagoAntes = rest.exchange(
                url("/api/pagos/cita/" + idCita), HttpMethod.GET,
                new HttpEntity<>(headersAdmin), PagoResponseDTO.class);
        assertEquals(EstadoPago.PENDIENTE, pagoAntes.getBody().getEstadoPago());

        // Enviar webhook payment_intent.succeeded con firma real
        String payload = construirPayloadSucceeded(piId);
        String sigHeader = firmar(payload);

        var whHeaders = new HttpHeaders();
        whHeaders.setContentType(MediaType.APPLICATION_JSON);
        whHeaders.set("Stripe-Signature", sigHeader);

        ResponseEntity<String> whResp = rest.exchange(url("/api/pagos/webhook"), HttpMethod.POST,
                new HttpEntity<>(payload, whHeaders), String.class);
        assertEquals(HttpStatus.OK, whResp.getStatusCode());

        // Verificar pago PAGADO
        ResponseEntity<PagoResponseDTO> pagoDespues = rest.exchange(
                url("/api/pagos/cita/" + idCita), HttpMethod.GET,
                new HttpEntity<>(headersAdmin), PagoResponseDTO.class);
        assertEquals(EstadoPago.PAGADO, pagoDespues.getBody().getEstadoPago());

        // Verificar cita CONFIRMADA
        ResponseEntity<Map> citaResp = rest.exchange(url("/api/citas/" + idCita),
                HttpMethod.GET, new HttpEntity<>(headersAdmin), Map.class);
        assertEquals("CONFIRMADA", citaResp.getBody().get("estado"));
    }

    @Test
    void webhookDuplicado_seProcesaUnaSolaVez() throws Exception {
        // Crear payment intent
        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        rest.exchange(url("/api/pagos/crear-intent"), HttpMethod.POST,
                new HttpEntity<>(Map.of("citaId", idCita), headersAdmin), Map.class);

        String piId = FAKE_PI_ID;
        String payload = construirPayloadSucceeded(piId);
        String sigHeader = firmar(payload);

        var whHeaders = new HttpHeaders();
        whHeaders.setContentType(MediaType.APPLICATION_JSON);
        whHeaders.set("Stripe-Signature", sigHeader);

        // Primer envío
        ResponseEntity<String> primero = rest.exchange(url("/api/pagos/webhook"),
                HttpMethod.POST, new HttpEntity<>(payload, whHeaders), String.class);
        assertEquals(HttpStatus.OK, primero.getStatusCode());

        // Segundo envío (mismo event ID)
        ResponseEntity<String> segundo = rest.exchange(url("/api/pagos/webhook"),
                HttpMethod.POST, new HttpEntity<>(payload, whHeaders), String.class);
        assertEquals(HttpStatus.OK, segundo.getStatusCode());

        // La cita sigue CONFIRMADA (no se procesó dos veces)
        ResponseEntity<Map> citaResp = rest.exchange(url("/api/citas/" + idCita),
                HttpMethod.GET, new HttpEntity<>(headersAdmin), Map.class);
        assertEquals("CONFIRMADA", citaResp.getBody().get("estado"));
    }

    private String construirPayloadSucceeded(String paymentIntentId) throws JsonProcessingException {
        return MAPPER.writeValueAsString(Map.of(
                "id", "evt_test_duplicate_001",
                "type", "payment_intent.succeeded",
                "data", Map.of(
                        "object", Map.of(
                                "id", paymentIntentId,
                                "object", "payment_intent",
                                "status", "succeeded",
                                "amount", 3000,
                                "currency", "eur"
                        )
                )
        ));
    }

    private String firmar(String payload) {
        try {
            String secret = WEBHOOK_SECRET;
            long timestamp = Instant.now().getEpochSecond();
            String toSign = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(key);
            String signature = HexFormat.of().formatHex(mac.doFinal(toSign.getBytes()));
            return "t=" + timestamp + ",v1=" + signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @TestConfiguration
    static class TestStripeConfig {

        @Bean
        @Primary
        PaymentGateway testPaymentGateway() {
            return new PaymentGateway() {
                @Override
                public IntentPasarela crearIntent(BigDecimal monto, String descripcion, Integer citaId) {
                    return new IntentPasarela(FAKE_PI_ID, "cs_test_secret", "requires_payment_method");
                }

                @Override
                public IntentPasarela recuperarIntent(String id) {
                    return new IntentPasarela(id, "cs_test_secret", "requires_payment_method");
                }

                @Override
                public void cancelarIntent(String id) {
                }

                @Override
                public void reembolsar(String paymentIntentId) {
                }

                @Override
                public EventoPasarela validarWebhook(String payload, String firma) {
                    try {
                        String secret = WEBHOOK_SECRET;
                        // Extraer timestamp y firma del header
                        String t = null;
                        String v1 = null;
                        for (String parte : firma.split(",")) {
                            String[] kv = parte.split("=", 2);
                            if (kv.length == 2) {
                                if ("t".equals(kv[0])) t = kv[1];
                                if ("v1".equals(kv[0])) v1 = kv[1];
                            }
                        }
                        if (t == null || v1 == null) {
                            throw new IllegalArgumentException("Formato de firma invalido");
                        }
                        String toSign = t + "." + payload;
                        Mac mac = Mac.getInstance("HmacSHA256");
                        SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
                        mac.init(key);
                        String esperada = HexFormat.of().formatHex(mac.doFinal(toSign.getBytes()));
                        if (!esperada.equals(v1)) {
                            throw new IllegalArgumentException("Firma del webhook invalida.");
                        }
                        // Parsear payload
                        var json = MAPPER.readTree(payload);
                        String eventId = json.get("id").asText();
                        String tipo = json.get("type").asText();
                        String piId = json.path("data").path("object").path("id").asText(null);
                        return new EventoPasarela(eventId, tipo, piId);
                    } catch (Exception e) {
                        if (e instanceof IllegalArgumentException iae) {
                            throw iae;
                        }
                        throw new IllegalArgumentException("Error al validar webhook: " + e.getMessage());
                    }
                }
            };
        }
    }
}
