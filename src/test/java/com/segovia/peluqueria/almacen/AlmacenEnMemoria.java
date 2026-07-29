package com.segovia.peluqueria.almacen;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Almacen de pega para los tests: guarda los objetos en un mapa.
 *
 * <p>Se usa un doble de verdad y no un mock porque lo que interesa comprobar es
 * el estado resultante (que la clave nueva esta y la anterior ya no), no la
 * secuencia de llamadas.
 */
public class AlmacenEnMemoria implements AlmacenFicheros {

    public record Objeto(byte[] contenido, String contentType) {}

    private final Map<String, Objeto> objetos = new LinkedHashMap<>();

    private boolean fallarAlFirmar;

    /**
     * Hace que firmar falle como lo haria el almacen real cuando no responde. Sirve
     * para comprobar que una URL que no se puede firmar no tumba la peticion.
     */
    public void fallarAlFirmar() {
        this.fallarAlFirmar = true;
    }

    @Override
    public String guardar(String bucket, String clave, byte[] contenido, String contentType) {
        objetos.put(ruta(bucket, clave), new Objeto(contenido, contentType));
        return clave;
    }

    @Override
    public void borrar(String bucket, String clave) {
        objetos.remove(ruta(bucket, clave));
    }

    @Override
    public String urlDeLectura(String bucket, String clave) {
        return "https://almacen.test/" + ruta(bucket, clave);
    }

    /**
     * URL distinguible de la publica a proposito: asi un test puede afirmar que el
     * contenido privado se sirve firmado y no por la ruta abierta.
     */
    @Override
    public String urlFirmada(String bucket, String clave, Duration validez) {
        if (fallarAlFirmar) {
            throw new AlmacenException("Firma no disponible en el doble de test.");
        }
        return "https://almacen.test/firmada/" + ruta(bucket, clave) + "?validez=" + validez.toSeconds();
    }

    public boolean contiene(String bucket, String clave) {
        return objetos.containsKey(ruta(bucket, clave));
    }

    public Objeto obtener(String bucket, String clave) {
        return objetos.get(ruta(bucket, clave));
    }

    public int total() {
        return objetos.size();
    }

    private String ruta(String bucket, String clave) {
        return bucket + "/" + clave;
    }
}
