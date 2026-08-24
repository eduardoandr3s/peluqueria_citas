package com.segovia.peluqueria.asistente;

import com.segovia.peluqueria.calendario.dto.DiaCerradoDTO;
import com.segovia.peluqueria.cita.CitaService;
import com.segovia.peluqueria.cita.HorarioProperties;
import com.segovia.peluqueria.peluquero.PeluqueroService;
import com.segovia.peluqueria.servicio.ServicioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Herramientas que el asistente puede invocar. Todas son de <strong>solo lectura</strong>:
 * delegan en los services que ya existen y ninguna escribe en la base de datos. Eso es
 * deliberado y es lo que hace que el endpoint pueda ser público sin auth: una inyección de
 * prompt no tiene nada que romper, y al modelo no le llega ningún dato personal de clientes
 * (ninguna herramienta devuelve nombres, correos ni teléfonos).
 *
 * <p>Los DTOs de la aplicación no se exponen tal cual. Cada herramienta devuelve un record
 * mínimo con lo que hace falta para responder, porque todo lo que devuelve una herramienta
 * entra en el contexto del modelo y se paga en tokens en cada turno siguiente: la URL de la
 * foto de un servicio no ayuda a decir cuánto cuesta y costaría dinero en cada mensaje.
 *
 * <p>Las fechas entran y salen como texto ISO ({@code 2026-09-14}) en vez de como
 * {@link LocalDate}: el modelo genera texto, y así el fallo de una fecha mal escrita es un
 * mensaje que el modelo puede leer y corregir, no una excepción de deserialización.
 */
@Component
public class AsistenteHerramientas {

    private static final Logger log = LoggerFactory.getLogger(AsistenteHerramientas.class);

    /** Tope del rango de días cerrados, para que el modelo no se traiga un año de golpe. */
    private static final int MAX_DIAS_RANGO = 90;

    private final ServicioService servicioService;
    private final CitaService citaService;
    private final PeluqueroService peluqueroService;
    private final HorarioProperties horario;
    private final Clock clock;

    public AsistenteHerramientas(ServicioService servicioService,
                                 CitaService citaService,
                                 PeluqueroService peluqueroService,
                                 HorarioProperties horario,
                                 Clock clock) {
        this.servicioService = servicioService;
        this.citaService = citaService;
        this.peluqueroService = peluqueroService;
        this.horario = horario;
        this.clock = clock;
    }

    public record ServicioBreve(Integer id, String nombre, BigDecimal precioEuros, Integer minutos) {
    }

    public record PeluqueroBreve(Integer id, String nombre) {
    }

    public record DiaCerrado(String fecha, String motivo) {
    }

    public record Horario(String abre, String cierra, List<String> diasSiempreCerrados, String hoy) {
    }

    @Tool(description = """
            Catalogo de servicios de la peluqueria con su precio en euros y su duracion en
            minutos. Usala siempre antes de hablar de precios, y para obtener el id de
            servicio que necesita consultarDisponibilidad.""")
    public List<ServicioBreve> listarServicios() {
        return servicioService.listarServicios().stream()
                .map(s -> new ServicioBreve(s.getIdServicio(), s.getNombre(), s.getPrecio(), s.getDuracion()))
                .toList();
    }

    @Tool(description = """
            Horas libres para un servicio en una fecha concreta. Devuelve una lista de horas
            en formato HH:mm; si esta vacia, ese dia no queda hueco para ese servicio.
            La fecha va en formato ISO (2026-09-14) y no puede ser pasada.""")
    public List<String> consultarDisponibilidad(
            @ToolParam(description = "Fecha en formato ISO yyyy-MM-dd") String fecha,
            @ToolParam(description = "Id de servicio, tal como lo devuelve listarServicios") Integer idServicio,
            @ToolParam(required = false, description = "Id de peluquero para filtrar. Omitelo para ver los huecos de cualquiera.") Integer idPeluquero) {
        LocalDate dia = parsearFecha(fecha);
        return citaService.obtenerDisponibilidad(dia, idServicio, idPeluquero);
    }

    @Tool(description = """
            Dias en los que la peluqueria no abre dentro de un rango: festivos, cierres
            puntuales y los dias de la semana que no se trabaja, con el motivo cuando lo hay.
            Ambas fechas en formato ISO. Si no se indica ninguna, se toman los proximos 30 dias.""")
    public List<DiaCerrado> consultarDiasCerrados(
            @ToolParam(required = false, description = "Inicio del rango, ISO yyyy-MM-dd") String desde,
            @ToolParam(required = false, description = "Fin del rango, ISO yyyy-MM-dd") String hasta) {
        LocalDate inicio = (desde == null || desde.isBlank()) ? LocalDate.now(clock) : parsearFecha(desde);
        LocalDate fin = (hasta == null || hasta.isBlank()) ? inicio.plusDays(30) : parsearFecha(hasta);
        if (inicio.plusDays(MAX_DIAS_RANGO).isBefore(fin)) {
            throw new IllegalArgumentException(
                    "El rango no puede superar los " + MAX_DIAS_RANGO + " dias. Pide un rango mas corto.");
        }
        return citaService.obtenerDiasCerrados(inicio, fin).stream()
                .map(this::aDiaCerrado)
                .toList();
    }

    @Tool(description = """
            Peluqueros que atienden ahora mismo, con su id, por si el cliente quiere pedir
            cita con uno en concreto.""")
    public List<PeluqueroBreve> listarPeluqueros() {
        return peluqueroService.listarActivos().stream()
                .map(p -> new PeluqueroBreve(p.getIdPeluquero(), p.getNombre()))
                .toList();
    }

    @Tool(description = """
            Horario de apertura y cierre, dias de la semana en los que nunca se abre, y la
            fecha de hoy. Usa el 'hoy' que devuelve esta herramienta para resolver
            expresiones como 'manana' o 'el jueves': no supongas la fecha actual.""")
    public Horario consultarHorario() {
        List<String> cerrados = horario.getDiasCerrados().stream()
                .sorted()
                .map(this::nombreDia)
                .toList();
        return new Horario(horario.getApertura().toString(), horario.getCierre().toString(),
                cerrados, LocalDate.now(clock).toString());
    }

    private DiaCerrado aDiaCerrado(DiaCerradoDTO dto) {
        return new DiaCerrado(dto.getFecha().toString(), dto.getMotivo());
    }

    private String nombreDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
    }

    /**
     * El modelo puede escribir cualquier cosa en el hueco de la fecha. Se traduce el fallo
     * de formato a un mensaje que Spring AI le devuelve como resultado de la herramienta,
     * para que reintente con el formato correcto en vez de reventar la peticion.
     */
    private LocalDate parsearFecha(String fecha) {
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException | NullPointerException e) {
            log.debug("El asistente envio una fecha no parseable: {}", fecha);
            throw new IllegalArgumentException(
                    "Fecha '" + fecha + "' invalida. Usa el formato ISO yyyy-MM-dd, por ejemplo 2026-09-14.");
        }
    }
}
