package com.segovia.peluqueria.galeria.dto;

import com.segovia.peluqueria.galeria.GaleriaFoto;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Foto de la galeria tal y como la ve un cliente: con URLs, nunca con las claves
 * del almacen. Las URLs se calculan al leer en {@code GaleriaService}.
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

    public static GaleriaFotoResponseDTO desde(GaleriaFoto foto, String urlImagen, String urlMiniatura) {
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
        return dto;
    }
}
