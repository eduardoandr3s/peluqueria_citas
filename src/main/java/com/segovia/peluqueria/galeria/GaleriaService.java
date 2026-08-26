package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.almacen.AlmacenFicheros;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoResponseDTO;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GaleriaService {

    /** Carpetas logicas dentro del bucket, para no mezclar tamanos. */
    private static final String PREFIJO_IMAGEN = "fotos";
    private static final String PREFIJO_MINIATURA = "miniaturas";

    private final GaleriaFotoRepository galeriaFotoRepository;
    private final AlmacenFicheros almacen;
    private final ValidadorImagen validadorImagen;
    private final AlmacenProperties almacenProperties;

    public GaleriaService(GaleriaFotoRepository galeriaFotoRepository,
                          AlmacenFicheros almacen,
                          ValidadorImagen validadorImagen,
                          AlmacenProperties almacenProperties) {
        this.galeriaFotoRepository = galeriaFotoRepository;
        this.almacen = almacen;
        this.validadorImagen = validadorImagen;
        this.almacenProperties = almacenProperties;
    }

    @Transactional(readOnly = true)
    public List<GaleriaFotoResponseDTO> listarFotos() {
        return galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc().stream()
                .map(this::aDTO)
                .toList();
    }

    /**
     * Sube una foto nueva al final de la rejilla.
     *
     * <p>La miniatura viene generada por el cliente, que es donde ya se redimensiona
     * la foto de servicio y el avatar: el servidor tiene 0,1 CPU en produccion y
     * escalar imagenes ahi seria gastarla en algo que el navegador hace gratis. Se
     * valida igual que la grande (por los bytes, no por lo que diga la peticion) y,
     * si no llega, la fila queda sin miniatura y al leer se cae a la imagen grande.
     */
    @Transactional
    public GaleriaFotoResponseDTO subirFoto(MultipartFile imagen, MultipartFile miniatura, String titulo) {
        ValidadorImagen.ImagenValidada grande = validadorImagen.validar(imagen, PREFIJO_IMAGEN);
        String bucket = almacenProperties.getBucketGaleria();

        GaleriaFoto foto = new GaleriaFoto();
        foto.setImagenClave(almacen.guardar(bucket, grande.clave(), grande.contenido(), grande.contentType()));

        if (miniatura != null && !miniatura.isEmpty()) {
            ValidadorImagen.ImagenValidada pequena = validadorImagen.validar(miniatura, PREFIJO_MINIATURA);
            foto.setMiniaturaClave(
                    almacen.guardar(bucket, pequena.clave(), pequena.contenido(), pequena.contentType()));
        }

        foto.setTitulo(normalizar(titulo));
        foto.setOrden(siguienteOrden());
        return aDTO(galeriaFotoRepository.save(foto));
    }

    /** Cambia titulo u orden. Lo que no venga en el DTO se queda como estaba. */
    @Transactional
    public GaleriaFotoResponseDTO actualizarFoto(Integer id, GaleriaFotoUpdateDTO request) {
        GaleriaFoto foto = obtenerEntidadPorId(id);
        if (request.getTitulo() != null) {
            foto.setTitulo(normalizar(request.getTitulo()));
        }
        if (request.getOrden() != null) {
            foto.setOrden(Math.max(0, request.getOrden()));
        }
        return aDTO(galeriaFotoRepository.save(foto));
    }

    /**
     * Borra la foto y sus DOS objetos del almacen. Si solo se borrara la grande, la
     * miniatura se quedaria comiendo cuota sin que nada la referencie.
     */
    @Transactional
    public void eliminarFoto(Integer id) {
        GaleriaFoto foto = obtenerEntidadPorId(id);
        String bucket = almacenProperties.getBucketGaleria();
        galeriaFotoRepository.delete(foto);

        almacen.borrar(bucket, foto.getImagenClave());
        if (foto.getMiniaturaClave() != null && !foto.getMiniaturaClave().isBlank()) {
            almacen.borrar(bucket, foto.getMiniaturaClave());
        }
    }

    private GaleriaFoto obtenerEntidadPorId(Integer id) {
        return galeriaFotoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Foto de galeria no encontrada con id: " + id));
    }

    private int siguienteOrden() {
        return galeriaFotoRepository.findFirstByOrderByOrdenDescIdFotoDesc()
                .map(ultima -> ultima.getOrden() + 1)
                .orElse(0);
    }

    /** Un titulo en blanco es no tener titulo; no se guardan cadenas vacias. */
    private String normalizar(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return null;
        }
        return titulo.trim();
    }

    /** Traduce las claves guardadas a URLs. El bucket es publico: material promocional. */
    private GaleriaFotoResponseDTO aDTO(GaleriaFoto foto) {
        String bucket = almacenProperties.getBucketGaleria();
        String urlImagen = almacen.urlDeLectura(bucket, foto.getImagenClave());
        String urlMiniatura = (foto.getMiniaturaClave() == null || foto.getMiniaturaClave().isBlank())
                ? null
                : almacen.urlDeLectura(bucket, foto.getMiniaturaClave());
        return GaleriaFotoResponseDTO.desde(foto, urlImagen, urlMiniatura);
    }
}
