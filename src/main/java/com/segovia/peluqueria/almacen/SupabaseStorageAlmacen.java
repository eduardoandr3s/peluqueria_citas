package com.segovia.peluqueria.almacen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

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
        this(propiedades, RestClient.builder());
    }

    /**
     * Costura para los tests: recibir el constructor del cliente permite enchufarle
     * un servidor simulado y comprobar las peticiones que se mandan a Supabase, que
     * de otro modo solo se ejercitarian en produccion.
     */
    SupabaseStorageAlmacen(AlmacenProperties propiedades, RestClient.Builder constructor) {
        this.baseUrl = propiedades.getSupabaseUrl().replaceAll("/+$", "");
        this.cliente = constructor
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", "Bearer " + propiedades.getServiceKey())
                // La pasarela de Supabase enruta por la cabecera apikey; sin ella hay
                // montajes que responden 401 aunque el Bearer sea correcto. Mandar las
                // dos es redundante en el peor caso y evita depender de ese detalle.
                .defaultHeader("apikey", propiedades.getServiceKey())
                .build();
    }

    /**
     * Ruta del objeto dentro de la API.
     *
     * <p>Se concatena en vez de pasar la clave como variable de plantilla porque al
     * expandirla se escaparian sus barras a {@code %2F}, y la clave lleva la carpeta
     * dentro ({@code "7/uuid.jpg"}): aqui tiene que seguir siendo una ruta. Las claves
     * las genera {@link ValidadorImagen} (id + UUID + extension), asi que no traen
     * nada que hubiera que escapar.
     */
    private static String ruta(String prefijo, String bucket, String clave) {
        return prefijo + bucket + "/" + clave;
    }

    @Override
    public String guardar(String bucket, String clave, byte[] contenido, String contentType) {
        try {
            cliente.post()
                    .uri(ruta("/storage/v1/object/", bucket, clave))
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
                    .uri(ruta("/storage/v1/object/", bucket, clave))
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

    /** Lo que devuelve el endpoint de firma. El nombre del campo es el de la API. */
    private record RespuestaFirma(String signedURL) {}

    @Override
    public String urlFirmada(String bucket, String clave, Duration validez) {
        try {
            RespuestaFirma respuesta = cliente.post()
                    .uri(ruta("/storage/v1/object/sign/", bucket, clave))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", validez.toSeconds()))
                    .retrieve()
                    .body(RespuestaFirma.class);

            if (respuesta == null || respuesta.signedURL() == null || respuesta.signedURL().isBlank()) {
                throw new AlmacenException("El almacen no ha devuelto una URL firmada para " + bucket + "/" + clave);
            }
            // signedURL viene RELATIVA a /storage/v1 (p.ej. "/object/sign/avatares/7/x.jpg?token=..."),
            // asi que hay que prefijarla o el cliente recibe una URL que no resuelve.
            return baseUrl + "/storage/v1" + respuesta.signedURL();
        } catch (RestClientException e) {
            throw new AlmacenException("No se ha podido firmar la URL de " + bucket + "/" + clave, e);
        }
    }
}
