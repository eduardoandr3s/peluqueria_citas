package com.segovia.peluqueria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extraer el header Authorization
        String authHeader = request.getHeader("Authorization");

        // 2. Si no hay header o no empieza con "Bearer ", continuar sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraer el token (sin el prefijo "Bearer ")
        String token = authHeader.substring(7);

        try {
            // 4. Extraer el email del token
            String email = jwtService.extraerEmail(token);

            // 5. Si hay email y no hay autenticacion previa en el contexto
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 6. Cargar los datos del usuario desde la BD
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // 7. Validar el token
                if (jwtService.esTokenValido(token, userDetails.getUsername())) {

                    // 8. Crear la autenticacion y asignarla al contexto de seguridad
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token invalido o expirado: continuar sin autenticar
        }

        filterChain.doFilter(request, response);
    }
}
