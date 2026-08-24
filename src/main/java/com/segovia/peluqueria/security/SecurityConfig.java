package com.segovia.peluqueria.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/servicios", "/api/servicios/**").permitAll()
                        // Almacen local de desarrollo: en produccion las fotos las sirve
                        // Supabase Storage y este handler no se registra (ver AlmacenConfig).
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        // El asistente es publico porque se pregunta por precios y horarios
                        // ANTES de registrarse. Sus herramientas son de solo lectura y ninguna
                        // devuelve datos de clientes, asi que no expone nada personal; lo que
                        // si expone a anonimos es la disponibilidad y la lista de peluqueros,
                        // que por el API REST piden login. Va limitado por IP en RateLimitFilter.
                        .requestMatchers(HttpMethod.POST, "/api/asistente").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/servicios").hasRole("ADMIN")
                        // La regla de POST de arriba es exacta y no cubre las subrutas.
                        .requestMatchers(HttpMethod.POST, "/api/servicios/*/imagen").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/servicios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/servicios/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/usuarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/usuarios/*/rol").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/usuarios/*/activar").hasRole("ADMIN")
                        // El avatar NO es cosa solo de ADMIN: cada usuario gestiona el suyo. La
                        // comprobacion de que el id es el propio la hace UsuarioService, que es
                        // quien puede resolverla. Van antes del DELETE /** de abajo, que si es
                        // de ADMIN y en otro orden se tragaria el borrado del avatar propio.
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/*/avatar").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/usuarios/*/avatar").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/usuarios/**").authenticated()

                        .requestMatchers("/api/citas/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/peluqueros", "/api/peluqueros/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/peluqueros").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/peluqueros/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/peluqueros/**").hasRole("ADMIN")

                        // Los dias cerrados los consulta cualquier usuario autenticado (para el
                        // calendario de agendar); solo un ADMIN puede bloquear o desbloquear.
                        .requestMatchers(HttpMethod.GET, "/api/dias-bloqueados", "/api/dias-bloqueados/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/dias-bloqueados").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/dias-bloqueados/**").hasRole("ADMIN")

                        .requestMatchers("/api/estadisticas/**").hasRole("ADMIN")

                        .requestMatchers("/api/pagos/webhook").permitAll()
                        .requestMatchers("/api/pagos/crear-intent").authenticated()
                        // El listado completo de pagos es del panel; el detalle por cita lo consulta
                        // tambien el cliente (el servicio ya comprueba que la cita sea suya).
                        .requestMatchers(HttpMethod.GET, "/api/pagos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/pagos/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/manual").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pagos/*/reembolsar").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
