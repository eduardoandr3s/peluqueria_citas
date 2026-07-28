package com.segovia.peluqueria.almacen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Elige el almacen segun haya credenciales de Supabase o no, y cuando no las hay
 * publica el directorio local en {@code /media/**} para que las URLs que devuelve
 * {@link AlmacenLocal} se puedan abrir de verdad.
 */
@Configuration
public class AlmacenConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AlmacenConfig.class);

    private final AlmacenProperties propiedades;

    public AlmacenConfig(AlmacenProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Bean
    public AlmacenFicheros almacenFicheros() {
        if (propiedades.usaSupabase()) {
            return new SupabaseStorageAlmacen(propiedades);
        }
        log.warn("Sin SUPABASE_URL/SUPABASE_SERVICE_KEY: se usa el almacen local en {}. "
                + "No apto para produccion, el disco es efimero.", propiedades.getDirectorioLocal());
        return new AlmacenLocal(propiedades);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        if (propiedades.usaSupabase()) {
            return;
        }
        AlmacenLocal local = new AlmacenLocal(propiedades);
        registro.addResourceHandler("/media/**")
                .addResourceLocations(local.getRaiz().toUri().toString());
    }
}
