package com.segovia.peluqueria.metrica;

import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaAnuladaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaModificadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaRecordatorioEvent;
import com.segovia.peluqueria.notificacion.evento.PagoConfirmadoEvent;
import com.segovia.peluqueria.notificacion.evento.PasswordResetSolicitadoEvent;
import com.segovia.peluqueria.notificacion.evento.UsuarioRegistradoEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los contadores de negocio. Se prueban con un {@link SimpleMeterRegistry} de verdad en vez
 * de con un doble, porque lo que interesa fijar es el nombre y las etiquetas de cada métrica:
 * un dashboard y una alerta se escriben contra esos nombres, así que renombrarlos rompe algo
 * que está fuera de este repositorio y ningún compilador avisa.
 */
class MetricasNegocioListenerTest {

    private SimpleMeterRegistry registry;
    private MetricasNegocioListener listener;

    private static final LocalDateTime CUANDO = LocalDateTime.of(2026, 9, 14, 10, 0);

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new MetricasNegocioListener(registry);
    }

    @Test
    void lasTresAccionesSobreUnaCitaCompartenMetricaYSeDistinguenPorLaEtiquetaEstado() {
        listener.onCitaAgendada(new CitaAgendadaEvent("Ana", "ana@x.com", "Corte", CUANDO));
        listener.onCitaAgendada(new CitaAgendadaEvent("Luis", "luis@x.com", "Corte", CUANDO));
        listener.onCitaModificada(new CitaModificadaEvent("Ana", "ana@x.com", "Corte", CUANDO));
        listener.onCitaAnulada(new CitaAnuladaEvent("Ana", "ana@x.com", "Corte", CUANDO));

        assertEquals(2, contadorCitas("agendada", "Corte"));
        assertEquals(1, contadorCitas("modificada", "Corte"));
        assertEquals(1, contadorCitas("anulada", "Corte"));
    }

    @Test
    void elServicioEsUnaEtiquetaYNoUnaMetricaAparte() {
        listener.onCitaAgendada(new CitaAgendadaEvent("Ana", "ana@x.com", "Corte", CUANDO));
        listener.onCitaAgendada(new CitaAgendadaEvent("Luis", "luis@x.com", "Tinte", CUANDO));

        assertEquals(1, contadorCitas("agendada", "Corte"));
        assertEquals(1, contadorCitas("agendada", "Tinte"));
        // Una sola metrica con dos series, que es lo que permite sumar el total en Prometheus.
        assertEquals(2, registry.find("peluqueria.citas").counters().size());
    }

    /**
     * Micrometer lanza si una etiqueta llega a null, y eso convertiria un dato ausente en un
     * fallo dentro de un listener de eventos, donde nadie lo espera. Se sustituye por un
     * valor fijo.
     */
    @Test
    void unServicioSinNombreNoRevientaYCaeEnLaEtiquetaDesconocido() {
        listener.onCitaAgendada(new CitaAgendadaEvent("Ana", "ana@x.com", null, CUANDO));
        listener.onCitaAgendada(new CitaAgendadaEvent("Ana", "ana@x.com", "   ", CUANDO));

        assertEquals(2, contadorCitas("agendada", "desconocido"));
    }

    /**
     * La regla que no puede relajarse: los eventos traen nombre y correo del cliente, y
     * ninguna metrica los lleva. Son datos personales, y cada correo distinto crearia una
     * serie temporal nueva.
     */
    @Test
    void ningunaMetricaEtiquetaDatosPersonalesDelCliente() {
        listener.onCitaAgendada(new CitaAgendadaEvent("Ana", "ana@x.com", "Corte", CUANDO));
        listener.onPagoConfirmado(new PagoConfirmadoEvent("Ana", "ana@x.com", "Corte", CUANDO));
        listener.onUsuarioRegistrado(new UsuarioRegistradoEvent("Ana", "ana@x.com"));

        boolean hayDatosPersonales = registry.getMeters().stream()
                .flatMap(m -> m.getId().getTags().stream())
                .anyMatch(t -> t.getValue().contains("@") || t.getValue().equals("Ana"));

        assertTrue(registry.getMeters().size() >= 3, "deberia haber contado las tres cosas");
        assertTrue(!hayDatosPersonales, "ninguna etiqueta puede llevar nombre ni correo");
    }

    @Test
    void pagosRecordatoriosAltasYRecuperacionesTienenSuPropioContador() {
        listener.onPagoConfirmado(new PagoConfirmadoEvent("Ana", "ana@x.com", "Tinte", CUANDO));
        listener.onCitaRecordatorio(new CitaRecordatorioEvent("Ana", "ana@x.com", "Tinte", CUANDO));
        listener.onUsuarioRegistrado(new UsuarioRegistradoEvent("Ana", "ana@x.com"));
        listener.onPasswordResetSolicitado(new PasswordResetSolicitadoEvent("Ana", "ana@x.com", "http://x"));

        assertEquals(1, registry.get("peluqueria.pagos.confirmados").tag("servicio", "Tinte").counter().count(), 0.0);
        assertEquals(1, registry.get("peluqueria.recordatorios.enviados").counter().count(), 0.0);
        assertEquals(1, registry.get("peluqueria.usuarios.registrados").counter().count(), 0.0);
        assertEquals(1, registry.get("peluqueria.password.reset.solicitados").counter().count(), 0.0);
    }

    /**
     * Un contador que nunca se incrementa no existe en el registro, y por tanto tampoco en
     * Prometheus. No es un fallo: es lo que hace que el endpoint no publique cientos de
     * series a cero, pero conviene tenerlo escrito para que nadie busque una metrica que
     * simplemente no ha ocurrido todavia.
     */
    @Test
    void unContadorSoloApareceCuandoHaOcurridoAlgo() {
        assertNull(registry.find("peluqueria.citas").counter());

        listener.onCitaAgendada(new CitaAgendadaEvent("Ana", "ana@x.com", "Corte", CUANDO));

        assertEquals(1, contadorCitas("agendada", "Corte"));
    }

    private double contadorCitas(String estado, String servicio) {
        return registry.get("peluqueria.citas")
                .tag("estado", estado)
                .tag("servicio", servicio)
                .counter()
                .count();
    }
}
