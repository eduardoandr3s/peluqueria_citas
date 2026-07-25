package com.segovia.peluqueria.calendario;

import com.segovia.peluqueria.calendario.dto.DiaBloqueadoRequestDTO;
import com.segovia.peluqueria.calendario.dto.DiaBloqueadoResponseDTO;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.cita.HorarioProperties;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CalendarioServiceTest {

    /** Viernes 2026-08-07 a las 12:00, para que "hoy" sea estable en los tests. */
    private static final LocalDate HOY = LocalDate.of(2026, 8, 7);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private DiaBloqueadoRepository diaBloqueadoRepository;
    private CitaRepository citaRepository;
    private HorarioProperties horario;
    private CalendarioService calendarioService;

    @BeforeEach
    void setUp() {
        diaBloqueadoRepository = mock(DiaBloqueadoRepository.class);
        citaRepository = mock(CitaRepository.class);
        horario = new HorarioProperties();

        when(diaBloqueadoRepository.existsByFecha(any())).thenReturn(false);
        when(diaBloqueadoRepository.findByFecha(any())).thenReturn(Optional.empty());
        when(diaBloqueadoRepository.findByFechaBetweenOrderByFecha(any(), any())).thenReturn(List.of());
        when(citaRepository.contarActivasEnElDia(any(), any(), any())).thenReturn(0L);
        when(diaBloqueadoRepository.save(any(DiaBloqueado.class))).thenAnswer(inv -> {
            DiaBloqueado d = inv.getArgument(0);
            d.setIdDiaBloqueado(1);
            return d;
        });

        Clock clock = Clock.fixed(HOY.atTime(12, 0).atZone(ZONA).toInstant(), ZONA);
        calendarioService = new CalendarioService(diaBloqueadoRepository, citaRepository, horario, clock);
    }

    private DiaBloqueado bloqueo(LocalDate fecha, String motivo) {
        DiaBloqueado dia = new DiaBloqueado();
        dia.setIdDiaBloqueado(1);
        dia.setFecha(fecha);
        dia.setMotivo(motivo);
        return dia;
    }

    @Test
    void esCerrado_domingo() {
        // 2026-08-09 es domingo.
        assertTrue(calendarioService.esCerrado(LocalDate.of(2026, 8, 9)));
    }

    @Test
    void esCerrado_diaLaborableSinBloqueo() {
        assertFalse(calendarioService.esCerrado(LocalDate.of(2026, 8, 10)));
    }

    @Test
    void esCerrado_diaBloqueado() {
        LocalDate lunes = LocalDate.of(2026, 8, 10);
        when(diaBloqueadoRepository.existsByFecha(lunes)).thenReturn(true);

        assertTrue(calendarioService.esCerrado(lunes));
    }

    @Test
    void esCerrado_respetaDiasCerradosConfigurados() {
        // Un negocio que ademas cierra los lunes.
        horario.setDiasCerrados(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY));

        assertTrue(calendarioService.esCerrado(LocalDate.of(2026, 8, 10)));
    }

    @Test
    void motivoCierre_diaAbierto_devuelveNull() {
        assertNull(calendarioService.motivoCierre(LocalDate.of(2026, 8, 10)));
    }

    @Test
    void motivoCierre_bloqueoSinMotivo_devuelveTextoPorDefecto() {
        LocalDate lunes = LocalDate.of(2026, 8, 10);
        when(diaBloqueadoRepository.findByFecha(lunes)).thenReturn(Optional.of(bloqueo(lunes, null)));

        assertEquals("Cerrado", calendarioService.motivoCierre(lunes));
    }

    @Test
    void diasCerrados_rangoConDomingoYFestivo() {
        LocalDate lunes = LocalDate.of(2026, 8, 10);
        LocalDate miercoles = LocalDate.of(2026, 8, 12);
        LocalDate domingo = LocalDate.of(2026, 8, 16);
        when(diaBloqueadoRepository.findByFechaBetweenOrderByFecha(lunes, domingo))
                .thenReturn(List.of(bloqueo(miercoles, "Vacaciones")));

        var cerrados = calendarioService.diasCerrados(lunes, domingo);

        assertEquals(2, cerrados.size());
        assertEquals(miercoles, cerrados.get(0).getFecha());
        assertEquals("Vacaciones", cerrados.get(0).getMotivo());
        assertEquals(domingo, cerrados.get(1).getFecha());
    }

    @Test
    void diasCerrados_rangoInvertido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> calendarioService.diasCerrados(HOY.plusDays(3), HOY));
    }

    @Test
    void bloquear_exitoso() {
        DiaBloqueadoRequestDTO request = new DiaBloqueadoRequestDTO();
        request.setFecha(HOY.plusDays(10));
        request.setMotivo("  Reyes  ");

        DiaBloqueadoResponseDTO respuesta = calendarioService.bloquear(request);

        assertEquals(HOY.plusDays(10), respuesta.getFecha());
        assertEquals("Reyes", respuesta.getMotivo());
        verify(diaBloqueadoRepository).save(any(DiaBloqueado.class));
    }

    @Test
    void bloquear_motivoVacio_guardaNull() {
        DiaBloqueadoRequestDTO request = new DiaBloqueadoRequestDTO();
        request.setFecha(HOY.plusDays(10));
        request.setMotivo("   ");

        assertNull(calendarioService.bloquear(request).getMotivo());
    }

    @Test
    void bloquear_hoy_esValido() {
        DiaBloqueadoRequestDTO request = new DiaBloqueadoRequestDTO();
        request.setFecha(HOY);

        assertEquals(HOY, calendarioService.bloquear(request).getFecha());
    }

    @Test
    void bloquear_fechaPasada_lanzaExcepcion() {
        DiaBloqueadoRequestDTO request = new DiaBloqueadoRequestDTO();
        request.setFecha(HOY.minusDays(1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> calendarioService.bloquear(request));
        assertTrue(ex.getMessage().contains("pasado"));
        verify(diaBloqueadoRepository, never()).save(any());
    }

    @Test
    void bloquear_diaYaBloqueado_lanzaConflicto() {
        LocalDate fecha = HOY.plusDays(5);
        when(diaBloqueadoRepository.existsByFecha(fecha)).thenReturn(true);
        DiaBloqueadoRequestDTO request = new DiaBloqueadoRequestDTO();
        request.setFecha(fecha);

        assertThrows(ConflictoHorarioException.class, () -> calendarioService.bloquear(request));
        verify(diaBloqueadoRepository, never()).save(any());
    }

    @Test
    void bloquear_conCitasActivas_lanzaConflicto() {
        LocalDate fecha = HOY.plusDays(5);
        when(citaRepository.contarActivasEnElDia(EstadoCita.ANULADA,
                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())).thenReturn(3L);
        DiaBloqueadoRequestDTO request = new DiaBloqueadoRequestDTO();
        request.setFecha(fecha);

        ConflictoHorarioException ex = assertThrows(ConflictoHorarioException.class,
                () -> calendarioService.bloquear(request));
        assertTrue(ex.getMessage().contains("3"));
        verify(diaBloqueadoRepository, never()).save(any());
    }

    @Test
    void listarProximos_pideDesdeHoy() {
        LocalDate futuro = HOY.plusDays(20);
        when(diaBloqueadoRepository.findByFechaGreaterThanEqualOrderByFecha(HOY))
                .thenReturn(List.of(bloqueo(futuro, "Vacaciones")));

        List<DiaBloqueadoResponseDTO> lista = calendarioService.listarProximos();

        assertEquals(1, lista.size());
        assertEquals(futuro, lista.get(0).getFecha());
        verify(diaBloqueadoRepository).findByFechaGreaterThanEqualOrderByFecha(HOY);
    }

    @Test
    void desbloquear_exitoso() {
        DiaBloqueado dia = bloqueo(HOY.plusDays(3), "Reyes");
        when(diaBloqueadoRepository.findById(1)).thenReturn(Optional.of(dia));

        calendarioService.desbloquear(1);

        verify(diaBloqueadoRepository).delete(dia);
    }

    @Test
    void desbloquear_noExiste_lanzaNotFound() {
        when(diaBloqueadoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> calendarioService.desbloquear(99));
        verify(diaBloqueadoRepository, never()).delete(any());
    }
}
