package com.segovia.peluqueria.almacen;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Valida que lo que sube el cliente es de verdad una imagen.
 *
 * <p>No se confia en el {@code Content-Type} ni en el nombre del fichero: los dos
 * los pone quien sube, asi que un ejecutable renombrado a {@code .jpg} pasaria
 * cualquier comprobacion basada en ellos. Lo que se mira son los primeros bytes
 * del contenido (la firma del formato), y la extension y el tipo devueltos salen
 * de ahi, no de la peticion.
 */
@Component
public class ValidadorImagen {

    /** Formatos aceptados, con su firma en los primeros bytes del fichero. */
    private enum Formato {
        JPEG("image/jpeg", "jpg", new int[] { 0xFF, 0xD8, 0xFF }),
        PNG("image/png", "png", new int[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }),
        // WebP es un contenedor RIFF: "RIFF" + 4 bytes de tamano + "WEBP".
        WEBP("image/webp", "webp", new int[] { 0x52, 0x49, 0x46, 0x46 }, new int[] { 0x57, 0x45, 0x42, 0x50 });

        private final String contentType;
        private final String extension;
        private final int[] firma;
        private final int[] firmaEnByte8;

        Formato(String contentType, String extension, int[] firma) {
            this(contentType, extension, firma, null);
        }

        Formato(String contentType, String extension, int[] firma, int[] firmaEnByte8) {
            this.contentType = contentType;
            this.extension = extension;
            this.firma = firma;
            this.firmaEnByte8 = firmaEnByte8;
        }

        boolean coincide(byte[] datos) {
            if (!empiezaPor(datos, firma, 0)) {
                return false;
            }
            return firmaEnByte8 == null || empiezaPor(datos, firmaEnByte8, 8);
        }

        private static boolean empiezaPor(byte[] datos, int[] esperado, int desplazamiento) {
            if (datos.length < desplazamiento + esperado.length) {
                return false;
            }
            for (int i = 0; i < esperado.length; i++) {
                if ((datos[desplazamiento + i] & 0xFF) != esperado[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Imagen ya comprobada: contenido, tipo real y la clave con la que se va a
     * guardar. La clave la genera el servidor, nunca viene del cliente.
     */
    public record ImagenValidada(byte[] contenido, String contentType, String clave) {}

    private final AlmacenProperties propiedades;

    public ValidadorImagen(AlmacenProperties propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Comprueba el fichero y devuelve su contenido con la clave que le toca.
     *
     * @param prefijoClave carpeta logica dentro del bucket, p.ej. el id del servicio
     * @throws IllegalArgumentException si viene vacio, se pasa de tamano o no es
     *                                  una imagen de un formato aceptado (se
     *                                  traduce a 400)
     */
    public ImagenValidada validar(MultipartFile fichero, String prefijoClave) {
        if (fichero == null || fichero.isEmpty()) {
            throw new IllegalArgumentException("No se ha recibido ninguna imagen.");
        }
        long maximo = propiedades.getTamanoMaximo().toBytes();
        if (fichero.getSize() > maximo) {
            throw new IllegalArgumentException(
                    "La imagen supera el tamano maximo de " + propiedades.getTamanoMaximo().toMegabytes() + " MB.");
        }

        byte[] contenido;
        try {
            contenido = fichero.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se ha podido leer la imagen.", e);
        }

        // getSize() lo declara el cliente en la cabecera; el contenido real es lo que manda.
        if (contenido.length > maximo) {
            throw new IllegalArgumentException(
                    "La imagen supera el tamano maximo de " + propiedades.getTamanoMaximo().toMegabytes() + " MB.");
        }

        Formato formato = detectar(contenido);
        String clave = prefijoClave + "/" + UUID.randomUUID() + "." + formato.extension;
        return new ImagenValidada(contenido, formato.contentType, clave);
    }

    private Formato detectar(byte[] contenido) {
        for (Formato formato : Formato.values()) {
            if (formato.coincide(contenido)) {
                return formato;
            }
        }
        throw new IllegalArgumentException("El fichero no es una imagen JPEG, PNG o WebP valida.");
    }
}
