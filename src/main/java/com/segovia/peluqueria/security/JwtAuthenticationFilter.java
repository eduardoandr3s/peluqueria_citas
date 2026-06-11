package com.segovia.peluqueria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String email = jwtService.extraerEmail(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Ademas de la firma/expiracion: la cuenta debe seguir activa y el tokenVersion
                // del JWT debe coincidir con el de la BD (revocacion de tokens emitidos antes).
                if (jwtService.esTokenValido(token, userDetails.getUsername())
                        && userDetails.isEnabled()
                        && tokenVersionVigente(token, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token invalido o expirado: continuar sin autenticar (sin imprimir el token).
            log.debug("Token JWT rechazado: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // El token solo es vigente si su claim tokenVersion coincide con el actual del usuario en BD.
    // Un token sin el claim (emitido antes de esta funcionalidad) se considera no vigente.
    private boolean tokenVersionVigente(String token, UserDetails userDetails) {
        if (userDetails instanceof UsuarioPrincipal principal) {
            Integer versionToken = jwtService.extraerTokenVersion(token);
            return versionToken != null && versionToken.equals(principal.getTokenVersion());
        }
        return true;
    }
}
