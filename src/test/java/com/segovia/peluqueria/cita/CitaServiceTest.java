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
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitaServiceTest {

    private static final String EMAIL_ADMIN = "admin@test.com";

    private CitaRepository citaRepository;
    private UsuarioRepository usuarioRepository;
    private ServicioRepository servicioRepository;
    private PeluqueroRepository peluqueroRepository;
    private ApplicationEventPublisher eventPublisher;
    private CitaService citaService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        citaRepository = mock(CitaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        servicioRepository = mock(ServicioRepository.class);
        peluqueroRepository = mock(PeluqueroRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        // HorarioProperties con sus valores por defecto: 09:00 - 20:00.
        citaService = new CitaService(citaRepository, usuarioRepository, servicioRepository, peluqueroRepository, new HorarioProperties(), eventPublisher);

        // Por defecto, el usuario autenticado es un ADMIN (acceso total).
        Usuario admin = new Usuario();
        admin.setIdUsuario(99);
        admin.setEmail(EMAIL_ADMIN);
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(admin));
    }

    private Usuario crearUsuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail("carlos@test.com");
        usuario.setRol(Rol.USER);
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

        CitaResponseDTO resultado = citaService.agendarCita(request, EMAIL_ADMIN);

        assertNotNull(resultado.getIdCita());
        assertEquals(EstadoCita.PENDIENTE, resultado.getEstado());
        assertEquals(usuario.getIdUsuario(), resultado.getUsuario().getIdUsuario());
        assertEquals(servicio.getIdServicio(), resultado.getServicio().getIdServicio());
        verify(citaRepository).save(any(Cita.class));

        // Debe publicar el evento de cita agendada con los datos para la notificacion.
        ArgumentCaptor<CitaAgendadaEvent> captor = ArgumentCaptor.forClass(CitaAgendadaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(usuario.getEmail(), captor.getValue().clienteEmail());
        assertEquals(servicio.getNombre(), captor.getValue().servicioNombre());
    }

    @Test
    void agendarCita_usuarioNoExiste_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        when(usuarioRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
    }

    @Test
    void agendarCita_usuarioInactivo_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();
        usuario.setActivo(false);

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("usuario inactivo"));
    }

    @Test
    void agendarCita_servicioNoExiste_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        Usuario usuario = crearUsuarioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
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
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("servicio inactivo"));
    }

    @Test
    void agendarCita_enElPasado_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(LocalDateTime.of(2020, 1, 6, 10, 0));

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("pasado"));
    }

    @Test
    void agendarCita_domingo_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0));

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("domingos"));
    }

    @Test
    void agendarCita_antesDeApertura_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(proximoLunesALas(7, 0));

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("antes de las"));
    }

    @Test
    void agendarCita_terminaDespuesDeCierre_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(proximoLunesALas(19, 45));

        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
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
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
    }

    @Test
    void agendarCita_comoUser_ignoraUsuarioIdAjeno() {
        // Carlos (USER, id=1) intenta agendar enviando usuarioId=2 (ajeno): debe forzarse a su propio id.
        Usuario carlos = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();

        CitaRequestDTO request = crearRequestValido();
        request.setUsuarioId(2);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(carlos));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictos(any(), any())).thenReturn(0);
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> {
            Cita c = i.getArgument(0);
            c.setIdCita(1);
            return c;
        });

        CitaResponseDTO resultado = citaService.agendarCita(request, "carlos@test.com");

        assertEquals(1, resultado.getUsuario().getIdUsuario());
        verify(usuarioRepository, never()).findById(2);
    }

    @Test
    void obtenerCitaPorId_exitoso() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.PENDIENTE);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        CitaResponseDTO resultado = citaService.obtenerCitaPorId(1, EMAIL_ADMIN);

        assertEquals(1, resultado.getIdCita());
    }

    @Test
    void obtenerCitaPorId_noExiste_lanzaExcepcion() {
        when(citaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.obtenerCitaPorId(99, EMAIL_ADMIN));
    }

    @Test
    void obtenerCitaPorId_propia_noAdmin_exitoso() {
        Usuario carlos = crearUsuarioActivo();
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setUsuario(carlos);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        CitaResponseDTO resultado = citaService.obtenerCitaPorId(1, "carlos@test.com");

        assertEquals(1, resultado.getIdCita());
    }

    @Test
    void obtenerCitaPorId_ajena_noAdmin_lanzaAccessDenied() {
        Usuario carlos = crearUsuarioActivo();
        Usuario otro = new Usuario();
        otro.setIdUsuario(2);
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setUsuario(otro);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        assertThrows(AccessDeniedException.class,
                () -> citaService.obtenerCitaPorId(1, "carlos@test.com"));
    }

    @Test
    void listarCitas_comoAdmin_devuelveTodas() {
        Cita c1 = new Cita();
        c1.setIdCita(1);
        Cita c2 = new Cita();
        c2.setIdCita(2);

        when(citaRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(c1, c2)));

        Page<CitaResponseDTO> resultado = citaService.listarCitas(EMAIL_ADMIN, pageable);

        assertEquals(2, resultado.getTotalElements());
        verify(citaRepository).findAll(pageable);
        verify(citaRepository, never()).findByUsuarioIdUsuario(any(), any());
    }

    @Test
    void listarCitas_comoUser_soloLasSuyas() {
        Usuario carlos = crearUsuarioActivo();
        Cita c1 = new Cita();
        c1.setIdCita(1);
        c1.setUsuario(carlos);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        when(citaRepository.findByUsuarioIdUsuario(1, pageable)).thenReturn(new PageImpl<>(List.of(c1)));

        Page<CitaResponseDTO> resultado = citaService.listarCitas("carlos@test.com", pageable);

        assertEquals(1, resultado.getTotalElements());
        verify(citaRepository).findByUsuarioIdUsuario(1, pageable);
        verify(citaRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void disponibilidad_diaVacio_devuelveTodosLosSlots() {
        Servicio servicio = crearServicioActivo(); // 30 min
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictos(any(), any())).thenReturn(0);

        List<String> slots = citaService.obtenerDisponibilidad(fecha, 1, null);

        // 09:00 .. 19:30 en pasos de 30 min con servicio de 30 min => 22 slots.
        assertEquals(22, slots.size());
        assertEquals("09:00", slots.get(0));
        assertEquals("19:30", slots.get(slots.size() - 1));
        assertFalse(slots.contains("19:45"));
    }

    @Test
    void disponibilidad_servicio90MinAlFiloDelCierre() {
        Servicio servicio = crearServicioActivo();
        servicio.setDuracion(90);
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictos(any(), any())).thenReturn(0);

        List<String> slots = citaService.obtenerDisponibilidad(fecha, 1, null);

        // Ultimo inicio valido para 90 min: 18:30 (termina 20:00).
        assertEquals("18:30", slots.get(slots.size() - 1));
        assertFalse(slots.contains("18:45"));
        assertFalse(slots.contains("19:00"));
    }

    @Test
    void disponibilidad_conCitaQueOcupaUnSlot() {
        Servicio servicio = crearServicioActivo(); // 30 min
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        // Conflicto solo en el slot de las 10:00.
        when(citaRepository.contarConflictos(any(), any())).thenAnswer(inv -> {
            LocalDateTime inicio = inv.getArgument(0);
            return (inicio.getHour() == 10 && inicio.getMinute() == 0) ? 1 : 0;
        });

        List<String> slots = citaService.obtenerDisponibilidad(fecha, 1, null);

        assertTrue(slots.contains("09:30"));
        assertFalse(slots.contains("10:00"));
        assertTrue(slots.contains("10:30"));
    }

    @Test
    void disponibilidad_domingo_devuelveVacio() {
        Servicio servicio = crearServicioActivo();
        LocalDate domingo = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        List<String> slots = citaService.obtenerDisponibilidad(domingo, 1, null);

        assertTrue(slots.isEmpty());
    }

    @Test
    void disponibilidad_fechaPasada_lanzaExcepcion() {
        LocalDate ayer = LocalDate.now().minusDays(1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.obtenerDisponibilidad(ayer, 1, null));
        assertTrue(ex.getMessage().contains("pasada"));
    }

    @Test
    void disponibilidad_servicioInactivo_lanzaExcepcion() {
        Servicio servicio = crearServicioActivo();
        servicio.setActivo(false);
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        assertThrows(IllegalArgumentException.class,
                () -> citaService.obtenerDisponibilidad(fecha, 1, null));
    }

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

        CitaResponseDTO resultado = citaService.actualizarCita(1, request, EMAIL_ADMIN);

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

        CitaResponseDTO resultado = citaService.actualizarCita(1, request, EMAIL_ADMIN);

        assertEquals(proximoLunesALas(14, 0), resultado.getFechaHora());
        verify(citaRepository).contarConflictosExcluyendo(any(), any(), eq(1));
        verify(eventPublisher).publishEvent(any(CitaModificadaEvent.class));
    }

    @Test
    void actualizarCita_anula_publicaEventoAnulada() {
        Cita citaExistente = new Cita();
        citaExistente.setIdCita(1);
        citaExistente.setEstado(EstadoCita.PENDIENTE);
        citaExistente.setFechaHora(proximoLunesALas(10, 0));
        citaExistente.setServicio(crearServicioActivo());
        citaExistente.setUsuario(crearUsuarioActivo());

        when(citaRepository.findById(1)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setEstado(EstadoCita.ANULADA);

        citaService.actualizarCita(1, request, EMAIL_ADMIN);

        verify(eventPublisher).publishEvent(any(CitaAnuladaEvent.class));
        verify(eventPublisher, never()).publishEvent(any(CitaModificadaEvent.class));
    }

    @Test
    void actualizarCita_noExiste_lanzaExcepcion() {
        when(citaRepository.findById(99)).thenReturn(Optional.empty());

        CitaUpdateDTO request = new CitaUpdateDTO();

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.actualizarCita(99, request, EMAIL_ADMIN));
    }

    @Test
    void eliminarCita_exitoso() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        // Una cita real siempre tiene usuario y servicio (FKs NOT NULL); necesarios para el evento de anulacion.
        cita.setUsuario(crearUsuarioActivo());
        cita.setServicio(crearServicioActivo());
        cita.setFechaHora(LocalDateTime.now().plusDays(1));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        citaService.eliminarCita(1, EMAIL_ADMIN);

        verify(citaRepository).delete(cita);
        verify(eventPublisher).publishEvent(any(CitaAnuladaEvent.class));
    }

    @Test
    void eliminarCita_noExiste_lanzaExcepcion() {
        when(citaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.eliminarCita(99, EMAIL_ADMIN));
    }

    @Test
    void eliminarCita_ajena_noAdmin_lanzaAccessDenied() {
        Usuario carlos = crearUsuarioActivo();
        Usuario otro = new Usuario();
        otro.setIdUsuario(2);
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setUsuario(otro);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        assertThrows(AccessDeniedException.class,
                () -> citaService.eliminarCita(1, "carlos@test.com"));
        verify(citaRepository, never()).delete(any());
    }

    // --- Tests con peluquero ---

    private Peluquero crearPeluqueroActivo() {
        Peluquero p = new Peluquero();
        p.setIdPeluquero(1);
        p.setNombre("Lalo");
        p.setActivo(true);
        return p;
    }

    @Test
    void agendarCita_conPeluquero_exitoso() {
        CitaRequestDTO request = crearRequestValido();
        request.setPeluqueroId(1);
        Usuario usuario = crearUsuarioActivo();
        Servicio servicio = crearServicioActivo();
        Peluquero peluquero = crearPeluqueroActivo();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(peluquero));
        when(citaRepository.contarConflictosConPeluquero(any(), any(), eq(1))).thenReturn(0);
        when(citaRepository.save(any(Cita.class))).thenAnswer(invocation -> {
            Cita c = invocation.getArgument(0);
            c.setIdCita(1);
            return c;
        });

        CitaResponseDTO resultado = citaService.agendarCita(request, EMAIL_ADMIN);

        assertNotNull(resultado.getPeluquero());
        assertEquals(1, resultado.getPeluquero().getIdPeluquero());
        assertEquals("Lalo", resultado.getPeluquero().getNombre());
        verify(peluqueroRepository).findById(1);
    }

    @Test
    void agendarCita_peluqueroNoExiste_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setPeluqueroId(99);
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(crearUsuarioActivo()));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioActivo()));
        when(peluqueroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
    }

    @Test
    void agendarCita_peluqueroInactivo_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setPeluqueroId(1);
        Peluquero peluquero = crearPeluqueroActivo();
        peluquero.setActivo(false);

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(crearUsuarioActivo()));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioActivo()));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(peluquero));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("peluquero inactivo"));
    }

    @Test
    void disponibilidad_conPeluquero_consultaFiltrada() {
        Servicio servicio = crearServicioActivo();
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictosConPeluquero(any(), any(), eq(1))).thenReturn(0);

        List<String> slots = citaService.obtenerDisponibilidad(fecha, 1, 1);

        assertEquals(22, slots.size());
        verify(citaRepository, never()).contarConflictos(any(), any());
        verify(citaRepository, atLeastOnce()).contarConflictosConPeluquero(any(), any(), eq(1));
    }

    @Test
    void disponibilidad_sinPeluquero_usaQueryGlobal() {
        Servicio servicio = crearServicioActivo();
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(citaRepository.contarConflictos(any(), any())).thenReturn(0);

        List<String> slots = citaService.obtenerDisponibilidad(fecha, 1, null);

        assertEquals(22, slots.size());
        verify(citaRepository, atLeastOnce()).contarConflictos(any(), any());
        verify(citaRepository, never()).contarConflictosConPeluquero(any(), any(), any());
    }

    @Test
    void actualizarCita_asignarPeluquero() {
        Cita citaExistente = new Cita();
        citaExistente.setIdCita(1);
        citaExistente.setEstado(EstadoCita.PENDIENTE);
        citaExistente.setFechaHora(proximoLunesALas(10, 0));
        citaExistente.setServicio(crearServicioActivo());
        citaExistente.setUsuario(crearUsuarioActivo());

        Peluquero peluquero = crearPeluqueroActivo();

        when(citaRepository.findById(1)).thenReturn(Optional.of(citaExistente));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(peluquero));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setPeluqueroId(1);

        CitaResponseDTO resultado = citaService.actualizarCita(1, request, EMAIL_ADMIN);

        assertNotNull(resultado.getPeluquero());
        assertEquals(1, resultado.getPeluquero().getIdPeluquero());
    }
}
