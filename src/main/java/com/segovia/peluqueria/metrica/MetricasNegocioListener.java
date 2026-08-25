package com.segovia.peluqueria.metrica;

import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaAnuladaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaModificadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaRecordatorioEvent;
import com.segovia.peluqueria.notificacion.evento.PagoConfirmadoEvent;
import com.segovia.peluqueria.notificacion.evento.PasswordResetSolicitadoEvent;
import com.segovia.peluqueria.notificacion.evento.UsuarioRegistradoEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cuenta lo que le pasa al negocio, para que el dashboard diga algo más que "la JVM está
 * viva": citas, pagos, recordatorios y altas. Lo técnico (memoria, latencias, pool de
 * conexiones) lo publica Actuator por su cuenta y no hace falta escribirlo.
 *
 * <p>Escucha los eventos de dominio que ya existían para los emails, así que
 * <strong>ningún service se ha tocado para medir</strong>. Eso no es solo comodidad: si
 * instrumentar obligara a pasar un {@code MeterRegistry} a cada service, medir algo nuevo
 * sería modificar lógica de negocio, y nadie quiere tocar el cobro de un pago para añadir
 * un contador.
 *
 * <p>Cuenta en {@code AFTER_COMMIT}, igual que los emails y por el mismo motivo: una cita
 * cuyo insert acaba en rollback no es una cita, y contarla convertiría el dashboard en un
 * generador de cifras infladas. Si algún día esto pasa por una cola de mensajes, esta fase
 * es lo primero que hay que conservar.
 *
 * <p><strong>Aquí no entra ningún dato personal.</strong> Los eventos traen nombre y correo
 * del cliente, y ninguno se usa como etiqueta. Son dos problemas en uno: datos personales
 * saliendo a un sistema de métricas que nadie considera una base de datos de clientes, y una
 * serie temporal nueva por cada correo distinto — la explosión de cardinalidad que tumba a
 * Prometheus. El nombre del servicio sí se etiqueta porque está acotado por el catálogo.
 */
@Component
public class MetricasNegocioListener {

    /** Valor de etiqueta cuando el evento no trae servicio. Micrometer no admite null. */
    private static final String SIN_SERVICIO = "desconocido";

    private final MeterRegistry registry;

    public MetricasNegocioListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaAgendada(CitaAgendadaEvent e) {
        contarCita("agendada", e.servicioNombre());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaModificada(CitaModificadaEvent e) {
        contarCita("modificada", e.servicioNombre());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaAnulada(CitaAnuladaEvent e) {
        contarCita("anulada", e.servicioNombre());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPagoConfirmado(PagoConfirmadoEvent e) {
        Counter.builder("peluqueria.pagos.confirmados")
                .description("Pagos confirmados por la pasarela")
                .tag("servicio", etiqueta(e.servicioNombre()))
                .register(registry)
                .increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaRecordatorio(CitaRecordatorioEvent e) {
        Counter.builder("peluqueria.recordatorios.enviados")
                .description("Recordatorios de cita enviados por el scheduler")
                .register(registry)
                .increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUsuarioRegistrado(UsuarioRegistradoEvent e) {
        Counter.builder("peluqueria.usuarios.registrados")
                .description("Altas de clientes")
                .register(registry)
                .increment();
    }

    /**
     * Este no es de negocio, es de seguridad: un pico de peticiones de recuperación es la
     * señal de que alguien está probando correos ajenos. El rate limit ya lo frena, pero
     * frenarlo sin verlo no sirve de nada.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetSolicitado(PasswordResetSolicitadoEvent e) {
        Counter.builder("peluqueria.password.reset.solicitados")
                .description("Peticiones de restablecimiento de contrasena")
                .register(registry)
                .increment();
    }

    /**
     * Una sola métrica con etiqueta {@code estado} en vez de tres contadores distintos: así
     * el total de citas es una suma y la tasa de anulación es una división, en vez de tener
     * que saberse de memoria los nombres de tres métricas que hay que cruzar.
     */
    private void contarCita(String estado, String servicio) {
        Counter.builder("peluqueria.citas")
                .description("Citas por estado")
                .tag("estado", estado)
                .tag("servicio", etiqueta(servicio))
                .register(registry)
                .increment();
    }

    private String etiqueta(String servicio) {
        return (servicio == null || servicio.isBlank()) ? SIN_SERVICIO : servicio;
    }
}
