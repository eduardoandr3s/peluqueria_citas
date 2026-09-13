package com.segovia.peluqueria.notificacion;

import com.segovia.peluqueria.cita.Cita;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.notificacion.evento.CitaRecordatorioEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.segovia.peluqueria.modulo.Modulo;
import com.segovia.peluqueria.modulo.ModuloService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RecordatorioCitaScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioCitaScheduler.class);

    private final CitaRepository citaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final long horasAntes;
    private final ModuloService moduloService;

    public RecordatorioCitaScheduler(CitaRepository citaRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock,
                                     @Value("${peluqueria.recordatorio.horas-antes}") long horasAntes,
                                     ModuloService moduloService) {
        this.citaRepository = citaRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.horasAntes = horasAntes;
        this.moduloService = moduloService;
    }

    // Cada hora: el recordatorio se envia con 24 h de antelacion, asi que comprobar
    // una vez por hora sigue garantizando >=23 h de aviso y reduce conexiones a la BD.
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void procesarRecordatorios() {
        // Con el modulo apagado no se manda nada y, sobre todo, **no se marca ninguna cita
        // como avisada**: si se marcaran, volver a encenderlo dejaria sin recordatorio a
        // todas las citas de esa ventana. Se sale y ya; la programacion no se toca.
        if (!moduloService.estaActivo(Modulo.RECORDATORIOS_EMAIL)) {
            return;
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime desde = ahora;
        LocalDateTime hasta = ahora.plusHours(horasAntes);

        List<Cita> pendientes = citaRepository
                .findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                        EstadoCita.CONFIRMADA, desde, hasta);

        for (Cita cita : pendientes) {
            cita.setRecordatorioEnviado(true);
            citaRepository.save(cita);

            eventPublisher.publishEvent(new CitaRecordatorioEvent(
                    cita.getUsuario().getNombre(),
                    cita.getUsuario().getEmail(),
                    cita.getServicio().getNombre(),
                    cita.getFechaHora()));

            log.info("Recordatorio programado para cita {} de {}", cita.getIdCita(), cita.getUsuario().getEmail());
        }
    }
}
