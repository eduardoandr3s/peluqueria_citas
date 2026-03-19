package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class CitaService {

    private static final LocalTime HORA_APERTURA = LocalTime.of(9, 0);
    private static final LocalTime HORA_CIERRE = LocalTime.of(20, 0);

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;

    public CitaService(CitaRepository citaRepository,
                       UsuarioRepository usuarioRepository,
                       ServicioRepository servicioRepository) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicioRepository = servicioRepository;
    }

    public List<Cita> listarCitas() {
        return citaRepository.findAll();
    }

    public Cita agendarCita(CitaRequestDTO request) {

        Usuario usuarioCompleto = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));
        if (!usuarioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un usuario inactivo.");
        }

        Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
        if (!servicioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un servicio inactivo.");
        }

        validarFechaFutura(request.getFechaHora());
        validarHorarioLaboral(request.getFechaHora(), servicioCompleto.getDuracion());

        validarConflictoHorario(request.getFechaHora(), servicioCompleto.getDuracion(), null);

        Cita cita = new Cita();
        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);
        cita.setFechaHora(request.getFechaHora());
        cita.setEstado(EstadoCita.PENDIENTE);

        return citaRepository.save(cita);
    }

    public Cita obtenerCitaPorId(Integer id){
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    public Cita actualizarCita(Integer id, CitaUpdateDTO request) {

        Cita citaExistente = obtenerCitaPorId(id);

        if (request.getFechaHora() != null) {
            citaExistente.setFechaHora(request.getFechaHora());
        }

        if (request.getEstado() != null) {
            citaExistente.setEstado(request.getEstado());
        }

        if (request.getUsuarioId() != null) {
            Usuario usuarioCompleto = usuarioRepository.findById(request.getUsuarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));
            citaExistente.setUsuario(usuarioCompleto);
        }

        if (request.getServicioId() != null) {
            Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
            citaExistente.setServicio(servicioCompleto);
        }

        if (request.getFechaHora() != null || request.getServicioId() != null) {
            validarFechaFutura(citaExistente.getFechaHora());
            validarHorarioLaboral(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion());
            validarConflictoHorario(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion(), id);
        }

        return citaRepository.save(citaExistente);
    }

    public void eliminarCita(Integer id){
        Cita citaExistente = obtenerCitaPorId(id);
        citaRepository.delete(citaExistente);
    }

    private void validarFechaFutura(LocalDateTime fechaHora) {
        if (fechaHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se puede agendar una cita en el pasado.");
        }
    }

    private void validarHorarioLaboral(LocalDateTime inicio, Integer duracionMinutos) {
        LocalTime horaInicio = inicio.toLocalTime();
        LocalTime horaFin = horaInicio.plusMinutes(duracionMinutos);
        DayOfWeek dia = inicio.getDayOfWeek();

        if (dia == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("No se atiende los domingos.");
        }

        if (horaInicio.isBefore(HORA_APERTURA)) {
            throw new IllegalArgumentException("La cita no puede ser antes de las " + HORA_APERTURA + ".");
        }

        if (horaFin.isAfter(HORA_CIERRE)) {
            throw new IllegalArgumentException("La cita (incluyendo la duracion del servicio) no puede terminar despues de las " + HORA_CIERRE + ".");
        }
    }

    private void validarConflictoHorario(LocalDateTime inicio, Integer duracionMinutos, Integer idExcluir) {
        LocalDateTime fin = inicio.plusMinutes(duracionMinutos);

        int conflictos;
        if (idExcluir != null) {
            conflictos = citaRepository.contarConflictosExcluyendo(inicio, fin, idExcluir);
        } else {
            conflictos = citaRepository.contarConflictos(inicio, fin);
        }

        if (conflictos > 0) {
            throw new ConflictoHorarioException("Ya existe una cita agendada en ese horario. Por favor elige otro horario.");
        }
    }
}
