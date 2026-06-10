package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<CitaResponseDTO> listarCitas(String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        // Un ADMIN ve todas las citas; un USER solo las suyas.
        List<Cita> citas = esAdmin(actual)
                ? citaRepository.findAll()
                : citaRepository.findByUsuarioIdUsuario(actual.getIdUsuario());
        return citas.stream().map(this::mapearAResponseDTO).toList();
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

        validarFechaFutura(request.getFechaHora());
        validarHorarioLaboral(request.getFechaHora(), servicioCompleto.getDuracion());

        validarConflictoHorario(request.getFechaHora(), servicioCompleto.getDuracion(), null);

        Cita cita = new Cita();
        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);
        cita.setFechaHora(request.getFechaHora());
        cita.setEstado(EstadoCita.PENDIENTE);

        return mapearAResponseDTO(citaRepository.save(cita));
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

        if (request.getFechaHora() != null || request.getServicioId() != null) {
            validarFechaFutura(citaExistente.getFechaHora());
            validarHorarioLaboral(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion());
            validarConflictoHorario(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion(), id);
        }

        return mapearAResponseDTO(citaRepository.save(citaExistente));
    }

    @Transactional
    public void eliminarCita(Integer id, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita citaExistente = obtenerEntidadPorId(id);
        verificarAcceso(citaExistente, actual);
        citaRepository.delete(citaExistente);
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
        return dto;
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
