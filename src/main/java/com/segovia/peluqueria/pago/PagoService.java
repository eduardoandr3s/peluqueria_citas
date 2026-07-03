package com.segovia.peluqueria.pago;

import com.segovia.peluqueria.cita.Cita;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.notificacion.evento.PagoConfirmadoEvent;
import com.segovia.peluqueria.pago.PaymentGateway.EventoPasarela;
import com.segovia.peluqueria.pago.PaymentGateway.IntentPasarela;
import com.segovia.peluqueria.pago.dto.PagoResponseDTO;
import com.segovia.peluqueria.pago.dto.PaymentIntentResponseDTO;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Gestion de pagos de citas. El pago online usa PaymentIntents a traves del puerto
 * {@link PaymentGateway}; la fuente de verdad del cobro es SIEMPRE el webhook, nunca el
 * redirect del navegador (el cliente puede cerrar la pestana tras pagar y no volver).
 *
 * <p>Politica de reembolso: reembolsar NO anula la cita. Son decisiones separadas: el
 * administrador reembolsa aqui y, si procede, anula la cita desde el API de citas (lo que
 * ademas notifica al cliente por el flujo de eventos de cita).
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private static final String INTENT_COMPLETADO = "payment_intent.succeeded";
    private static final String INTENT_FALLIDO = "payment_intent.payment_failed";
    private static final String INTENT_CANCELADO = "payment_intent.canceled";
    private static final Set<String> TIPOS_GESTIONADOS =
            Set.of(INTENT_COMPLETADO, INTENT_FALLIDO, INTENT_CANCELADO);

    private final PagoRepository pagoRepository;
    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final StripeEventoRepository stripeEventoRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    public PagoService(PagoRepository pagoRepository,
                       CitaRepository citaRepository,
                       UsuarioRepository usuarioRepository,
                       StripeEventoRepository stripeEventoRepository,
                       PaymentGateway paymentGateway,
                       ApplicationEventPublisher eventPublisher) {
        this.pagoRepository = pagoRepository;
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.stripeEventoRepository = stripeEventoRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentIntentResponseDTO crearPaymentIntent(Integer citaId, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = obtenerCitaPorId(citaId);
        verificarAcceso(cita, actual);

        if (cita.getEstado() == EstadoCita.ANULADA) {
            throw new IllegalArgumentException("No se puede pagar una cita anulada.");
        }

        Pago pago = pagoRepository.findByCitaIdCita(citaId).orElse(null);
        if (pago != null) {
            if (pago.getEstadoPago() == EstadoPago.PAGADO || pago.getEstadoPago() == EstadoPago.REEMBOLSADO) {
                throw new IllegalArgumentException("Esta cita ya tiene un pago registrado.");
            }
            // Un intento abandonado no bloquea la cita: si el intent sigue vivo se reutiliza
            // (mismo client_secret); si murio en Stripe, se crea otro sobre el mismo registro.
            if (pago.getEstadoPago() == EstadoPago.PENDIENTE && pago.getReferenciaExterna() != null) {
                IntentPasarela existente = paymentGateway.recuperarIntent(pago.getReferenciaExterna());
                if ("succeeded".equals(existente.estado())) {
                    throw new IllegalArgumentException(
                            "El pago ya se ha completado; la confirmacion llegara en breve.");
                }
                if (!"canceled".equals(existente.estado())) {
                    return respuesta(existente, pago);
                }
            }
        } else {
            pago = new Pago();
            pago.setCita(cita);
            pago.setFechaCreacion(LocalDateTime.now());
        }

        BigDecimal monto = cita.getServicio().getPrecio();
        IntentPasarela intent = paymentGateway.crearIntent(
                monto,
                "Pago cita " + cita.getServicio().getNombre() + " - " + cita.getUsuario().getNombre(),
                citaId);

        pago.setMonto(monto);
        pago.setMetodoPago(MetodoPago.TARJETA);
        pago.setEstadoPago(EstadoPago.PENDIENTE);
        pago.setReferenciaExterna(intent.id());
        pagoRepository.save(pago);

        return respuesta(intent, pago);
    }

    @Transactional
    public void procesarWebhook(String payload, String sigHeader) {
        EventoPasarela evento = paymentGateway.validarWebhook(payload, sigHeader);

        if (stripeEventoRepository.existsById(evento.id())) {
            log.info("Webhook duplicado ignorado: {} ({})", evento.id(), evento.tipo());
            return;
        }

        if (!TIPOS_GESTIONADOS.contains(evento.tipo())) {
            log.debug("Webhook de tipo no gestionado: {}", evento.tipo());
            return;
        }

        if (evento.paymentIntentId() == null) {
            // Sin registrar el evento: si Stripe lo reenvia (o se reenvia a mano tras corregir
            // el desfase de version del API) se vuelve a intentar.
            log.error("Webhook {} ({}) sin PaymentIntent deserializable; no se procesa.",
                    evento.id(), evento.tipo());
            return;
        }

        switch (evento.tipo()) {
            case INTENT_COMPLETADO -> confirmarPagoOnline(evento.paymentIntentId());
            case INTENT_CANCELADO -> cancelarPagoOnline(evento.paymentIntentId());
            case INTENT_FALLIDO -> log.info(
                    "Intento de pago fallido para {}: el cliente puede reintentar con el mismo intent.",
                    evento.paymentIntentId());
            default -> throw new IllegalStateException("Tipo no esperado: " + evento.tipo());
        }

        stripeEventoRepository.save(new StripeEvento(evento.id(), evento.tipo(), LocalDateTime.now()));
    }

    /** El intent se completo en Stripe: marca el pago, confirma la cita y notifica por correo. */
    private void confirmarPagoOnline(String paymentIntentId) {
        Pago pago = pagoRepository.findByReferenciaExterna(paymentIntentId).orElse(null);
        if (pago == null) {
            // Puede ser un intent de otro entorno que comparte la cuenta de Stripe: se responde
            // 200 igualmente para que Stripe no reintente durante dias.
            log.warn("Webhook de pago completado sin pago asociado a la referencia {}.", paymentIntentId);
            return;
        }
        if (pago.getEstadoPago() == EstadoPago.PAGADO) {
            return;
        }
        marcarPagadoYConfirmarCita(pago);
    }

    private void cancelarPagoOnline(String paymentIntentId) {
        Pago pago = pagoRepository.findByReferenciaExterna(paymentIntentId).orElse(null);
        if (pago == null || pago.getEstadoPago() != EstadoPago.PENDIENTE) {
            return;
        }
        pago.setEstadoPago(EstadoPago.CANCELADO);
        pagoRepository.save(pago);
        log.info("Pago {} cancelado: el intent {} expiro o fue cancelado en Stripe.",
                pago.getIdPago(), paymentIntentId);
    }

    @Transactional
    public PagoResponseDTO registrarPagoManual(Integer citaId, MetodoPago metodoPago, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        if (actual.getRol() != Rol.ADMIN) {
            throw new AccessDeniedException("Solo un administrador puede registrar pagos manuales.");
        }

        Cita cita = obtenerCitaPorId(citaId);
        if (cita.getEstado() == EstadoCita.ANULADA) {
            throw new IllegalArgumentException("No se puede pagar una cita anulada.");
        }

        Pago pago = pagoRepository.findByCitaIdCita(citaId).orElse(null);
        if (pago != null) {
            if (pago.getEstadoPago() == EstadoPago.PAGADO || pago.getEstadoPago() == EstadoPago.REEMBOLSADO) {
                throw new IllegalArgumentException("Esta cita ya tiene un pago registrado.");
            }
            // Un intento online abandonado no debe bloquear el cobro en el local. Se cancela el
            // intent en Stripe (evita un doble cobro si el cliente completara el pago online
            // despues) y se reutiliza el registro.
            if (pago.getEstadoPago() == EstadoPago.PENDIENTE && pago.getReferenciaExterna() != null) {
                paymentGateway.cancelarIntent(pago.getReferenciaExterna());
            }
        } else {
            pago = new Pago();
            pago.setCita(cita);
            pago.setFechaCreacion(LocalDateTime.now());
        }

        pago.setMonto(cita.getServicio().getPrecio());
        pago.setMetodoPago(metodoPago);
        marcarPagadoYConfirmarCita(pago);

        return PagoResponseDTO.desde(pago);
    }

    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPagoPorCita(Integer citaId, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = obtenerCitaPorId(citaId);
        verificarAcceso(cita, actual);

        Pago pago = pagoRepository.findByCitaIdCita(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("No hay un pago registrado para esta cita."));
        return PagoResponseDTO.desde(pago);
    }

    @Transactional
    public void reembolsar(Integer citaId, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        if (actual.getRol() != Rol.ADMIN) {
            throw new AccessDeniedException("Solo un administrador puede reembolsar pagos.");
        }

        Pago pago = pagoRepository.findByCitaIdCita(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("No hay un pago registrado para esta cita."));

        if (pago.getEstadoPago() != EstadoPago.PAGADO) {
            throw new IllegalArgumentException("El pago no esta en estado PAGADO, no se puede reembolsar.");
        }

        if (pago.getMetodoPago() == MetodoPago.TARJETA && pago.getReferenciaExterna() != null) {
            paymentGateway.reembolsar(pago.getReferenciaExterna());
        }

        pago.setEstadoPago(EstadoPago.REEMBOLSADO);
        pagoRepository.save(pago);
    }

    private void marcarPagadoYConfirmarCita(Pago pago) {
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setFechaPago(LocalDateTime.now());
        pagoRepository.save(pago);

        Cita cita = pago.getCita();
        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);

        eventPublisher.publishEvent(new PagoConfirmadoEvent(
                cita.getUsuario().getNombre(),
                cita.getUsuario().getEmail(),
                cita.getServicio().getNombre(),
                cita.getFechaHora()));
    }

    private PaymentIntentResponseDTO respuesta(IntentPasarela intent, Pago pago) {
        PaymentIntentResponseDTO response = new PaymentIntentResponseDTO();
        response.setClientSecret(intent.clientSecret());
        response.setPaymentIntentId(intent.id());
        response.setPagoId(pago.getIdPago());
        return response;
    }

    private Cita obtenerCitaPorId(Integer id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    private Usuario obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    private void verificarAcceso(Cita cita, Usuario actual) {
        if (actual.getRol() != Rol.ADMIN && !cita.getUsuario().getIdUsuario().equals(actual.getIdUsuario())) {
            throw new AccessDeniedException("No tienes permiso para acceder a este recurso.");
        }
    }
}
