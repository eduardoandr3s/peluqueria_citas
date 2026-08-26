package com.segovia.peluqueria.almacen;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Configuracion del almacen de ficheros ({@code peluqueria.almacen.*}).
 *
 * <p>Si no hay {@code supabase-url} y {@code service-key} se cae al almacen
 * local: el proyecto arranca igual, solo que los ficheros viven en disco y se
 * pierden al redesplegar (aceptable en desarrollo, nunca en produccion).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "peluqueria.almacen")
public class AlmacenProperties {

    /** URL del proyecto de Supabase, p.ej. {@code https://xxxx.supabase.co}. */
    private String supabaseUrl = "";

    /**
     * Service role key de Supabase. Salta las politicas de seguridad de la fila,
     * asi que vive solo en el servidor: NUNCA se envia a los frontends.
     */
    private String serviceKey = "";

    /** Bucket de las fotos del catalogo de servicios. Lectura publica. */
    private String bucketServicios = "servicios";

    /**
     * Bucket de los avatares. A diferencia del de servicios NO es publico: el
     * avatar es un dato personal, asi que se lee con URL firmada.
     */
    private String bucketAvatares = "avatares";

    /**
     * Bucket de la galeria de trabajos. Publico como el de servicios: es material
     * promocional, se ve sin cuenta.
     */
    private String bucketGaleria = "galeria";

    /**
     * Validez de las URLs firmadas de contenido privado. Cuanto mas corta, menos
     * dura una URL filtrada; cuanto mas larga, mejor la cachea el navegador.
     */
    private Duration validezUrlFirmada = Duration.ofHours(1);

    /** Tope de tamano por fichero. Se valida antes de leer el contenido entero. */
    private DataSize tamanoMaximo = DataSize.ofMegabytes(2);

    /** Solo para el almacen local: donde se escriben los ficheros. */
    private String directorioLocal = "target/almacen-local";

    /** Solo para el almacen local: prefijo con el que se sirven esos ficheros. */
    private String baseUrlLocal = "http://localhost:8080";

    /** True si hay credenciales para hablar con Supabase Storage. */
    public boolean usaSupabase() {
        return !supabaseUrl.isBlank() && !serviceKey.isBlank();
    }
}
