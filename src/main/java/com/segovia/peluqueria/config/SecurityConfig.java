package com.segovia.peluqueria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Bean para codificar las contraseñas utilizando BCryptPasswordEncoder.
    @Bean //Indica que este método devuelve un bean que se gestionará por el contenedor de Spring.
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    // Bean para configurar la cadena de filtros de seguridad. En este caso, se desactiva CSRF y se permite el acceso a todas las solicitudes sin autenticación.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }




}
