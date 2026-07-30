package com.segovia.peluqueria.almacen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Elige el almacen segun haya credenciales de Supabase o no, y cuando no las hay
 * publica el directorio local en {@code /media/**} para que las URLs que devuelve
 * {@link AlmacenLocal} se puedan abrir de verdad.
 *
 * <p>Caer al almacen local es un apano de desarrollo, asi que con el perfil de
 * produccion activo no se permite: alli el disco es efimero y el fallo seria
 * silencioso (las fotos se subirian bien y desaparecerian en el siguiente
 * despliegue). Mejor no arrancar que servir un almacen que pierde datos.
 */
@Configuration
public class AlmacenConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AlmacenConfig.class);

    private final AlmacenProperties propiedades;
    private final Environment entorno;

    public AlmacenConfig(AlmacenProperties propiedades, Environment entorno) {
        this.propiedades = propiedades;
        this.entorno = entorno;
    }

    @Bean
    public AlmacenFicheros almacenFicheros() {
        if (propiedades.usaSupabase()) {
            return new SupabaseStorageAlmacen(propiedades);
        }
        if (entorno.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "Faltan SUPABASE_URL y/o SUPABASE_SERVICE_KEY con el perfil prod activo. "
                            + "El almacen local no vale en produccion: el disco es efimero y las "
                            + "imagenes se perderian en el siguiente despliegue.");
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
