package com.segovia.peluqueria.calendario;

import com.segovia.peluqueria.calendario.dto.DiaBloqueadoRequestDTO;
import com.segovia.peluqueria.calendario.dto.DiaBloqueadoResponseDTO;
import com.segovia.peluqueria.calendario.dto.DiaCerradoDTO;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.cita.HorarioProperties;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Días en los que la peluquería no abre. Unifica los dos orígenes de cierre:
 * los días de la semana fijos (domingo, vía {@link HorarioProperties}) y los días
 * bloqueados a mano por el administrador (festivos, cierres puntuales).
 */
@Service
public class CalendarioService {

    /** Ventana por defecto del listado de próximos bloqueos (un año). */
    private static final int MESES_LISTADO = 12;

    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-ES");

    private final DiaBloqueadoRepository diaBloqueadoRepository;
    private final CitaRepository citaRepository;
    private final HorarioProperties horario;
    private final Clock clock;

    public CalendarioService(DiaBloqueadoRepository diaBloqueadoRepository,
                             CitaRepository citaRepository,
                             HorarioProperties horario,
                             Clock clock) {
        this.diaBloqueadoRepository = diaBloqueadoRepository;
        this.citaRepository = citaRepository;
        this.horario = horario;
        this.clock = clock;
    }

    /** true si ese día no se atiende, ya sea por el día de la semana o por un bloqueo puntual. */
    @Transactional(readOnly = true)
    public boolean esCerrado(LocalDate fecha) {
        return esDiaSemanaCerrado(fecha) || diaBloqueadoRepository.existsByFecha(fecha);
    }

    /** Motivo del cierre listo para mostrar, o {@code null} si ese día se abre. */
    @Transactional(readOnly = true)
    public String motivoCierre(LocalDate fecha) {
        if (esDiaSemanaCerrado(fecha)) {
            return motivoDiaSemana(fecha.getDayOfWeek());
        }
        return diaBloqueadoRepository.findByFecha(fecha)
                .map(this::motivoBloqueo)
                .orElse(null);
    }

    /** Todos los días cerrados del rango (ambos extremos incluidos), ordenados por fecha. */
    @Transactional(readOnly = true)
    public List<DiaCerradoDTO> diasCerrados(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }

        // Los bloqueos del rango en una sola consulta; el día de la semana se calcula en memoria.
        Map<LocalDate, DiaBloqueado> bloqueos = diaBloqueadoRepository
                .findByFechaBetweenOrderByFecha(desde, hasta).stream()
                .collect(Collectors.toMap(DiaBloqueado::getFecha, Function.identity()));

        List<DiaCerradoDTO> cerrados = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (esDiaSemanaCerrado(fecha)) {
                cerrados.add(new DiaCerradoDTO(fecha, motivoDiaSemana(fecha.getDayOfWeek())));
            } else if (bloqueos.containsKey(fecha)) {
                cerrados.add(new DiaCerradoDTO(fecha, motivoBloqueo(bloqueos.get(fecha))));
            }
        }
        return cerrados;
    }

    /** Bloqueos de hoy en adelante: los pasados no se listan porque ya no son accionables. */
    @Transactional(readOnly = true)
    public List<DiaBloqueadoResponseDTO> listarProximos() {
        return diaBloqueadoRepository.findByFechaGreaterThanEqualOrderByFecha(LocalDate.now(clock)).stream()
                .map(DiaBloqueadoResponseDTO::desde)
                .toList();
    }

    @Transactional
    public DiaBloqueadoResponseDTO bloquear(DiaBloqueadoRequestDTO request) {
        LocalDate fecha = request.getFecha();

        if (fecha.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("No se puede bloquear un dia pasado.");
        }
        if (diaBloqueadoRepository.existsByFecha(fecha)) {
            throw new ConflictoHorarioException("El dia " + fecha + " ya estaba bloqueado.");
        }

        // Bloquear un dia con citas vivas dejaria esas citas sin horario valido: se exige
        // resolverlas antes (anular o reprogramar) en vez de anularlas por sorpresa.
        long citasActivas = citaRepository.contarActivasEnElDia(
                EstadoCita.ANULADA, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        if (citasActivas > 0) {
            throw new ConflictoHorarioException("Hay " + citasActivas + " cita(s) ese dia; anulalas o reprogramalas antes de bloquearlo.");
        }

        DiaBloqueado dia = new DiaBloqueado();
        dia.setFecha(fecha);
        dia.setMotivo(request.getMotivo() != null && !request.getMotivo().isBlank()
                ? request.getMotivo().trim()
                : null);
        return DiaBloqueadoResponseDTO.desde(diaBloqueadoRepository.save(dia));
    }

    @Transactional
    public void desbloquear(Integer id) {
        DiaBloqueado dia = diaBloqueadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dia bloqueado no encontrado con id: " + id));
        diaBloqueadoRepository.delete(dia);
    }

    private boolean esDiaSemanaCerrado(LocalDate fecha) {
        return horario.getDiasCerrados().contains(fecha.getDayOfWeek());
    }

    private String motivoDiaSemana(DayOfWeek dia) {
        return "Cerrado (" + dia.getDisplayName(TextStyle.FULL, LOCALE_ES) + ")";
    }

    private String motivoBloqueo(DiaBloqueado dia) {
        return dia.getMotivo() != null ? dia.getMotivo() : "Cerrado";
    }
}
