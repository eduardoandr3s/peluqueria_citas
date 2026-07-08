package com.segovia.peluqueria.notificacion;

import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaAnuladaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaModificadaEvent;
import com.segovia.peluqueria.notificacion.evento.PagoConfirmadoEvent;
import com.segovia.peluqueria.notificacion.evento.PasswordCambiadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaRecordatorioEvent;
import com.segovia.peluqueria.notificacion.evento.PasswordResetSolicitadoEvent;
import com.segovia.peluqueria.notificacion.evento.UsuarioRegistradoEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Escucha los eventos de dominio y dispara los correos correspondientes. Usa
 * {@link TransactionalEventListener} con fase AFTER_COMMIT para no notificar de operaciones
 * que acaben revertidas; el envio en si es asincrono dentro de {@link EmailService}.
 */
@Component
public class NotificacionEmailListener {

    private static final Locale ES = Locale.of("es");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", ES);

    private final EmailService email;
    private final String emailNegocio;

    public NotificacionEmailListener(EmailService email,
                                     @Value("${peluqueria.mail.negocio}") String emailNegocio) {
        this.email = email;
        this.emailNegocio = emailNegocio;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaAgendada(CitaAgendadaEvent e) {
        notificarCita(e.clienteNombre(), e.clienteEmail(), e.servicioNombre(), e.fechaHora(), "reservada");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaModificada(CitaModificadaEvent e) {
        notificarCita(e.clienteNombre(), e.clienteEmail(), e.servicioNombre(), e.fechaHora(), "modificada");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaAnulada(CitaAnuladaEvent e) {
        notificarCita(e.clienteNombre(), e.clienteEmail(), e.servicioNombre(), e.fechaHora(), "anulada");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPagoConfirmado(PagoConfirmadoEvent e) {
        notificarCita(e.clienteNombre(), e.clienteEmail(), e.servicioNombre(), e.fechaHora(), "confirmada");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUsuarioRegistrado(UsuarioRegistradoEvent e) {
        email.enviarHtml(e.email(), "Te damos la bienvenida a Lalo Segovia", "bienvenida",
                Map.of("nombre", e.nombre()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordCambiada(PasswordCambiadaEvent e) {
        email.enviarHtml(e.email(), "Tu contrasena ha sido modificada", "password-cambiada",
                Map.of("nombre", e.nombre()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCitaRecordatorio(CitaRecordatorioEvent e) {
        String fecha = FMT.format(e.fechaHora());
        email.enviarHtml(e.clienteEmail(), "Recordatorio: tienes una cita manana", "recordatorio",
                Map.of("nombre", e.clienteNombre(), "servicio", e.servicioNombre(), "fecha", fecha));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetSolicitado(PasswordResetSolicitadoEvent e) {
        email.enviarHtml(e.email(), "Restablece tu contrasena", "recuperar-password",
                Map.of("nombre", e.nombre(), "enlace", e.enlace()));
    }

    private void notificarCita(String nombre, String emailCliente, String servicio,
                               LocalDateTime fechaHora, String accion) {
        String fecha = FMT.format(fechaHora);

        // Aviso al cliente (confirmacion de SU cita).
        email.enviarHtml(emailCliente, "Tu cita ha sido " + accion, "cita-cliente",
                Map.of("nombre", nombre, "accion", accion, "servicio", servicio, "fecha", fecha));

        // Aviso al negocio (gestion).
        email.enviarHtml(emailNegocio, "Cita " + accion + " - " + nombre, "cita-negocio",
                Map.of("cliente", nombre, "accion", accion, "servicio", servicio, "fecha", fecha));
    }
}
