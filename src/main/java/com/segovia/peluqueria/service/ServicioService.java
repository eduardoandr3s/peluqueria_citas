package com.segovia.peluqueria.service;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.repository.ServicioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioService {
    @Autowired
    private ServicioRepository servicioRepository;

    // Listar todos los servicios
    public List<Servicio> listarServicios() {
        return servicioRepository.findAll();
    }

    // Crear un nuevo servicio
    public Servicio crearServicio(Servicio servicio) {
        return servicioRepository.save(servicio);
    }

    // Obtener un servicio por su ID
    public Servicio obtenerServicioPorId(Integer id) {
        return servicioRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Servicio no encontrado con id: " + id));
    }

    // Actualizar un servicio existente
    public Servicio actualizarServicio(Integer id, Servicio servicioActualizado) {
        // Obtener el servicio existente
        Servicio servicioExistente = obtenerServicioPorId(id);

        // Actualizar los campos del servicio existente con los datos del servicio actualizado
        servicioExistente.setNombre(servicioActualizado.getNombre());
        servicioExistente.setDescripcion(servicioActualizado.getDescripcion());
        servicioExistente.setPrecio(servicioActualizado.getPrecio());
        servicioExistente.setDuracion(servicioActualizado.getDuracion());

        // Guardar el servicio actualizado en la base de datos
        return servicioRepository.save(servicioExistente);
    }

    // Eliminar un servicio por su ID
    public void eliminarServicio(Integer id) {
        // Verificar si el servicio existe antes de eliminarlo
        Servicio servicioExistente = obtenerServicioPorId(id);

        // Eliminar el servicio de la base de datos
        servicioRepository.delete(servicioExistente);
    }
}
