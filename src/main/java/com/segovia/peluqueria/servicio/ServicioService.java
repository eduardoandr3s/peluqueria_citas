package com.segovia.peluqueria.servicio;

import com.segovia.peluqueria.almacen.AlmacenFicheros;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.servicio.dto.ServicioRequestDTO;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.servicio.dto.ServicioUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final AlmacenFicheros almacen;
    private final ValidadorImagen validadorImagen;
    private final AlmacenProperties almacenProperties;

    public ServicioService(ServicioRepository servicioRepository,
                           AlmacenFicheros almacen,
                           ValidadorImagen validadorImagen,
                           AlmacenProperties almacenProperties) {
        this.servicioRepository = servicioRepository;
        this.almacen = almacen;
        this.validadorImagen = validadorImagen;
        this.almacenProperties = almacenProperties;
    }

    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarServicios() {
        return servicioRepository.findByActivoTrue().stream()
                .map(this::aDTO)
                .toList();
    }

    /** Traduce la clave guardada a una URL utilizable por el cliente. */
    private ServicioResponseDTO aDTO(Servicio servicio) {
        String clave = servicio.getImagenClave();
        String url = (clave == null || clave.isBlank())
                ? null
                : almacen.urlDeLectura(almacenProperties.getBucketServicios(), clave);
        return ServicioResponseDTO.desde(servicio, url);
    }

    /**
     * Sustituye la foto del servicio. La clave la genera el validador (nunca el
     * cliente) y la anterior se borra del almacen para no dejar objetos huerfanos
     * ocupando cuota.
     */
    @Transactional
    public ServicioResponseDTO subirImagen(Integer id, MultipartFile imagen) {
        Servicio servicio = obtenerEntidadPorId(id);
        ValidadorImagen.ImagenValidada validada = validadorImagen.validar(imagen, String.valueOf(id));
        String bucket = almacenProperties.getBucketServicios();

        String claveAnterior = servicio.getImagenClave();
        String clave = almacen.guardar(bucket, validada.clave(), validada.contenido(), validada.contentType());
        servicio.setImagenClave(clave);
        Servicio guardado = servicioRepository.save(servicio);

        if (claveAnterior != null && !claveAnterior.isBlank() && !claveAnterior.equals(clave)) {
            almacen.borrar(bucket, claveAnterior);
        }
        return aDTO(guardado);
    }

    /** Quita la foto del servicio. Es idempotente: sin foto, no hace nada. */
    @Transactional
    public ServicioResponseDTO borrarImagen(Integer id) {
        Servicio servicio = obtenerEntidadPorId(id);
        String clave = servicio.getImagenClave();
        if (clave == null || clave.isBlank()) {
            return aDTO(servicio);
        }
        servicio.setImagenClave(null);
        Servicio guardado = servicioRepository.save(servicio);
        almacen.borrar(almacenProperties.getBucketServicios(), clave);
        return aDTO(guardado);
    }

    @Transactional
    public ServicioResponseDTO crearServicio(ServicioRequestDTO request) {
        Servicio servicio = new Servicio();
        servicio.setNombre(request.getNombre());
        servicio.setDescripcion(request.getDescripcion());
        servicio.setPrecio(request.getPrecio());
        servicio.setDuracion(request.getDuracion());
        return aDTO(servicioRepository.save(servicio));
    }

    private Servicio obtenerEntidadPorId(Integer id) {
        return servicioRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Servicio no encontrado con id: " + id));
    }

    @Transactional(readOnly = true)
    public ServicioResponseDTO obtenerServicioPorId(Integer id) {
        return aDTO(obtenerEntidadPorId(id));
    }

    @Transactional
    public ServicioResponseDTO actualizarServicio(Integer id, ServicioUpdateDTO request) {
        Servicio servicioExistente = obtenerEntidadPorId(id);

        if (request.getNombre() != null && !request.getNombre().isEmpty()) {
            servicioExistente.setNombre(request.getNombre());
        }
        if (request.getDescripcion() != null) {
            servicioExistente.setDescripcion(request.getDescripcion());
        }
        if (request.getPrecio() != null) {
            servicioExistente.setPrecio(request.getPrecio());
        }
        if (request.getDuracion() != null) {
            servicioExistente.setDuracion(request.getDuracion());
        }

        return aDTO(servicioRepository.save(servicioExistente));
    }

    @Transactional
    public void eliminarServicio(Integer id) {
        Servicio servicioExistente = obtenerEntidadPorId(id);
        servicioExistente.setActivo(false);
        servicioRepository.save(servicioExistente);
    }
}
