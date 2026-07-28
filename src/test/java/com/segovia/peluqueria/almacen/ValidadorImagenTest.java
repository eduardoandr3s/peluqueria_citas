package com.segovia.peluqueria.almacen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorImagenTest {

    private ValidadorImagen validador;

    @BeforeEach
    void setUp() {
        AlmacenProperties propiedades = new AlmacenProperties();
        propiedades.setTamanoMaximo(DataSize.ofKilobytes(10));
        validador = new ValidadorImagen(propiedades);
    }

    /** Cabecera valida del formato + relleno hasta el tamano pedido. */
    private static byte[] conFirma(byte[] firma, int tamanoTotal) {
        byte[] datos = new byte[Math.max(firma.length, tamanoTotal)];
        System.arraycopy(firma, 0, datos, 0, firma.length);
        return datos;
    }

    private static final byte[] FIRMA_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] FIRMA_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static MockMultipartFile fichero(String nombre, byte[] contenido, String contentType) {
        return new MockMultipartFile("imagen", nombre, contentType, contenido);
    }

    @Test
    void aceptaJpegYDevuelveSuTipoReal() {
        var validada = validador.validar(fichero("foto.jpg", conFirma(FIRMA_JPEG, 100), "image/jpeg"), "7");

        assertEquals("image/jpeg", validada.contentType());
        assertTrue(validada.clave().endsWith(".jpg"));
    }

    @Test
    void aceptaPng() {
        var validada = validador.validar(fichero("foto.png", conFirma(FIRMA_PNG, 100), "image/png"), "7");

        assertEquals("image/png", validada.contentType());
        assertTrue(validada.clave().endsWith(".png"));
    }

    @Test
    void aceptaWebp() {
        // RIFF + 4 bytes de tamano + WEBP
        byte[] datos = new byte[100];
        System.arraycopy("RIFF".getBytes(), 0, datos, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, datos, 8, 4);

        var validada = validador.validar(fichero("foto.webp", datos, "image/webp"), "7");

        assertEquals("image/webp", validada.contentType());
        assertTrue(validada.clave().endsWith(".webp"));
    }

    @Test
    void rechazaUnEjecutableDisfrazadoDeJpeg() {
        // "MZ", cabecera de un ejecutable de Windows, con nombre y Content-Type de imagen:
        // los dos los pone el cliente, asi que solo el contenido puede desmentirlo.
        byte[] ejecutable = conFirma(new byte[] { 0x4D, 0x5A }, 100);

        var ex = assertThrows(IllegalArgumentException.class,
                () -> validador.validar(fichero("foto.jpg", ejecutable, "image/jpeg"), "7"));

        assertTrue(ex.getMessage().contains("no es una imagen"));
    }

    @Test
    void rechazaUnFicheroDemasiadoGrande() {
        byte[] grande = conFirma(FIRMA_JPEG, (int) DataSize.ofKilobytes(11).toBytes());

        var ex = assertThrows(IllegalArgumentException.class,
                () -> validador.validar(fichero("foto.jpg", grande, "image/jpeg"), "7"));

        assertTrue(ex.getMessage().contains("tamano maximo"));
    }

    @Test
    void rechazaUnFicheroVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(fichero("foto.jpg", new byte[0], "image/jpeg"), "7"));
    }

    @Test
    void rechazaLaAusenciaDeFichero() {
        assertThrows(IllegalArgumentException.class, () -> validador.validar(null, "7"));
    }

    @Test
    void rechazaAlgoDemasiadoCortoParaTenerFirma() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(fichero("foto.jpg", new byte[] { (byte) 0xFF }, "image/jpeg"), "7"));
    }

    @Test
    void laClaveLaGeneraElServidorYNoUsaElNombreDelCliente() {
        String nombreMalicioso = "../../../etc/passwd.jpg";

        var validada = validador.validar(fichero(nombreMalicioso, conFirma(FIRMA_JPEG, 100), "image/jpeg"), "7");

        assertTrue(validada.clave().startsWith("7/"), "la clave debe colgar del prefijo dado");
        assertFalse(validada.clave().contains(".."), "el nombre del cliente no puede llegar a la clave");
        assertFalse(validada.clave().contains("passwd"));
    }

    @Test
    void dosSubidasNoComparteClave() {
        var primera = validador.validar(fichero("a.jpg", conFirma(FIRMA_JPEG, 100), "image/jpeg"), "7");
        var segunda = validador.validar(fichero("a.jpg", conFirma(FIRMA_JPEG, 100), "image/jpeg"), "7");

        assertNotEquals(primera.clave(), segunda.clave());
    }
}
