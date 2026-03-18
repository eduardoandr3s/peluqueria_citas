package com.segovia.peluqueria.service;

import com.segovia.peluqueria.exception.ConflictoHorarioException;
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

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class CitaService {

    private static final LocalTime HORA_APERTURA = LocalTime.of(9, 0);
    private static final LocalTime HORA_CIERRE = LocalTime.of(20, 0);

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

        // 4. Validar fecha futura y horario laboral
        validarFechaFutura(cita.getFechaHora());
        validarHorarioLaboral(cita.getFechaHora(), servicioCompleto.getDuracion());

        // 5. Validar que no haya conflicto de horarios
        validarConflictoHorario(cita.getFechaHora(), servicioCompleto.getDuracion(), null);

        //5. Si el estado de la cita no se ha especificado, asignar "PENDIENTE" por defecto

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

        // 6. Si se cambio la fecha/hora o el servicio, validar fecha, horario y conflictos
        if (citaActualizada.getFechaHora() != null || citaActualizada.getServicio() != null) {
            validarFechaFutura(citaExistente.getFechaHora());
            validarHorarioLaboral(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion());
            validarConflictoHorario(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion(), id);
        }

        //7. Guardar la cita actualizada en la base de datos
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
