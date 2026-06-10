package com.segovia.peluqueria.servicio;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.servicio.dto.ServicioRequestDTO;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.servicio.dto.ServicioUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServicioService {

    private final ServicioRepository servicioRepository;

    public ServicioService(ServicioRepository servicioRepository) {
        this.servicioRepository = servicioRepository;
    }

    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarServicios() {
        return servicioRepository.findByActivoTrue().stream()
                .map(ServicioResponseDTO::desde)
                .toList();
    }

    @Transactional
    public ServicioResponseDTO crearServicio(ServicioRequestDTO request) {
        Servicio servicio = new Servicio();
        servicio.setNombre(request.getNombre());
        servicio.setDescripcion(request.getDescripcion());
        servicio.setPrecio(request.getPrecio());
        servicio.setDuracion(request.getDuracion());
        return ServicioResponseDTO.desde(servicioRepository.save(servicio));
    }

    private Servicio obtenerEntidadPorId(Integer id) {
        return servicioRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Servicio no encontrado con id: " + id));
    }

    @Transactional(readOnly = true)
    public ServicioResponseDTO obtenerServicioPorId(Integer id) {
        return ServicioResponseDTO.desde(obtenerEntidadPorId(id));
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

        return ServicioResponseDTO.desde(servicioRepository.save(servicioExistente));
    }

    @Transactional
    public void eliminarServicio(Integer id) {
        Servicio servicioExistente = obtenerEntidadPorId(id);
        servicioExistente.setActivo(false);
        servicioRepository.save(servicioExistente);
    }
}
