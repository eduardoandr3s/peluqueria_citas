package com.segovia.peluqueria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    // Zona horaria del negocio. Las fechas de cita se guardan como hora local del negocio,
    // asi que "ahora" debe calcularse en esta zona y no en la del host (Render corre en UTC).
    @Bean
    public Clock clock(@Value("${peluqueria.zona-horaria:Europe/Madrid}") String zonaHoraria) {
        return Clock.system(ZoneId.of(zonaHoraria));
    }
}
