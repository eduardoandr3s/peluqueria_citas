package com.segovia.peluqueria.almacen;

/**
 * Puerto de almacenamiento de ficheros binarios.
 *
 * <p>Existe porque el disco del contenedor de produccion es efimero: cada
 * despliegue lo borra, asi que los binarios NO pueden vivir junto a la
 * aplicacion. La base de datos guarda solo la clave del objeto y la URL se
 * construye al leer, de modo que cambiar de bucket o de proveedor no obliga a
 * migrar ninguna fila.
 *
 * <p>Implementaciones: {@link SupabaseStorageAlmacen} en produccion y
 * {@link AlmacenLocal} cuando no hay credenciales, para que el repositorio se
 * pueda clonar y arrancar sin cuenta en ningun servicio externo.
 */
public interface AlmacenFicheros {

    /**
     * Guarda (o reemplaza) el objeto y devuelve la clave con la que quedo
     * almacenado, que es lo que hay que persistir.
     */
    String guardar(String bucket, String clave, byte[] contenido, String contentType);

    /** Borra el objeto. No falla si ya no existe: borrar es idempotente. */
    void borrar(String bucket, String clave);

    /** URL con la que un cliente puede descargar el objeto. */
    String urlDeLectura(String bucket, String clave);
}
