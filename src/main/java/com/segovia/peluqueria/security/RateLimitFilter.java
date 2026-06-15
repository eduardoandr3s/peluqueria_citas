package com.segovia.peluqueria.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita por IP las peticiones a los endpoints publicos de recuperacion de contrasena
 * ({@code POST /api/auth/recuperar} y {@code /api/auth/reset}) con un token bucket en memoria
 * (Bucket4j). Evita abuso/fuerza bruta sin afectar al resto del API. Al superar el limite
 * responde 429 con el mismo formato JSON de error del resto de la aplicacion.
 *
 * <p>El estado vive en memoria: valido para una sola instancia. En un despliegue multi-instancia
 * habria que respaldarlo en un store compartido (ej. Redis).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final Set<String> RUTAS_LIMITADAS = Set.of(
            "/api/auth/recuperar",
            "/api/auth/reset");

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacidad;
    private final long ventanaMinutos;

    public RateLimitFilter(@Value("${peluqueria.ratelimit.reset.capacidad:5}") int capacidad,
                           @Value("${peluqueria.ratelimit.reset.ventana-minutos:15}") long ventanaMinutos) {
        this.capacidad = capacidad;
        this.ventanaMinutos = ventanaMinutos;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && RUTAS_LIMITADAS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(claveCliente(request), k -> nuevoBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit superado en {} desde {}", request.getRequestURI(), claveCliente(request));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Demasiadas solicitudes. Intentalo de nuevo mas tarde.\"}");
        }
    }

    private Bucket nuevoBucket() {
        Bandwidth limite = Bandwidth.classic(capacidad,
                Refill.intervally(capacidad, Duration.ofMinutes(ventanaMinutos)));
        return Bucket.builder().addLimit(limite).build();
    }

    private String claveCliente(HttpServletRequest request) {
        // Tras un proxy (ej. Cloud Run) la IP real viene en X-Forwarded-For.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
