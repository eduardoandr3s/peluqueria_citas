package com.segovia.peluqueria.service;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Cita;
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
        Usuario usuarioCompleto = usuarioRepository.findById(cita.getUsuario().getId_usuario())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + cita.getUsuario().getId_usuario()));

        //2. Buscar el servicio en base de datos
        Servicio servicioCompleto = servicioRepository.findById(cita.getServicio().getId_servicio())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + cita.getServicio().getId_servicio()));

        // 3. Asignar el usuario y servicio completos a la cita

        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);

        //4. Si el estado de la cita no se ha especificado, asignar "PENDIENTE" por defecto

        if (cita.getEstado() == null || cita.getEstado().isEmpty()) {
            cita.setEstado("PENDIENTE");
        }

        //5. Guardar la cita en la base de datos
        return citaRepository.save(cita);
    }

}
