package com.segovia.peluqueria.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita el procesamiento asincrono ({@code @Async}), usado por el envio de correos
 * para no bloquear las peticiones HTTP.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
