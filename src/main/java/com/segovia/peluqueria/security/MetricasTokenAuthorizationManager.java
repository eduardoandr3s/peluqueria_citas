package com.segovia.peluqueria.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Autoriza el acceso a {@code /actuator/prometheus} contra un token en cabecera.
 *
 * <p>Hace falta porque quien lee ese endpoint es Prometheus, no una persona: el resto del
 * API se protege con un JWT, y un JWT caduca, así que un scraper que corre cada quince
 * segundos no puede usarlo. El token es fijo y vive en una variable de entorno.
 *
 * <p>Las métricas no son datos personales, pero sí son información interna que no tiene por
 * qué ser pública: revelan versiones, rutas, volumen de negocio y el consumo del asistente.
 *
 * <p><strong>Sin token configurado no autoriza a nadie.</strong> Es deliberado que el fallo
 * sea cerrar y no abrir: un despliegue en el que se olvide la variable deja el endpoint
 * inaccesible, que es un problema que se ve enseguida, en vez de dejarlo abierto a internet,
 * que no se ve nunca.
 */
@Component
public class MetricasTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(MetricasTokenAuthorizationManager.class);

    /** Cabecera que trae el token. Se configura en el {@code scrape_config} de Prometheus. */
    static final String CABECERA = "X-Metrics-Token";

    private final byte[] tokenEsperado;

    public MetricasTokenAuthorizationManager(@Value("${peluqueria.metricas.token:}") String token) {
        this.tokenEsperado = token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        if (this.tokenEsperado == null) {
            log.info("Sin METRICAS_TOKEN: /actuator/prometheus queda cerrado. Es lo normal en desarrollo.");
        }
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        if (tokenEsperado == null) {
            return new AuthorizationDecision(false);
        }
        String recibido = context.getRequest().getHeader(CABECERA);
        if (recibido == null) {
            return new AuthorizationDecision(false);
        }
        // MessageDigest.isEqual compara en tiempo constante: un equals() normal sale antes
        // en el primer byte distinto y filtra el token a quien mida los tiempos.
        return new AuthorizationDecision(
                MessageDigest.isEqual(tokenEsperado, recibido.getBytes(StandardCharsets.UTF_8)));
    }
}
