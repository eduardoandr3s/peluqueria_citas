package com.segovia.peluqueria.service;

import com.segovia.peluqueria.dto.ServicioRequestDTO;
import com.segovia.peluqueria.dto.ServicioUpdateDTO;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.repository.ServicioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioService {

    private final ServicioRepository servicioRepository;

    public ServicioService(ServicioRepository servicioRepository) {
        this.servicioRepository = servicioRepository;
    }

    // Listar solo los servicios activos
    public List<Servicio> listarServicios() {
        return servicioRepository.findByActivoTrue();
    }

    // Crear un nuevo servicio a partir del DTO
    public Servicio crearServicio(ServicioRequestDTO request) {
        Servicio servicio = new Servicio();
        servicio.setNombre(request.getNombre());
        servicio.setDescripcion(request.getDescripcion());
        servicio.setPrecio(request.getPrecio());
        servicio.setDuracion(request.getDuracion());
        return servicioRepository.save(servicio);
    }

    // Obtener un servicio por su ID
    public Servicio obtenerServicioPorId(Integer id) {
        return servicioRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Servicio no encontrado con id: " + id));
    }

    // Actualizar un servicio existente (update parcial)
    public Servicio actualizarServicio(Integer id, ServicioUpdateDTO request) {
        Servicio servicioExistente = obtenerServicioPorId(id);

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

        return servicioRepository.save(servicioExistente);
    }

    // Soft delete: marca el servicio como inactivo en vez de eliminarlo
    public void eliminarServicio(Integer id) {
        Servicio servicioExistente = obtenerServicioPorId(id);
        servicioExistente.setActivo(false);
        servicioRepository.save(servicioExistente);
    }
}
