package com.segovia.peluqueria.service;

import com.segovia.peluqueria.dto.CitaRequestDTO;
import com.segovia.peluqueria.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Cita;
import com.segovia.peluqueria.model.EstadoCita;
import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.repository.CitaRepository;
import com.segovia.peluqueria.repository.ServicioRepository;
import com.segovia.peluqueria.repository.UsuarioRepository;
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

        // 1. Buscar el usuario en base de datos
        Usuario usuarioCompleto = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));
        if (!usuarioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un usuario inactivo.");
        }

        // 2. Buscar el servicio en base de datos
        Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
        if (!servicioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un servicio inactivo.");
        }

        // 3. Validar fecha futura y horario laboral
        validarFechaFutura(request.getFechaHora());
        validarHorarioLaboral(request.getFechaHora(), servicioCompleto.getDuracion());

        // 4. Validar que no haya conflicto de horarios
        validarConflictoHorario(request.getFechaHora(), servicioCompleto.getDuracion(), null);

        // 5. Crear la entidad Cita
        Cita cita = new Cita();
        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);
        cita.setFechaHora(request.getFechaHora());
        cita.setEstado(EstadoCita.PENDIENTE);

        // 6. Guardar la cita en la base de datos
        return citaRepository.save(cita);
    }

    // método para obtener una cita por su ID, lanzando una excepción si no se encuentra
    public Cita obtenerCitaPorId(Integer id){
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    // Actualizar una cita existente (update parcial)
    public Cita actualizarCita(Integer id, CitaUpdateDTO request) {

        // 1. Obtener la cita existente de la base de datos
        Cita citaExistente = obtenerCitaPorId(id);

        // 2. Actualizar fecha/hora si se proporciona
        if (request.getFechaHora() != null) {
            citaExistente.setFechaHora(request.getFechaHora());
        }

        // 3. Actualizar estado si se proporciona
        if (request.getEstado() != null) {
            citaExistente.setEstado(request.getEstado());
        }

        // 4. Actualizar usuario si se proporciona
        if (request.getUsuarioId() != null) {
            Usuario usuarioCompleto = usuarioRepository.findById(request.getUsuarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));
            citaExistente.setUsuario(usuarioCompleto);
        }

        // 5. Actualizar servicio si se proporciona
        if (request.getServicioId() != null) {
            Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
            citaExistente.setServicio(servicioCompleto);
        }

        // 6. Si se cambio la fecha/hora o el servicio, validar fecha, horario y conflictos
        if (request.getFechaHora() != null || request.getServicioId() != null) {
            validarFechaFutura(citaExistente.getFechaHora());
            validarHorarioLaboral(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion());
            validarConflictoHorario(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion(), id);
        }

        // 7. Guardar la cita actualizada en la base de datos
        return citaRepository.save(citaExistente);
    }

    // método para eliminar una cita por su ID, lanzando una excepción si no se encuentra
    public void eliminarCita(Integer id){
        Cita citaExistente = obtenerCitaPorId(id);
        citaRepository.delete(citaExistente);
    }

    // Valida que la fecha de la cita sea en el futuro
    private void validarFechaFutura(LocalDateTime fechaHora) {
        if (fechaHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se puede agendar una cita en el pasado.");
        }
    }

    // Valida que la cita (inicio y fin) esté dentro del horario laboral: Lunes a Sabado, 9:00 - 20:00
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

    // Valida que no existan citas que se solapen con el rango [inicio, inicio + duracion)
    // Si idExcluir no es null, excluye esa cita de la busqueda (para actualizaciones)
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
