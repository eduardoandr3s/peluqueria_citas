package com.segovia.peluqueria.galeria.dto;

import com.segovia.peluqueria.galeria.GaleriaFoto;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Foto de la galeria tal y como la ve un cliente: con URLs, nunca con las claves
 * del almacen. Las URLs se calculan al leer en {@code GaleriaService}.
 *
 * <p>Este DTO lo lee cualquiera <b>sin cuenta</b>, porque el listado es el escaparate.
 * Por eso del dueno solo sale el nombre: ni el id ni el email, que no tienen nada que
 * hacer en una respuesta publica.
 */
@Data
public class GaleriaFotoResponseDTO {

    private Integer idFoto;
    private String titulo;
    private Integer orden;
    private LocalDateTime fechaSubida;

    /** Imagen a tamano completo. Solo se pide al abrir una foto concreta. */
    private String urlImagen;

    /**
     * Miniatura para la rejilla. Si la foto se subio sin miniatura cae a la imagen
     * grande, asi que el cliente puede usar este campo siempre sin comprobar nulos.
     */
    private String urlMiniatura;

    /**
     * Nombre de quien la subio, o null si es del negocio (las de antes de que esto se
     * guardara). Es para mostrar de quien es el trabajo, no para decidir permisos: dos
     * personas pueden llamarse igual.
     */
    private String subidoPorNombre;

    /**
     * Si la subio la cuenta que esta preguntando. Se calcula en el servidor comparando
     * ids, que es lo unico fiable, y es lo que usa el frontend para ocultar las acciones
     * que no le tocan. Sin cuenta o sin dueno es false.
     */
    private boolean mia;

    public static GaleriaFotoResponseDTO desde(GaleriaFoto foto, String urlImagen, String urlMiniatura,
                                               String subidoPorNombre, boolean mia) {
        if (foto == null) {
            return null;
        }
        GaleriaFotoResponseDTO dto = new GaleriaFotoResponseDTO();
        dto.setIdFoto(foto.getIdFoto());
        dto.setTitulo(foto.getTitulo());
        dto.setOrden(foto.getOrden());
        dto.setFechaSubida(foto.getFechaSubida());
        dto.setUrlImagen(urlImagen);
        dto.setUrlMiniatura(urlMiniatura != null ? urlMiniatura : urlImagen);
        dto.setSubidoPorNombre(subidoPorNombre);
        dto.setMia(mia);
        return dto;
    }
}
