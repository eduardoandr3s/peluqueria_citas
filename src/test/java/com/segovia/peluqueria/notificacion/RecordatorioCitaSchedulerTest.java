package com.segovia.peluqueria.notificacion;

import com.segovia.peluqueria.cita.Cita;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.notificacion.evento.CitaRecordatorioEvent;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordatorioCitaSchedulerTest {

    private static final long HORAS_ANTES = 24;

    private CitaRepository citaRepository;
    private ApplicationEventPublisher eventPublisher;
    private RecordatorioCitaScheduler scheduler;
    private Clock clock;

    @BeforeEach
    void setUp() {
        citaRepository = mock(CitaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        // Fijamos el reloj en una fecha conocida: 2026-07-08 10:00
        clock = Clock.fixed(Instant.parse("2026-07-08T08:00:00Z"), ZoneId.of("Europe/Madrid"));
        scheduler = new RecordatorioCitaScheduler(citaRepository, eventPublisher, clock, HORAS_ANTES);
    }

    private Usuario crearUsuario(String email) {
        Usuario u = new Usuario();
        u.setIdUsuario(1);
        u.setNombre("Cliente");
        u.setEmail(email);
        return u;
    }

    private Servicio crearServicio() {
        Servicio s = new Servicio();
        s.setIdServicio(1);
        s.setNombre("Corte");
        return s;
    }

    private Cita crearCita(Integer id, LocalDateTime fechaHora, EstadoCita estado, boolean recordatorioEnviado) {
        Cita c = new Cita();
        c.setIdCita(id);
        c.setUsuario(crearUsuario("cliente" + id + "@test.com"));
        c.setServicio(crearServicio());
        c.setFechaHora(fechaHora);
        c.setEstado(estado);
        c.setRecordatorioEnviado(recordatorioEnviado);
        return c;
    }

    @Test
    void procesarRecordatorios_citaDentroDeVentana_publicaEventoYMarcaFlag() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime fechaCita = ahora.plusHours(12); // dentro de la ventana de 24h
        Cita cita = crearCita(1, fechaCita, EstadoCita.CONFIRMADA, false);

        when(citaRepository.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                EstadoCita.CONFIRMADA, ahora, ahora.plusHours(HORAS_ANTES)))
                .thenReturn(List.of(cita));

        scheduler.procesarRecordatorios();

        assertTrue(cita.getRecordatorioEnviado());
        verify(citaRepository).save(cita);
        verify(eventPublisher).publishEvent(any(CitaRecordatorioEvent.class));
    }

    @Test
    void procesarRecordatorios_citaFueraDeVentana_noProcesaNada() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime fechaCita = ahora.plusHours(HORAS_ANTES + 1); // fuera de ventana

        when(citaRepository.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                EstadoCita.CONFIRMADA, ahora, ahora.plusHours(HORAS_ANTES)))
                .thenReturn(List.of());

        scheduler.procesarRecordatorios();

        verify(citaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void procesarRecordatorios_citaYaRecordada_noProcesa() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime fechaCita = ahora.plusHours(12);
        Cita cita = crearCita(1, fechaCita, EstadoCita.CONFIRMADA, true);

        when(citaRepository.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                EstadoCita.CONFIRMADA, ahora, ahora.plusHours(HORAS_ANTES)))
                .thenReturn(List.of()); // no la devuelve porque recordatorio_enviado = true

        scheduler.procesarRecordatorios();

        verify(citaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void procesarRecordatorios_citaNoConfirmada_noProcesa() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime fechaCita = ahora.plusHours(12);
        Cita cita = crearCita(1, fechaCita, EstadoCita.PENDIENTE, false);

        when(citaRepository.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                EstadoCita.CONFIRMADA, ahora, ahora.plusHours(HORAS_ANTES)))
                .thenReturn(List.of()); // no la devuelve porque estado != CONFIRMADA

        scheduler.procesarRecordatorios();

        verify(citaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void procesarRecordatorios_multiplesCitas_procesaTodas() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Cita c1 = crearCita(1, ahora.plusHours(5), EstadoCita.CONFIRMADA, false);
        Cita c2 = crearCita(2, ahora.plusHours(10), EstadoCita.CONFIRMADA, false);
        Cita c3 = crearCita(3, ahora.plusHours(20), EstadoCita.CONFIRMADA, false);

        when(citaRepository.findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
                EstadoCita.CONFIRMADA, ahora, ahora.plusHours(HORAS_ANTES)))
                .thenReturn(List.of(c1, c2, c3));

        scheduler.procesarRecordatorios();

        assertTrue(c1.getRecordatorioEnviado());
        assertTrue(c2.getRecordatorioEnviado());
        assertTrue(c3.getRecordatorioEnviado());
        verify(citaRepository, times(3)).save(any());
        verify(eventPublisher, times(3)).publishEvent(any(CitaRecordatorioEvent.class));
    }
}
