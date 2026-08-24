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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita por IP las peticiones POST a los endpoints publicos que conviene proteger, con un
 * token bucket en memoria (Bucket4j). Al superar el limite responde 429 con el mismo formato
 * JSON de error del resto de la aplicacion.
 *
 * <p>Cada ruta tiene <strong>su propio cupo</strong>, porque protegen de cosas distintas:
 * <ul>
 *   <li>{@code /api/auth/recuperar} y {@code /api/auth/reset}: fuerza bruta. Pocos intentos
 *       en una ventana larga.</li>
 *   <li>{@code /api/asistente}: gasto. Cada peticion consume cuota del proveedor del modelo,
 *       asi que el limite es lo que evita que quien encuentre la URL agote la cuota del dia.</li>
 * </ul>
 * Los cupos son independientes: gastar el del asistente no bloquea recuperar la contrasena.
 *
 * <p>El estado vive en memoria: valido para una sola instancia. En un despliegue multi-instancia
 * habria que respaldarlo en un store compartido (ej. Redis).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String RUTA_RECUPERAR = "/api/auth/recuperar";
    private static final String RUTA_RESET = "/api/auth/reset";
    private static final String RUTA_ASISTENTE = "/api/asistente";

    /** Cupo de una ruta: cuantas peticiones caben y en cuanto tiempo se recarga entero. */
    private record Limite(int capacidad, long ventanaMinutos) {
    }

    private final Map<String, Limite> limitesPorRuta;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${peluqueria.ratelimit.reset.capacidad:5}") int capacidadReset,
            @Value("${peluqueria.ratelimit.reset.ventana-minutos:15}") long ventanaReset,
            @Value("${peluqueria.ratelimit.asistente.capacidad:10}") int capacidadAsistente,
            @Value("${peluqueria.ratelimit.asistente.ventana-minutos:60}") long ventanaAsistente) {
        Limite reset = new Limite(capacidadReset, ventanaReset);
        this.limitesPorRuta = Map.of(
                RUTA_RECUPERAR, reset,
                RUTA_RESET, reset,
                RUTA_ASISTENTE, new Limite(capacidadAsistente, ventanaAsistente));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && limitesPorRuta.containsKey(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ruta = request.getRequestURI();
        String cliente = claveCliente(request);
        Limite limite = limitesPorRuta.get(ruta);
        // La clave lleva la ruta, no solo la IP: si no, las rutas compartirian cupo y gastar
        // el del asistente dejaria sin intentos la recuperacion de contrasena.
        Bucket bucket = buckets.computeIfAbsent(ruta + "|" + cliente, k -> nuevoBucket(limite));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit superado en {} desde {}", ruta, cliente);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Demasiadas solicitudes. Intentalo de nuevo mas tarde.\"}");
        }
    }

    private Bucket nuevoBucket(Limite limite) {
        Bandwidth ancho = Bandwidth.classic(limite.capacidad(),
                Refill.intervally(limite.capacidad(), Duration.ofMinutes(limite.ventanaMinutos())));
        return Bucket.builder().addLimit(ancho).build();
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
