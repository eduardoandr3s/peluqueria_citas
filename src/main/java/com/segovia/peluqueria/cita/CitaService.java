package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaAnuladaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaModificadaEvent;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.peluquero.PeluqueroRepository;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CitaService {

    private static final int PASO_SLOT_MINUTOS = 30;

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final PeluqueroRepository peluqueroRepository;
    private final HorarioProperties horario;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CitaService(CitaRepository citaRepository,
                       UsuarioRepository usuarioRepository,
                       ServicioRepository servicioRepository,
                       PeluqueroRepository peluqueroRepository,
                       HorarioProperties horario,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicioRepository = servicioRepository;
        this.peluqueroRepository = peluqueroRepository;
        this.horario = horario;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CitaResponseDTO> listarCitas(String emailAutenticado, Pageable pageable) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        // Un ADMIN ve todas las citas; un USER solo las suyas.
        Page<Cita> citas = esAdmin(actual)
                ? citaRepository.findAll(pageable)
                : citaRepository.findByUsuarioIdUsuario(actual.getIdUsuario(), pageable);
        return citas.map(this::mapearAResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<String> obtenerDisponibilidad(LocalDate fecha, Integer idServicio, Integer peluqueroId) {
        if (fecha.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("No se puede consultar disponibilidad en una fecha pasada.");
        }

        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + idServicio));
        if (!servicio.getActivo()) {
            throw new IllegalArgumentException("El servicio no esta disponible.");
        }

        // Si se filtra por peluquero, debe existir y estar activo.
        validarPeluqueroActivo(peluqueroId);

        // Domingo cerrado: no hay slots.
        if (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return List.of();
        }

        int duracion = servicio.getDuracion();
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<String> slotsLibres = new ArrayList<>();

        LocalTime inicio = horario.getApertura();
        // El último inicio válido es aquel cuya cita aún termina a la hora de cierre o antes.
        while (!inicio.plusMinutes(duracion).isAfter(horario.getCierre())) {
            LocalDateTime inicioSlot = fecha.atTime(inicio);
            LocalDateTime finSlot = inicioSlot.plusMinutes(duracion);

            // Para el día de hoy, descartar los slots cuya hora de inicio ya pasó.
            boolean yaPaso = inicioSlot.isBefore(ahora);
            if (!yaPaso && !hayConflicto(inicioSlot, finSlot, null, peluqueroId)) {
                slotsLibres.add(inicio.toString());
            }

            inicio = inicio.plusMinutes(PASO_SLOT_MINUTOS);
        }

        return slotsLibres;
    }

    /**
     * Valida que el peluquero indicado exista y esté activo. Devuelve la entidad,
     * o {@code null} si no se especifica peluquero (cita sin asignar).
     */
    private Peluquero validarPeluqueroActivo(Integer peluqueroId) {
        if (peluqueroId == null) {
            return null;
        }
        Peluquero peluquero = peluqueroRepository.findById(peluqueroId)
                .orElseThrow(() -> new ResourceNotFoundException("Peluquero no encontrado con ID: " + peluqueroId));
        if (!peluquero.getActivo()) {
            throw new IllegalArgumentException("No se puede usar un peluquero inactivo.");
        }
        return peluquero;
    }

    private boolean hayConflicto(LocalDateTime inicio, LocalDateTime fin, Integer idExcluir, Integer peluqueroId) {
        int conflictos;
        if (peluqueroId != null) {
            conflictos = idExcluir != null
                    ? citaRepository.contarConflictosExcluyendoConPeluquero(inicio, fin, idExcluir, peluqueroId)
                    : citaRepository.contarConflictosConPeluquero(inicio, fin, peluqueroId);
        } else {
            conflictos = idExcluir != null
                    ? citaRepository.contarConflictosExcluyendo(inicio, fin, idExcluir)
                    : citaRepository.contarConflictos(inicio, fin);
        }
        return conflictos > 0;
    }

    @Transactional
    public CitaResponseDTO agendarCita(CitaRequestDTO request, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        // Un USER solo puede agendar citas para sí mismo; un ADMIN puede agendar para cualquiera.
        Integer idUsuarioObjetivo = esAdmin(actual) ? request.getUsuarioId() : actual.getIdUsuario();

        Usuario usuarioCompleto = usuarioRepository.findById(idUsuarioObjetivo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + idUsuarioObjetivo));
        if (!usuarioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un usuario inactivo.");
        }

        Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
        if (!servicioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un servicio inactivo.");
        }

        Peluquero peluquero = validarPeluqueroActivo(request.getPeluqueroId());

        validarFechaFutura(request.getFechaHora());
        validarHorarioLaboral(request.getFechaHora(), servicioCompleto.getDuracion());

        validarConflictoHorario(request.getFechaHora(), servicioCompleto.getDuracion(), null, request.getPeluqueroId());

        Cita cita = new Cita();
        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);
        cita.setPeluquero(peluquero);
        cita.setFechaHora(request.getFechaHora());
        cita.setEstado(EstadoCita.PENDIENTE);

        Cita guardada = citaRepository.save(cita);
        eventPublisher.publishEvent(new CitaAgendadaEvent(
                usuarioCompleto.getNombre(), usuarioCompleto.getEmail(),
                servicioCompleto.getNombre(), guardada.getFechaHora()));
        return mapearAResponseDTO(guardada);
    }

    @Transactional(readOnly = true)
    public CitaResponseDTO obtenerCitaPorId(Integer id, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = obtenerEntidadPorId(id);
        verificarAcceso(cita, actual);
        return mapearAResponseDTO(cita);
    }

    @Transactional
    public CitaResponseDTO actualizarCita(Integer id, CitaUpdateDTO request, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita citaExistente = obtenerEntidadPorId(id);
        verificarAcceso(citaExistente, actual);

        if (request.getFechaHora() != null) {
            citaExistente.setFechaHora(request.getFechaHora());
        }

        if (request.getEstado() != null) {
            citaExistente.setEstado(request.getEstado());
        }

        // Solo un ADMIN puede reasignar una cita a otro usuario.
        if (request.getUsuarioId() != null && esAdmin(actual)) {
            Usuario usuarioCompleto = usuarioRepository.findById(request.getUsuarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));
            citaExistente.setUsuario(usuarioCompleto);
        }

        if (request.getServicioId() != null) {
            Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
            citaExistente.setServicio(servicioCompleto);
        }

        if (request.getPeluqueroId() != null) {
            citaExistente.setPeluquero(validarPeluqueroActivo(request.getPeluqueroId()));
        }

        Integer peluqueroIdParaConflicto = citaExistente.getPeluquero() != null
                ? citaExistente.getPeluquero().getIdPeluquero() : null;

        if (request.getFechaHora() != null || request.getServicioId() != null || request.getPeluqueroId() != null) {
            validarFechaFutura(citaExistente.getFechaHora());
            validarHorarioLaboral(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion());
            validarConflictoHorario(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion(), id, peluqueroIdParaConflicto);
        }

        boolean anulada = request.getEstado() == EstadoCita.ANULADA;
        boolean reprogramada = request.getFechaHora() != null || request.getServicioId() != null;

        Cita guardada = citaRepository.save(citaExistente);

        Usuario cliente = guardada.getUsuario();
        if (anulada) {
            eventPublisher.publishEvent(new CitaAnuladaEvent(
                    cliente.getNombre(), cliente.getEmail(),
                    guardada.getServicio().getNombre(), guardada.getFechaHora()));
        } else if (reprogramada) {
            eventPublisher.publishEvent(new CitaModificadaEvent(
                    cliente.getNombre(), cliente.getEmail(),
                    guardada.getServicio().getNombre(), guardada.getFechaHora()));
        }

        return mapearAResponseDTO(guardada);
    }

    @Transactional
    public void eliminarCita(Integer id, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita citaExistente = obtenerEntidadPorId(id);
        verificarAcceso(citaExistente, actual);

        // Capturamos los datos antes de borrar para poder notificar la anulacion.
        Usuario cliente = citaExistente.getUsuario();
        CitaAnuladaEvent evento = new CitaAnuladaEvent(
                cliente.getNombre(), cliente.getEmail(),
                citaExistente.getServicio().getNombre(), citaExistente.getFechaHora());

        citaRepository.delete(citaExistente);
        eventPublisher.publishEvent(evento);
    }

    private Cita obtenerEntidadPorId(Integer id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    private Usuario obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    private boolean esAdmin(Usuario usuario) {
        return usuario.getRol() == Rol.ADMIN;
    }

    // Solo el dueño de la cita o un ADMIN pueden verla, modificarla o eliminarla.
    private void verificarAcceso(Cita cita, Usuario actual) {
        if (!esAdmin(actual) && !cita.getUsuario().getIdUsuario().equals(actual.getIdUsuario())) {
            throw new AccessDeniedException("No tienes permiso para acceder a este recurso.");
        }
    }

    private CitaResponseDTO mapearAResponseDTO(Cita cita) {
        CitaResponseDTO dto = new CitaResponseDTO();
        dto.setIdCita(cita.getIdCita());
        dto.setFechaHora(cita.getFechaHora());
        dto.setEstado(cita.getEstado());
        dto.setUsuario(UsuarioResponseDTO.desde(cita.getUsuario()));
        dto.setServicio(ServicioResponseDTO.desde(cita.getServicio()));
        dto.setPeluquero(PeluqueroResponseDTO.desde(cita.getPeluquero()));
        return dto;
    }

    private void validarFechaFutura(LocalDateTime fechaHora) {
        if (fechaHora.isBefore(LocalDateTime.now(clock))) {
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

        if (horaInicio.isBefore(horario.getApertura())) {
            throw new IllegalArgumentException("La cita no puede ser antes de las " + horario.getApertura() + ".");
        }

        if (horaFin.isAfter(horario.getCierre())) {
            throw new IllegalArgumentException("La cita (incluyendo la duracion del servicio) no puede terminar despues de las " + horario.getCierre() + ".");
        }
    }

    private void validarConflictoHorario(LocalDateTime inicio, Integer duracionMinutos, Integer idExcluir, Integer peluqueroId) {
        if (hayConflicto(inicio, inicio.plusMinutes(duracionMinutos), idExcluir, peluqueroId)) {
            throw new ConflictoHorarioException("Ya existe una cita agendada en ese horario. Por favor elige otro horario.");
        }
    }
}
