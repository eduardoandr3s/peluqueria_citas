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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitaServiceTest {

    private CitaRepository citaRepository;
    private UsuarioRepository usuarioRepository;
    private ServicioRepository servicioRepository;
    private CitaService citaService;

    @BeforeEach
    void setUp() {
        citaRepository = mock(CitaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        servicioRepository = mock(ServicioRepository.class);
        citaService = new CitaService(citaRepository, usuarioRepository, servicioRepository);
    }

    private Usuario crearUsuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setActivo(true);
        return usuario;
    }

    private Servicio crearServicioActivo() {
        Servicio servicio = new Servicio();
        servicio.setIdServicio(1);
        servicio.setNombre("Corte");
        servicio.setDuracion(30);
        servicio.setPrecio(new BigDecimal("15.00"));
        servicio.setActivo(true);
        return servicio;
    }

    // Devuelve el proximo lunes a las 10:00 (siempre en el futuro, dia laboral)
    private LocalDateTime proximoLunesALas(int hora, int minuto) {
        return LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(hora).withMinute(minuto).withSecond(0).withNano(0);
    }

    private CitaRequestDTO crearRequestValido() {
        CitaRequestDTO request = new CitaRequestDTO();
        request.setUsuarioId(1);
        request.setServicioId(1);
        request.setFechaHora(proximoLunesALas(10, 0));
        return request;
    }

    // --- agendarCita ---

    @Test
    void agendarCita_exitoso() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictos(any(), any())).thenReturn(0);
        when(citaRepository.save(any(Cita.class))).thenAnswer(invocation -> {
            Cita c = invocation.getArgument(0);
            c.setIdCita(1);
            return c;
        });

        Cita resultado = citaService.agendarCita(request);

        assertNotNull(resultado.getIdCita());
        assertEquals(EstadoCita.PENDIENTE, resultado.getEstado());
        assertEquals(usuario, resultado.getUsuario());
        assertEquals(servicio, resultado.getServicio());
        verify(citaRepository).save(any(Cita.class));
    }

    @Test
    void agendarCita_usuarioNoExiste_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        when(usuarioRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.agendarCita(request));
    }

    @Test
    void agendarCita_usuarioInactivo_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();
        usuario.setActivo(false);

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request));
        assertTrue(ex.getMessage().contains("usuario inactivo"));
    }

    @Test
    void agendarCita_servicioNoExiste_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.agendarCita(request));
    }

    @Test
    void agendarCita_servicioInactivo_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();
        servicio.setActivo(false);

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request));
        assertTrue(ex.getMessage().contains("servicio inactivo"));
    }

    @Test
    void agendarCita_enElPasado_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(LocalDateTime.of(2020, 1, 6, 10, 0)); // lunes en el pasado

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request));
        assertTrue(ex.getMessage().contains("pasado"));
    }

    @Test
    void agendarCita_domingo_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        // Proximo domingo a las 10:00
        request.setFechaHora(LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0));

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request));
        assertTrue(ex.getMessage().contains("domingos"));
    }

    @Test
    void agendarCita_antesDeApertura_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(proximoLunesALas(7, 0)); // 7:00 AM

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request));
        assertTrue(ex.getMessage().contains("antes de las"));
    }

    @Test
    void agendarCita_terminaDespuesDeCierre_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(proximoLunesALas(19, 45)); // 19:45 + 30min = 20:15 > 20:00

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request));
        assertTrue(ex.getMessage().contains("despues de las"));
    }

    @Test
    void agendarCita_conflictoHorario_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictos(any(), any())).thenReturn(1);

        assertThrows(ConflictoHorarioException.class,
                () -> citaService.agendarCita(request));
    }

    // --- obtenerCitaPorId ---

    @Test
    void obtenerCitaPorId_exitoso() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.PENDIENTE);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        Cita resultado = citaService.obtenerCitaPorId(1);

        assertEquals(1, resultado.getIdCita());
    }

    @Test
    void obtenerCitaPorId_noExiste_lanzaExcepcion() {
        when(citaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.obtenerCitaPorId(99));
    }

    // --- listarCitas ---

    @Test
    void listarCitas_devuelveLista() {
        Cita c1 = new Cita();
        c1.setIdCita(1);
        Cita c2 = new Cita();
        c2.setIdCita(2);

        when(citaRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Cita> resultado = citaService.listarCitas();

        assertEquals(2, resultado.size());
    }

    // --- actualizarCita ---

    @Test
    void actualizarCita_cambiaEstado() {
        Cita citaExistente = new Cita();
        citaExistente.setIdCita(1);
        citaExistente.setEstado(EstadoCita.PENDIENTE);
        citaExistente.setFechaHora(proximoLunesALas(10, 0));
        citaExistente.setServicio(crearServicioActivo());

        when(citaRepository.findById(1)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setEstado(EstadoCita.CONFIRMADA);

        Cita resultado = citaService.actualizarCita(1, request);

        assertEquals(EstadoCita.CONFIRMADA, resultado.getEstado());
    }

    @Test
    void actualizarCita_cambiaFecha_revalida() {
        Cita citaExistente = new Cita();
        citaExistente.setIdCita(1);
        citaExistente.setEstado(EstadoCita.PENDIENTE);
        citaExistente.setFechaHora(proximoLunesALas(10, 0));
        citaExistente.setServicio(crearServicioActivo());
        citaExistente.setUsuario(crearUsuarioActivo());

        when(citaRepository.findById(1)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.contarConflictosExcluyendo(any(), any(), eq(1))).thenReturn(0);
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setFechaHora(proximoLunesALas(14, 0));

        Cita resultado = citaService.actualizarCita(1, request);

        assertEquals(proximoLunesALas(14, 0), resultado.getFechaHora());
        // Verifica que se validaron conflictos al cambiar fecha
        verify(citaRepository).contarConflictosExcluyendo(any(), any(), eq(1));
    }

    @Test
    void actualizarCita_noExiste_lanzaExcepcion() {
        when(citaRepository.findById(99)).thenReturn(Optional.empty());

        CitaUpdateDTO request = new CitaUpdateDTO();

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.actualizarCita(99, request));
    }

    // --- eliminarCita ---

    @Test
    void eliminarCita_exitoso() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        citaService.eliminarCita(1);

        verify(citaRepository).delete(cita);
    }

    @Test
    void eliminarCita_noExiste_lanzaExcepcion() {
        when(citaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.eliminarCita(99));
    }
}
