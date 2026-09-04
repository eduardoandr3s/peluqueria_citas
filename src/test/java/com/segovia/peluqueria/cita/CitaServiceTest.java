package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.calendario.CalendarioService;
import com.segovia.peluqueria.calendario.DiaBloqueado;
import com.segovia.peluqueria.calendario.DiaBloqueadoRepository;
import com.segovia.peluqueria.calendario.dto.DiaCerradoDTO;
import com.segovia.peluqueria.cita.dto.CitaCierreDTO;
import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaAnuladaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaModificadaEvent;
import com.segovia.peluqueria.pago.EstadoPago;
import com.segovia.peluqueria.pago.Pago;
import com.segovia.peluqueria.pago.PagoRepository;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.peluquero.PeluqueroRepository;
import com.segovia.peluqueria.peluquero.PeluqueroService;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.permiso.Permiso;
import com.segovia.peluqueria.permiso.PermisoService;
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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private PeluqueroService peluqueroService;
    private PagoRepository pagoRepository;
    private DiaBloqueadoRepository diaBloqueadoRepository;
    private ApplicationEventPublisher eventPublisher;
    private PermisoService permisoService;
    private CitaService citaService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        citaRepository = mock(CitaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        servicioRepository = mock(ServicioRepository.class);
        peluqueroRepository = mock(PeluqueroRepository.class);
        peluqueroService = mock(PeluqueroService.class);
        pagoRepository = mock(PagoRepository.class);
        // Por defecto ninguna cuenta tiene ficha de peluquero: los tests que la necesitan
        // la stubbean. Sin esto, verificarAcceso llamaria a un mock que devuelve null.
        when(peluqueroService.fichaDeUsuario(anyInt())).thenReturn(Optional.empty());
        diaBloqueadoRepository = mock(DiaBloqueadoRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        // Por defecto no hay pagos: las citas se mapean con estadoPago null.
        when(pagoRepository.findByCitaIdCitaIn(any())).thenReturn(List.of());
        when(pagoRepository.findByCitaIdCita(anyInt())).thenReturn(Optional.empty());
        // Por defecto no hay ningun dia bloqueado: solo cierra el domingo (regla del horario).
        when(diaBloqueadoRepository.existsByFecha(any())).thenReturn(false);
        when(diaBloqueadoRepository.findByFecha(any())).thenReturn(Optional.empty());
        when(diaBloqueadoRepository.findByFechaBetweenOrderByFecha(any(), any())).thenReturn(List.of());
        // HorarioProperties con sus valores por defecto: 09:00 - 20:00, domingo cerrado.
        // Clock del sistema para que "ahora" coincida con los LocalDateTime.now() de los helpers.
        HorarioProperties horario = new HorarioProperties();
        Clock clock = Clock.systemDefaultZone();
        // CalendarioService real (no mock) sobre el repo mockeado: la regla del dia de la
        // semana se calcula de verdad y solo hay que stubbear los bloqueos puntuales.
        CalendarioService calendario = new CalendarioService(diaBloqueadoRepository, citaRepository, horario, clock);
        permisoService = mock(PermisoService.class);
        // Por defecto los permisos configurables estan concedidos: aqui se prueban las
        // reglas de la cita, no la matriz. Los tests del permiso lo stubbean al reves.
        when(permisoService.tienePermiso(any(), any())).thenReturn(true);
        citaService = new CitaService(citaRepository, usuarioRepository, servicioRepository, peluqueroRepository, peluqueroService, pagoRepository, horario, calendario, eventPublisher, clock, permisoService);

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
    void agendarCita_horaYaPasadaEnZonaDelNegocio_lanzaExcepcion() {
        // Lunes 2026-07-20, 15:37 en Europe/Madrid (que son las 13:37 UTC en verano, CEST).
        // Reservar a las 14:00 de ese dia debe rechazarse porque ya paso en la hora del negocio,
        // aunque 14:00 sea "posterior" a las 13:37 UTC. Guarda contra el bug de zona horaria
        // (host en UTC dejaba agendar en el pasado local).
        Clock relojMadrid = Clock.fixed(Instant.parse("2026-07-20T13:37:00Z"), ZoneId.of("Europe/Madrid"));
        HorarioProperties horario = new HorarioProperties();
        CalendarioService calendario = new CalendarioService(diaBloqueadoRepository, citaRepository, horario, relojMadrid);
        citaService = new CitaService(citaRepository, usuarioRepository, servicioRepository,
                peluqueroRepository, peluqueroService, pagoRepository, horario, calendario, eventPublisher, relojMadrid, permisoService);

        CitaRequestDTO request = crearRequestValido();
        request.setFechaHora(LocalDateTime.of(2026, 7, 20, 14, 0));

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(crearUsuarioActivo()));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioActivo()));

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
        assertTrue(ex.getMessage().contains("domingo"));
    }

    @Test
    void agendarCita_diaBloqueado_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        LocalDate lunes = request.getFechaHora().toLocalDate();

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(crearUsuarioActivo()));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioActivo()));
        bloquear(lunes, "Reyes");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("Reyes"));
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void actualizarCita_reprogramarADiaBloqueado_lanzaExcepcion() {
        Cita citaExistente = new Cita();
        citaExistente.setIdCita(1);
        citaExistente.setEstado(EstadoCita.PENDIENTE);
        citaExistente.setFechaHora(proximoLunesALas(10, 0));
        citaExistente.setServicio(crearServicioActivo());
        citaExistente.setUsuario(crearUsuarioActivo());

        LocalDateTime nuevaFecha = proximoLunesALas(11, 0).plusDays(1);
        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setFechaHora(nuevaFecha);

        when(citaRepository.findById(1)).thenReturn(Optional.of(citaExistente));
        bloquear(nuevaFecha.toLocalDate(), "Puente");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarCita(1, request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("Puente"));
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void disponibilidad_diaBloqueado_devuelveVacio() {
        LocalDate lunes = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioActivo()));
        bloquear(lunes, "Festivo local");

        assertTrue(citaService.obtenerDisponibilidad(lunes, 1, null).isEmpty());
        // Si el dia esta cerrado no tiene sentido ir a buscar conflictos slot a slot.
        verify(citaRepository, never()).contarConflictos(any(), any());
    }

    @Test
    void diasCerrados_mezclaDomingosYBloqueos() {
        LocalDate lunes = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate domingo = lunes.plusDays(6);
        LocalDate martes = lunes.plusDays(1);

        DiaBloqueado bloqueo = new DiaBloqueado();
        bloqueo.setIdDiaBloqueado(1);
        bloqueo.setFecha(martes);
        bloqueo.setMotivo("Reyes");
        when(diaBloqueadoRepository.findByFechaBetweenOrderByFecha(lunes, domingo)).thenReturn(List.of(bloqueo));

        List<DiaCerradoDTO> cerrados = citaService.obtenerDiasCerrados(lunes, domingo);

        assertEquals(2, cerrados.size());
        assertEquals(martes, cerrados.get(0).getFecha());
        assertEquals("Reyes", cerrados.get(0).getMotivo());
        assertEquals(domingo, cerrados.get(1).getFecha());
        assertTrue(cerrados.get(1).getMotivo().contains("domingo"));
    }

    @Test
    void diasCerrados_rangoInvertido_lanzaExcepcion() {
        LocalDate hoy = LocalDate.now();

        assertThrows(IllegalArgumentException.class,
                () -> citaService.obtenerDiasCerrados(hoy.plusDays(5), hoy));
    }

    @Test
    void diasCerrados_rangoDemasiadoAmplio_lanzaExcepcion() {
        LocalDate hoy = LocalDate.now();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.obtenerDiasCerrados(hoy, hoy.plusYears(2)));
        assertTrue(ex.getMessage().contains("meses"));
    }

    /** Marca una fecha como bloqueada en el repositorio mockeado. */
    private void bloquear(LocalDate fecha, String motivo) {
        DiaBloqueado dia = new DiaBloqueado();
        dia.setIdDiaBloqueado(1);
        dia.setFecha(fecha);
        dia.setMotivo(motivo);
        when(diaBloqueadoRepository.existsByFecha(fecha)).thenReturn(true);
        when(diaBloqueadoRepository.findByFecha(fecha)).thenReturn(Optional.of(dia));
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
    void listarCitas_incluyeEstadoPagoEnUnaSolaConsulta() {
        Cita c1 = new Cita();
        c1.setIdCita(1);
        Cita c2 = new Cita();
        c2.setIdCita(2);

        // c1 tiene un pago PAGADO; c2 no tiene pago -> estadoPago null.
        Pago pagoC1 = new Pago();
        pagoC1.setIdPago(77);
        pagoC1.setCita(c1);
        pagoC1.setEstadoPago(EstadoPago.PAGADO);

        when(citaRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(c1, c2)));
        when(pagoRepository.findByCitaIdCitaIn(List.of(1, 2))).thenReturn(List.of(pagoC1));

        List<CitaResponseDTO> citas = citaService.listarCitas(EMAIL_ADMIN, pageable).getContent();

        assertEquals(EstadoPago.PAGADO, citas.get(0).getEstadoPago());
        assertNull(citas.get(1).getEstadoPago());
        // El id del pago viaja con la cita: es lo que permite pedir el recibo desde el
        // listado sin una peticion por cita.
        assertEquals(77, citas.get(0).getIdPago());
        assertNull(citas.get(1).getIdPago());
        // Una sola consulta batch, sin N+1 por cita.
        verify(pagoRepository).findByCitaIdCitaIn(List.of(1, 2));
        verify(pagoRepository, never()).findByCitaIdCita(any());
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
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(crearPeluqueroActivo()));
        when(citaRepository.contarConflictosConPeluquero(any(), any(), eq(1))).thenReturn(0);

        List<String> slots = citaService.obtenerDisponibilidad(fecha, 1, 1);

        assertEquals(22, slots.size());
        verify(citaRepository, never()).contarConflictos(any(), any());
        verify(citaRepository, atLeastOnce()).contarConflictosConPeluquero(any(), any(), eq(1));
    }

    @Test
    void disponibilidad_peluqueroNoExiste_lanzaExcepcion() {
        Servicio servicio = crearServicioActivo();
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(peluqueroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> citaService.obtenerDisponibilidad(fecha, 1, 99));
        verify(citaRepository, never()).contarConflictosConPeluquero(any(), any(), any());
    }

    @Test
    void disponibilidad_peluqueroInactivo_lanzaExcepcion() {
        Servicio servicio = crearServicioActivo();
        LocalDate fecha = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Peluquero inactivo = crearPeluqueroActivo();
        inactivo.setActivo(false);

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(inactivo));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.obtenerDisponibilidad(fecha, 1, 1));
        assertTrue(ex.getMessage().contains("peluquero inactivo"));
    }

    @Test
    void agendarCita_conPeluquero_conflicto_lanzaExcepcion() {
        CitaRequestDTO request = crearRequestValido();
        request.setPeluqueroId(1);

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(crearUsuarioActivo()));
        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioActivo()));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(crearPeluqueroActivo()));
        // El peluquero ya tiene una cita en ese horario → conflicto solo para ese peluquero.
        when(citaRepository.contarConflictosConPeluquero(any(), any(), eq(1))).thenReturn(1);

        assertThrows(ConflictoHorarioException.class,
                () -> citaService.agendarCita(request, EMAIL_ADMIN));
        verify(citaRepository, never()).contarConflictos(any(), any());
    }

    @Test
    void actualizarCita_peluqueroInactivo_lanzaExcepcion() {
        Cita citaExistente = new Cita();
        citaExistente.setIdCita(1);
        citaExistente.setEstado(EstadoCita.PENDIENTE);
        citaExistente.setFechaHora(proximoLunesALas(10, 0));
        citaExistente.setServicio(crearServicioActivo());
        citaExistente.setUsuario(crearUsuarioActivo());

        Peluquero inactivo = crearPeluqueroActivo();
        inactivo.setActivo(false);

        when(citaRepository.findById(1)).thenReturn(Optional.of(citaExistente));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(inactivo));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setPeluqueroId(1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarCita(1, request, EMAIL_ADMIN));
        assertTrue(ex.getMessage().contains("peluquero inactivo"));
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

    // ----------------------------------------------------------------------------------
    // Cierre de cita y agenda del rol PELUQUERO
    // ----------------------------------------------------------------------------------

    private static final String EMAIL_PELUQUERO = "lalo@test.com";

    /** Cuenta con rol PELUQUERO vinculada a la ficha que se le pase. */
    private Usuario autenticarPeluquero(Peluquero ficha) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(7);
        usuario.setNombre("Lalo");
        usuario.setEmail(EMAIL_PELUQUERO);
        usuario.setRol(Rol.PELUQUERO);
        usuario.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_PELUQUERO)).thenReturn(Optional.of(usuario));
        when(peluqueroService.fichaDeUsuario(7)).thenReturn(Optional.ofNullable(ficha));
        return usuario;
    }

    private Cita citaPasadaConPeluquero(Peluquero peluquero) {
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setFechaHora(LocalDateTime.now().minusDays(1).withHour(10).withMinute(0));
        cita.setServicio(crearServicioActivo());
        cita.setUsuario(crearUsuarioActivo());
        cita.setPeluquero(peluquero);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));
        return cita;
    }

    private CitaCierreDTO cierre(EstadoCita estado, String observaciones, Boolean contactado) {
        CitaCierreDTO dto = new CitaCierreDTO();
        dto.setEstado(estado);
        dto.setObservaciones(observaciones);
        dto.setClienteContactado(contactado);
        return dto;
    }

    // ---------- el permiso CITA_REPROGRAMAR ----------

    @Test
    void actualizarCita_peluqueroSinElPermiso_noPuedeCambiarLaFecha() {
        Peluquero ficha = crearPeluqueroActivo();
        autenticarPeluquero(ficha);
        citaPasadaConPeluquero(ficha);
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.CITA_REPROGRAMAR)).thenReturn(false);

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setFechaHora(proximoLunesALas(11, 0));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> citaService.actualizarCita(1, request, EMAIL_PELUQUERO));
        assertTrue(ex.getMessage().contains("no esta habilitado"));
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void actualizarCita_peluqueroConElPermiso_puedeCambiarLaFecha() {
        Peluquero ficha = crearPeluqueroActivo();
        autenticarPeluquero(ficha);
        Cita cita = citaPasadaConPeluquero(ficha);
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.CITA_REPROGRAMAR)).thenReturn(true);

        LocalDateTime nueva = proximoLunesALas(11, 0);
        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setFechaHora(nueva);

        citaService.actualizarCita(1, request, EMAIL_PELUQUERO);

        assertEquals(nueva, cita.getFechaHora());
    }

    @Test
    void actualizarCita_elPermisoNoAfectaAlClienteQueMueveLaSuya() {
        // El permiso estrecha lo del PELUQUERO y nada mas: un cliente sigue moviendo su
        // cita y un ADMIN sigue moviendo la que sea, con la matriz entera apagada.
        Peluquero ficha = crearPeluqueroActivo();
        Cita cita = citaPasadaConPeluquero(ficha);
        when(permisoService.tienePermiso(any(), any())).thenReturn(false);

        LocalDateTime nueva = proximoLunesALas(12, 0);
        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setFechaHora(nueva);

        citaService.actualizarCita(1, request, EMAIL_ADMIN);

        assertEquals(nueva, cita.getFechaHora());
    }

    @Test
    void cerrarCita_completada_congelaPrecioYComision() {
        Peluquero peluquero = crearPeluqueroActivo();
        Cita cita = citaPasadaConPeluquero(peluquero);
        when(peluqueroService.porcentajeAplicable(1, 1)).thenReturn(new BigDecimal("20.00"));

        CitaResponseDTO resultado = citaService.cerrarCita(1, cierre(EstadoCita.COMPLETADA, null, null), EMAIL_ADMIN);

        assertEquals(EstadoCita.COMPLETADA, resultado.getEstado());
        // El servicio vale 15.00 y la comision aplicable es del 20%: los dos quedan copiados
        // en la cita, que es lo que hace que la produccion no cambie si la tarifa sube.
        assertEquals(new BigDecimal("15.00"), cita.getPrecioAplicado());
        assertEquals(new BigDecimal("20.00"), cita.getComisionPorcentajeAplicado());
        assertNotNull(cita.getFechaCierre());
        assertEquals(99, cita.getCerradaPor().getIdUsuario());
    }

    @Test
    void cerrarCita_completada_sinPeluquero_comisionCero() {
        Cita cita = citaPasadaConPeluquero(null);

        citaService.cerrarCita(1, cierre(EstadoCita.COMPLETADA, null, null), EMAIL_ADMIN);

        // Hay venta (la FK de peluquero es nullable desde la V7) pero no a quien comisionar.
        assertEquals(new BigDecimal("15.00"), cita.getPrecioAplicado());
        assertEquals(BigDecimal.ZERO, cita.getComisionPorcentajeAplicado());
        verify(peluqueroService, never()).porcentajeAplicable(any(), any());
    }

    @Test
    void cerrarCita_completada_citaQueNoHaEmpezado_lanzaExcepcion() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setFechaHora(proximoLunesALas(10, 0));
        cita.setServicio(crearServicioActivo());
        cita.setUsuario(crearUsuarioActivo());
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.cerrarCita(1, cierre(EstadoCita.COMPLETADA, null, null), EMAIL_ADMIN));

        assertTrue(ex.getMessage().contains("todavia no ha empezado"));
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void cerrarCita_anulada_guardaObservacionesYAvisaAlCliente() {
        Cita cita = citaPasadaConPeluquero(crearPeluqueroActivo());

        CitaResponseDTO resultado = citaService.cerrarCita(
                1, cierre(EstadoCita.ANULADA, "  Llamo para cambiar de dia  ", true), EMAIL_ADMIN);

        assertEquals(EstadoCita.ANULADA, resultado.getEstado());
        assertEquals("Llamo para cambiar de dia", cita.getObservaciones());
        assertTrue(cita.getClienteContactado());
        // El email se manda aunque se haya contactado al cliente: si el aviso humano no
        // ocurrio, es lo unico que le queda.
        verify(eventPublisher).publishEvent(any(CitaAnuladaEvent.class));
    }

    @Test
    void cerrarCita_noAsistio_noCongelaImporte() {
        Cita cita = citaPasadaConPeluquero(crearPeluqueroActivo());

        citaService.cerrarCita(1, cierre(EstadoCita.NO_ASISTIO, "No aparecio", false), EMAIL_ADMIN);

        assertEquals(EstadoCita.NO_ASISTIO, cita.getEstado());
        assertNull(cita.getPrecioAplicado());
        assertNull(cita.getComisionPorcentajeAplicado());
        verify(eventPublisher, never()).publishEvent(any(CitaAnuladaEvent.class));
    }

    @Test
    void cerrarCita_cliente_soloPuedeAnular() {
        Usuario carlos = crearUsuarioActivo();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        citaPasadaConPeluquero(crearPeluqueroActivo());

        assertThrows(AccessDeniedException.class,
                () -> citaService.cerrarCita(1, cierre(EstadoCita.COMPLETADA, null, null), "carlos@test.com"));

        // Anular la suya si puede.
        CitaResponseDTO anulada = citaService.cerrarCita(
                1, cierre(EstadoCita.ANULADA, "No me viene bien", null), "carlos@test.com");
        assertEquals(EstadoCita.ANULADA, anulada.getEstado());
    }

    @Test
    void cerrarCita_yaCerrada_elPeluqueroNoLaPuedeReescribir() {
        Peluquero ficha = crearPeluqueroActivo();
        autenticarPeluquero(ficha);
        Cita cita = citaPasadaConPeluquero(ficha);
        cita.setEstado(EstadoCita.COMPLETADA);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> citaService.cerrarCita(1, cierre(EstadoCita.NO_ASISTIO, null, null), EMAIL_PELUQUERO));

        assertTrue(ex.getMessage().contains("administrador"));
    }

    @Test
    void cerrarCita_admin_corrigeCompletadaYLimpiaImportes() {
        Cita cita = citaPasadaConPeluquero(crearPeluqueroActivo());
        cita.setEstado(EstadoCita.COMPLETADA);
        cita.setPrecioAplicado(new BigDecimal("15.00"));
        cita.setComisionPorcentajeAplicado(new BigDecimal("20.00"));

        citaService.cerrarCita(1, cierre(EstadoCita.NO_ASISTIO, "Me equivoque de cita", null), EMAIL_ADMIN);

        // Un cierre corregido tiene que dejar de sumar en la produccion.
        assertNull(cita.getPrecioAplicado());
        assertNull(cita.getComisionPorcentajeAplicado());
    }

    @Test
    void cerrarCita_estadoQueNoEsDeCierre_lanzaExcepcion() {
        citaPasadaConPeluquero(crearPeluqueroActivo());

        assertThrows(IllegalArgumentException.class,
                () -> citaService.cerrarCita(1, cierre(EstadoCita.CONFIRMADA, null, null), EMAIL_ADMIN));
    }

    @Test
    void cerrarCita_peluqueroDeOtraAgenda_accesoDenegado() {
        Peluquero suya = crearPeluqueroActivo();
        autenticarPeluquero(suya);

        Peluquero deOtro = crearPeluqueroActivo();
        deOtro.setIdPeluquero(2);
        deOtro.setNombre("Pepe");
        citaPasadaConPeluquero(deOtro);

        assertThrows(AccessDeniedException.class,
                () -> citaService.cerrarCita(1, cierre(EstadoCita.COMPLETADA, null, null), EMAIL_PELUQUERO));
    }

    @Test
    void cerrarCita_peluqueroDeSuAgenda_completaYVeSuComision() {
        Peluquero ficha = crearPeluqueroActivo();
        autenticarPeluquero(ficha);
        citaPasadaConPeluquero(ficha);
        when(peluqueroService.porcentajeAplicable(1, 1)).thenReturn(new BigDecimal("15.00"));

        CitaResponseDTO resultado = citaService.cerrarCita(
                1, cierre(EstadoCita.COMPLETADA, "Corte y barba", null), EMAIL_PELUQUERO);

        assertEquals(EstadoCita.COMPLETADA, resultado.getEstado());
        assertEquals(new BigDecimal("15.00"), resultado.getComisionPorcentajeAplicado());
        assertEquals("Corte y barba", resultado.getObservaciones());
    }

    @Test
    void obtenerCitaPorId_cliente_noVeLosDatosDeGestion() {
        Usuario carlos = crearUsuarioActivo();
        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        Cita cita = citaPasadaConPeluquero(crearPeluqueroActivo());
        cita.setObservaciones("El cliente discutio el precio");
        cita.setComisionPorcentajeAplicado(new BigDecimal("20.00"));
        cita.setPrecioAplicado(new BigDecimal("15.00"));

        CitaResponseDTO resultado = citaService.obtenerCitaPorId(1, "carlos@test.com");

        // Notas internas y comision no viajan al cliente aunque la cita sea suya.
        assertNull(resultado.getObservaciones());
        assertNull(resultado.getComisionPorcentajeAplicado());
        assertNull(resultado.getPrecioAplicado());
    }

    @Test
    void actualizarCita_completadaPorPut_lanzaExcepcion() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setFechaHora(proximoLunesALas(10, 0));
        cita.setServicio(crearServicioActivo());
        cita.setUsuario(crearUsuarioActivo());
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setEstado(EstadoCita.COMPLETADA);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarCita(1, request, EMAIL_ADMIN));

        // Si el PUT dejara completar, quedarian citas completadas sin importe congelado.
        assertTrue(ex.getMessage().contains("cierre de cita"));
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void actualizarCita_anulaPorPut_sellaQuienCierra() {
        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setFechaHora(proximoLunesALas(10, 0));
        cita.setServicio(crearServicioActivo());
        cita.setUsuario(crearUsuarioActivo());
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaUpdateDTO request = new CitaUpdateDTO();
        request.setEstado(EstadoCita.ANULADA);

        citaService.actualizarCita(1, request, EMAIL_ADMIN);

        assertNotNull(cita.getFechaCierre());
        assertEquals(99, cita.getCerradaPor().getIdUsuario());
    }

    @Test
    void listarCitas_peluquero_devuelveSuAgendaYNoLasDeLaCasa() {
        Peluquero ficha = crearPeluqueroActivo();
        autenticarPeluquero(ficha);

        Cita suya = new Cita();
        suya.setIdCita(5);
        suya.setEstado(EstadoCita.CONFIRMADA);
        suya.setUsuario(crearUsuarioActivo());
        suya.setServicio(crearServicioActivo());
        suya.setPeluquero(ficha);
        when(citaRepository.findByPeluqueroIdPeluquero(eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(suya), pageable, 1));

        Page<CitaResponseDTO> resultado = citaService.listarCitas(EMAIL_PELUQUERO, pageable);

        assertEquals(1, resultado.getTotalElements());
        assertEquals(5, resultado.getContent().get(0).getIdCita());
        verify(citaRepository, never()).findAll(any(Pageable.class));
        verify(citaRepository, never()).findByUsuarioIdUsuario(anyInt(), any(Pageable.class));
    }

    @Test
    void listarCitas_peluqueroSinFichaVinculada_paginaVacia() {
        autenticarPeluquero(null);

        Page<CitaResponseDTO> resultado = citaService.listarCitas(EMAIL_PELUQUERO, pageable);

        // Una cuenta con el rol pero sin ficha no tiene agenda; devolver vacio es mas
        // honesto que reventar con un 500.
        assertTrue(resultado.isEmpty());
        verify(citaRepository, never()).findAll(any(Pageable.class));
    }
}
