package com.segovia.peluqueria.negocio;

import com.segovia.peluqueria.negocio.dto.ActualizarNegocioDTO;
import com.segovia.peluqueria.negocio.dto.NegocioDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Los datos de esta peluqueria: identidad, contacto y horario fijo semanal.
 *
 * <p>Es el unico sitio al que se pregunta "a que hora abre" y "que dias no abre". Antes esa
 * respuesta salia de {@code application.properties}, o sea del despliegue, y por eso no
 * podia ser distinta para cada cliente sin tocar Render y reiniciar.
 *
 * <p>Lleva cache en memoria por lo mismo que {@code ModuloService} y {@code PermisoService}:
 * el horario se pregunta en el camino de cada peticion que agenda o pinta huecos, y la fila
 * cambia una vez cada varios meses. Se rellena perezosamente y se tira entera al escribir.
 *
 * <p><b>Con varias instancias del backend habria que invalidarla tambien en las otras.</b>
 * Hoy corre una sola en Render; si algun dia se escala, este es uno de los tres sitios.
 */
@Service
public class NegocioService {

    private final NegocioRepository negocioRepository;

    /** null = todavia no cargado. Se reemplaza entero, nunca se muta. */
    private final AtomicReference<NegocioDTO> cache = new AtomicReference<>();

    public NegocioService(NegocioRepository negocioRepository) {
        this.negocioRepository = negocioRepository;
    }

    /** La ficha entera. Publica: lo que hay aqui es lo que el negocio quiere que se sepa. */
    @Transactional(readOnly = true)
    public NegocioDTO ficha() {
        return ficha0();
    }

    /** Hora a la que abre. Primer hueco que se ofrece al agendar. */
    @Transactional(readOnly = true)
    public LocalTime apertura() {
        return ficha0().getHoraApertura();
    }

    /** Hora a la que cierra. Una cita tiene que caber entera antes de ella. */
    @Transactional(readOnly = true)
    public LocalTime cierre() {
        return ficha0().getHoraCierre();
    }

    /**
     * Dias de la semana en los que no se abre nunca. Los festivos y cierres puntuales no
     * estan aqui: esos son {@code dias_bloqueados}, que van por fecha.
     */
    @Transactional(readOnly = true)
    public Set<DayOfWeek> diasCerrados() {
        return ficha0().getDiasCerrados();
    }

    /**
     * Guarda la ficha entera. Solo la toca un ADMIN: cambiar el horario mueve los huecos
     * que se le ofrecen a todo el mundo.
     *
     * <p>Se comprueba aqui que la apertura sea anterior al cierre aunque la tabla tenga su
     * CHECK: dejar que salte el de la base de datos convertiria un error del formulario en
     * un 500 sin nada que leer.
     */
    @Transactional
    public NegocioDTO actualizar(ActualizarNegocioDTO request) {
        if (!request.getHoraApertura().isBefore(request.getHoraCierre())) {
            throw new IllegalArgumentException("La hora de apertura debe ser anterior a la de cierre.");
        }

        Negocio negocio = fila();
        negocio.setNombre(request.getNombre().trim());
        negocio.setEslogan(vacioANull(request.getEslogan()));
        negocio.setTelefono(vacioANull(request.getTelefono()));
        negocio.setEmail(vacioANull(request.getEmail()));
        negocio.setDireccion(vacioANull(request.getDireccion()));
        negocio.setLocalidad(vacioANull(request.getLocalidad()));
        negocio.setLogoUrl(vacioANull(request.getLogoUrl()));
        negocio.setColorPrimario(vacioANull(request.getColorPrimario()));
        negocio.setHoraApertura(request.getHoraApertura());
        negocio.setHoraCierre(request.getHoraCierre());
        negocio.setDiasCerrados(serializar(request.getDiasCerrados()));
        negocioRepository.save(negocio);

        cache.set(null);
        return ficha0();
    }

    private NegocioDTO ficha0() {
        NegocioDTO actual = cache.get();
        if (actual != null) {
            return actual;
        }
        Negocio fila = fila();
        NegocioDTO calculada = new NegocioDTO(fila, deserializar(fila.getDiasCerrados()));
        cache.set(calculada);
        return calculada;
    }

    /**
     * La fila unica. Que no exista no es un caso de uso: la siembra la migracion y el
     * CHECK de la tabla impide que haya otra. Si falta, la instalacion esta rota y vale
     * mas decirlo que inventarse un nombre y un horario que nadie configuro.
     */
    private Negocio fila() {
        return negocioRepository.findById(Negocio.ID)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay fila de negocio: la instalacion esta incompleta. "
                                + "Deberia haberla sembrado la migracion V17."));
    }

    private String serializar(Set<DayOfWeek> dias) {
        if (dias == null || dias.isEmpty()) {
            return "";
        }
        return dias.stream()
                .sorted()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    /**
     * Una clave que ya no sea un dia valido se ignora en vez de tumbar el arranque: es
     * texto en una columna y el precio de equivocarse es abrir un dia de mas, no que la
     * peluqueria entera deje de responder.
     */
    private Set<DayOfWeek> deserializar(String dias) {
        Set<DayOfWeek> resultado = EnumSet.noneOf(DayOfWeek.class);
        if (dias == null || dias.isBlank()) {
            return resultado;
        }
        for (String clave : dias.split(",")) {
            Arrays.stream(DayOfWeek.values())
                    .filter(dia -> dia.name().equalsIgnoreCase(clave.trim()))
                    .findFirst()
                    .ifPresent(resultado::add);
        }
        return resultado;
    }

    private String vacioANull(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
