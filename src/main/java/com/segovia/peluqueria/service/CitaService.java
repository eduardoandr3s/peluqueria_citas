package com.segovia.peluqueria.service;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Cita;
import com.segovia.peluqueria.model.EstadoCita;
import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.repository.CitaRepository;
import com.segovia.peluqueria.repository.ServicioRepository;
import com.segovia.peluqueria.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service // anotación para marcar esta clase como un servicio de Spring
public class CitaService {

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    public List<Cita> listarCitas() {
        return citaRepository.findAll();
    }

    public Cita agendarCita(Cita cita) {

        //1. Buscar el usuario en base de datos
        Usuario usuarioCompleto = usuarioRepository.findById(cita.getUsuario().getIdUsuario())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + cita.getUsuario().getIdUsuario()));

        //2. Buscar el servicio en base de datos
        Servicio servicioCompleto = servicioRepository.findById(cita.getServicio().getIdServicio())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + cita.getServicio().getIdServicio()));

        // 3. Asignar el usuario y servicio completos a la cita

        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);

        //4. Si el estado de la cita no se ha especificado, asignar "PENDIENTE" por defecto

        if (cita.getEstado() == null) {
            cita.setEstado(EstadoCita.PENDIENTE);
        }

        //5. Guardar la cita en la base de datos
        return citaRepository.save(cita);
    }

    // método para obtener una cita por su ID, lanzando una excepción si no se encuentra
    public Cita obtenerCitaPorId(Integer id){
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    // método para actualizar una cita existente, permitiendo actualizar la fecha/hora, estado, usuario y servicio
    public Cita actualizarCita(Integer id, Cita citaActualizada ){

        //1. Obtener la cita existente de la base de datos
        Cita citaExistente = obtenerCitaPorId(id);

        //2. Actualizar los campos de la cita existente con los valores de la cita actualizada, si no son nulos o vacíos
        if (citaActualizada.getFechaHora() != null){
            citaExistente.setFechaHora(citaActualizada.getFechaHora());
        }


        // 3. Solo actualizar el estado si se ha proporcionado un valor no nulo y no vacío
        if (citaActualizada.getEstado() != null){
            citaExistente.setEstado(citaActualizada.getEstado());
        }

        //4. Si se ha proporcionado un nuevo usuario o servicio, buscar el registro completo en la base de datos y asignarlo a la cita existente
        if (citaActualizada.getUsuario() != null && citaActualizada.getUsuario().getIdUsuario() != null){
            Usuario usuarioCompleto = usuarioRepository.findById(citaActualizada.getUsuario().getIdUsuario())
                    .orElseThrow(()-> new ResourceNotFoundException("Usuario no encontrado con ID: " + citaActualizada.getUsuario().getIdUsuario()));
            citaExistente.setUsuario(usuarioCompleto);
        }


        //5. Si se ha proporcionado un nuevo servicio o servicio, buscar el registro completo en la base de datos y asignarlo a la cita existente
        if (citaActualizada.getServicio() != null && citaActualizada.getServicio().getIdServicio() != null){
            Servicio servicioCompleto = servicioRepository.findById(citaActualizada.getServicio().getIdServicio())
                    .orElseThrow(()-> new ResourceNotFoundException("Servicio no encontrado con ID: " + citaActualizada.getServicio().getIdServicio()));
            citaExistente.setServicio(servicioCompleto);
        }

        //6. Guardar la cita actualizada en la base de datos
        return citaRepository.save(citaExistente);
    }

    // método para eliminar una cita por su ID, lanzando una excepción si no se encuentra
    public void eliminarCita(Integer id){
        Cita citaExistente = obtenerCitaPorId(id);
        citaRepository.delete(citaExistente);
    }

}
