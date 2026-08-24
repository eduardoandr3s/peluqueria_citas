package com.segovia.peluqueria.asistente;

import com.segovia.peluqueria.calendario.dto.DiaCerradoDTO;
import com.segovia.peluqueria.cita.CitaService;
import com.segovia.peluqueria.cita.HorarioProperties;
import com.segovia.peluqueria.peluquero.PeluqueroService;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.servicio.ServicioService;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AsistenteHerramientasTest {

    /** Clock fijo: un lunes, para que "hoy" no dependa del dia en que se ejecute la suite. */
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-09-14T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private ServicioService servicioService;
    private CitaService citaService;
    private PeluqueroService peluqueroService;
    private HorarioProperties horario;
    private AsistenteHerramientas herramientas;

    @BeforeEach
    void setUp() {
        servicioService = mock(ServicioService.class);
        citaService = mock(CitaService.class);
        peluqueroService = mock(PeluqueroService.class);
        horario = new HorarioProperties();
        herramientas = new AsistenteHerramientas(
                servicioService, citaService, peluqueroService, horario, RELOJ);
    }

    private ServicioResponseDTO servicio() {
        ServicioResponseDTO dto = new ServicioResponseDTO();
        dto.setIdServicio(3);
        dto.setNombre("Corte de caballero");
        dto.setPrecio(new BigDecimal("15.00"));
        dto.setDuracion(30);
        dto.setUrlImagen("https://supabase.example/servicios/corte.webp");
        dto.setDescripcion("Descripcion larga que el modelo no necesita para dar un precio");
        return dto;
    }

    @Test
    void listarServicios_soloDevuelveLoQueElModeloNecesita() {
        when(servicioService.listarServicios()).thenReturn(List.of(servicio()));

        List<AsistenteHerramientas.ServicioBreve> resultado = herramientas.listarServicios();

        assertEquals(1, resultado.size());
        AsistenteHerramientas.ServicioBreve breve = resultado.get(0);
        assertEquals(3, breve.id());
        assertEquals("Corte de caballero", breve.nombre());
        assertEquals(new BigDecimal("15.00"), breve.precioEuros());
        assertEquals(30, breve.minutos());
    }

    /**
     * Todo lo que devuelve una herramienta entra en el contexto y se paga en tokens en cada
     * turno siguiente. La URL de la imagen y la descripcion no ayudan a responder por
     * precios, asi que no deben viajar: el record no tiene donde ponerlas.
     */
    @Test
    void listarServicios_noExponeImagenNiDescripcion() {
        when(servicioService.listarServicios()).thenReturn(List.of(servicio()));

        String serializado = herramientas.listarServicios().get(0).toString();

        assertFalse(serializado.contains("supabase.example"), "la URL de la imagen no debe viajar al modelo");
        assertFalse(serializado.contains("Descripcion larga"), "la descripcion no debe viajar al modelo");
    }

    @Test
    void consultarDisponibilidad_delegaConLaFechaParseada() {
        when(citaService.obtenerDisponibilidad(LocalDate.of(2026, 9, 15), 3, null))
                .thenReturn(List.of("09:00", "09:30"));

        List<String> horas = herramientas.consultarDisponibilidad("2026-09-15", 3, null);

        assertEquals(List.of("09:00", "09:30"), horas);
        verify(citaService).obtenerDisponibilidad(LocalDate.of(2026, 9, 15), 3, null);
    }

    @Test
    void consultarDisponibilidad_pasaElPeluqueroCuandoSeIndica() {
        when(citaService.obtenerDisponibilidad(any(), anyInt(), eq(7))).thenReturn(List.of());

        herramientas.consultarDisponibilidad("2026-09-15", 3, 7);

        verify(citaService).obtenerDisponibilidad(LocalDate.of(2026, 9, 15), 3, 7);
    }

    /**
     * El modelo escribe la fecha como texto y puede equivocarse. El fallo tiene que ser un
     * mensaje que pueda leer y corregir, no una excepcion de parseo opaca, y sobre todo no
     * debe llegar a llamar al service.
     */
    @Test
    void consultarDisponibilidad_fechaInvalidaExplicaElFormato() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> herramientas.consultarDisponibilidad("el jueves que viene", 3, null));

        assertTrue(ex.getMessage().contains("yyyy-MM-dd"), "el mensaje debe decir el formato esperado");
        verifyNoInteractions(citaService);
    }

    @Test
    void consultarDiasCerrados_sinFechasTomaLosProximosTreintaDias() {
        when(citaService.obtenerDiasCerrados(any(), any())).thenReturn(List.of());

        herramientas.consultarDiasCerrados(null, null);

        verify(citaService).obtenerDiasCerrados(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 14));
    }

    @Test
    void consultarDiasCerrados_traduceElMotivo() {
        when(citaService.obtenerDiasCerrados(any(), any())).thenReturn(
                List.of(new DiaCerradoDTO(LocalDate.of(2026, 12, 25), "Navidad")));

        List<AsistenteHerramientas.DiaCerrado> cerrados =
                herramientas.consultarDiasCerrados("2026-12-01", "2026-12-31");

        assertEquals(1, cerrados.size());
        assertEquals("2026-12-25", cerrados.get(0).fecha());
        assertEquals("Navidad", cerrados.get(0).motivo());
    }

    /**
     * Sin tope, el modelo puede pedir un año de cierres de una vez y meter cientos de filas
     * en el contexto, que se pagan en tokens en todos los turnos siguientes.
     */
    @Test
    void consultarDiasCerrados_rangoDemasiadoLargoSeRechaza() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> herramientas.consultarDiasCerrados("2026-01-01", "2026-12-31"));

        assertTrue(ex.getMessage().contains("90"));
        verifyNoInteractions(citaService);
    }

    @Test
    void listarPeluqueros_devuelveIdYNombre() {
        PeluqueroResponseDTO dto = new PeluqueroResponseDTO();
        dto.setIdPeluquero(7);
        dto.setNombre("Lalo");
        dto.setActivo(true);
        when(peluqueroService.listarActivos()).thenReturn(List.of(dto));

        List<AsistenteHerramientas.PeluqueroBreve> resultado = herramientas.listarPeluqueros();

        assertEquals(1, resultado.size());
        assertEquals(7, resultado.get(0).id());
        assertEquals("Lalo", resultado.get(0).nombre());
    }

    /**
     * El modelo no sabe en que dia vive. Si no le damos "hoy", resuelve "manana" inventando
     * una fecha, que es el fallo mas facil y mas confuso para el cliente.
     */
    @Test
    void consultarHorario_incluyeLaFechaDeHoy() {
        AsistenteHerramientas.Horario resultado = herramientas.consultarHorario();

        assertEquals("2026-09-14", resultado.hoy());
        assertEquals("09:00", resultado.abre());
        assertEquals("20:00", resultado.cierra());
        assertEquals(List.of("domingo"), resultado.diasSiempreCerrados());
    }

    @Test
    void consultarHorario_reflejaLaConfiguracionYNoValoresFijos() {
        horario.setApertura(LocalTime.of(10, 30));
        horario.setCierre(LocalTime.of(21, 0));
        horario.setDiasCerrados(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY));

        AsistenteHerramientas.Horario resultado = herramientas.consultarHorario();

        assertEquals("10:30", resultado.abre());
        assertEquals("21:00", resultado.cierra());
        assertEquals(List.of("lunes", "domingo"), resultado.diasSiempreCerrados());
    }
}
