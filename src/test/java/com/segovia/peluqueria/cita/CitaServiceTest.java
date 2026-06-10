package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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
    private CitaService citaService;

    @BeforeEach
    void setUp() {
        citaRepository = mock(CitaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        servicioRepository = mock(ServicioRepository.class);
        citaService = new CitaService(citaRepository, usuarioRepository, servicioRepository);

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

        when(citaRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CitaResponseDTO> resultado = citaService.listarCitas(EMAIL_ADMIN);

        assertEquals(2, resultado.size());
        verify(citaRepository).findAll();
        verify(citaRepository, never()).findByUsuarioIdUsuario(any());
    }

    @Test
    void listarCitas_comoUser_soloLasSuyas() {
        Usuario carlos = crearUsuarioActivo();
        Cita c1 = new Cita();
        c1.setIdCita(1);
        c1.setUsuario(carlos);

        when(usuarioRepository.findByEmail("carlos@test.com")).thenReturn(Optional.of(carlos));
        when(citaRepository.findByUsuarioIdUsuario(1)).thenReturn(List.of(c1));

        List<CitaResponseDTO> resultado = citaService.listarCitas("carlos@test.com");

        assertEquals(1, resultado.size());
        verify(citaRepository).findByUsuarioIdUsuario(1);
        verify(citaRepository, never()).findAll();
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
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        citaService.eliminarCita(1, EMAIL_ADMIN);

        verify(citaRepository).delete(cita);
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
}
