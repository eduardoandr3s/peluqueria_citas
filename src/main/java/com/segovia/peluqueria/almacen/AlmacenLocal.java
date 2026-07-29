package com.segovia.peluqueria.almacen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Almacen en el disco local, para desarrollo y para que el repositorio se pueda
 * clonar y arrancar sin cuenta en Supabase.
 *
 * <p>NO vale para produccion: el disco del contenedor es efimero y los ficheros
 * desaparecen en el siguiente despliegue. {@link AlmacenConfig} avisa por log
 * cuando se activa.
 */
public class AlmacenLocal implements AlmacenFicheros {

    private static final Logger log = LoggerFactory.getLogger(AlmacenLocal.class);

    private final Path raiz;
    private final String baseUrl;

    public AlmacenLocal(AlmacenProperties propiedades) {
        this.raiz = Path.of(propiedades.getDirectorioLocal()).toAbsolutePath().normalize();
        this.baseUrl = propiedades.getBaseUrlLocal().replaceAll("/+$", "");
    }

    /** Raiz del almacen, que {@link AlmacenConfig} publica como recurso estatico. */
    public Path getRaiz() {
        return raiz;
    }

    @Override
    public String guardar(String bucket, String clave, byte[] contenido, String contentType) {
        Path destino = resolver(bucket, clave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
            return clave;
        } catch (IOException e) {
            throw new AlmacenException("No se ha podido escribir el fichero en " + destino, e);
        }
    }

    @Override
    public void borrar(String bucket, String clave) {
        try {
            Files.deleteIfExists(resolver(bucket, clave));
        } catch (IOException e) {
            log.warn("No se ha podido borrar {}/{} del disco: {}", bucket, clave, e.getMessage());
        }
    }

    @Override
    public String urlDeLectura(String bucket, String clave) {
        return baseUrl + "/media/" + bucket + "/" + clave;
    }

    /**
     * Misma URL que {@link #urlDeLectura}: en disco no hay nada que firmar, porque
     * no hay bucket privado del que restringir la lectura. Es deliberado y no una
     * implementacion a medias; en produccion el almacen es Supabase.
     */
    @Override
    public String urlFirmada(String bucket, String clave, Duration validez) {
        return urlDeLectura(bucket, clave);
    }

    /**
     * Resuelve la ruta comprobando que no se sale de la raiz. Las claves las genera
     * el servidor, pero la comprobacion evita que un cambio futuro convierta esto
     * en un salto de directorio.
     */
    private Path resolver(String bucket, String clave) {
        Path destino = raiz.resolve(bucket).resolve(clave).normalize();
        if (!destino.startsWith(raiz)) {
            throw new AlmacenException("Clave de fichero no permitida: " + clave);
        }
        return destino;
    }
}
