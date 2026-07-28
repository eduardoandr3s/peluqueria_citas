package com.segovia.peluqueria.almacen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Almacen sobre Supabase Storage, por su API REST.
 *
 * <p>Se usa la API REST y no el SDK de S3 a proposito: son tres llamadas HTTP y
 * la alternativa serian varios megas de dependencia en una instancia con 512 MB
 * de memoria.
 */
public class SupabaseStorageAlmacen implements AlmacenFicheros {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageAlmacen.class);

    private final RestClient cliente;
    private final String baseUrl;

    public SupabaseStorageAlmacen(AlmacenProperties propiedades) {
        this.baseUrl = propiedades.getSupabaseUrl().replaceAll("/+$", "");
        this.cliente = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", "Bearer " + propiedades.getServiceKey())
                // La pasarela de Supabase enruta por la cabecera apikey; sin ella hay
                // montajes que responden 401 aunque el Bearer sea correcto. Mandar las
                // dos es redundante en el peor caso y evita depender de ese detalle.
                .defaultHeader("apikey", propiedades.getServiceKey())
                .build();
    }

    @Override
    public String guardar(String bucket, String clave, byte[] contenido, String contentType) {
        try {
            cliente.post()
                    .uri("/storage/v1/object/{bucket}/{clave}", bucket, clave)
                    .contentType(MediaType.parseMediaType(contentType))
                    // Sustituir en vez de fallar si la clave ya existe.
                    .header("x-upsert", "true")
                    .body(contenido)
                    .retrieve()
                    .toBodilessEntity();
            return clave;
        } catch (RestClientException e) {
            throw new AlmacenException("No se ha podido guardar el fichero en el almacen.", e);
        }
    }

    @Override
    public void borrar(String bucket, String clave) {
        try {
            cliente.delete()
                    .uri("/storage/v1/object/{bucket}/{clave}", bucket, clave)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Un borrado que falla deja un objeto huerfano ocupando cuota, pero no es
            // motivo para tumbar la operacion del usuario: se registra y se sigue.
            log.warn("No se ha podido borrar {}/{} del almacen: {}", bucket, clave, e.getMessage());
        }
    }

    @Override
    public String urlDeLectura(String bucket, String clave) {
        return baseUrl + "/storage/v1/object/public/" + bucket + "/" + clave;
    }
}
