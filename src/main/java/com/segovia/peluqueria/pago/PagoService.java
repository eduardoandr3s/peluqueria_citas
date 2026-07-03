package com.segovia.peluqueria.pago;

import com.segovia.peluqueria.cita.Cita;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.pago.dto.PagoResponseDTO;
import com.segovia.peluqueria.pago.dto.PaymentIntentResponseDTO;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public PagoService(PagoRepository pagoRepository,
                       CitaRepository citaRepository,
                       UsuarioRepository usuarioRepository) {
        this.pagoRepository = pagoRepository;
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public PaymentIntentResponseDTO crearPaymentIntent(Integer citaId, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + citaId));
        verificarAcceso(cita, actual);

        if (cita.getEstado() == EstadoCita.ANULADA) {
            throw new IllegalArgumentException("No se puede pagar una cita anulada.");
        }

        if (pagoRepository.findByCitaIdCita(citaId).isPresent()) {
            throw new IllegalArgumentException("Esta cita ya tiene un registro de pago.");
        }

        try {
            BigDecimal monto = cita.getServicio().getPrecio();
            long montoCentavos = monto.multiply(BigDecimal.valueOf(100)).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(montoCentavos)
                    .setCurrency("eur")
                    .setDescription("Pago cita " + cita.getServicio().getNombre() + " - " + cita.getUsuario().getNombre())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            Pago pago = new Pago();
            pago.setCita(cita);
            pago.setMonto(monto);
            pago.setMetodoPago(MetodoPago.TARJETA);
            pago.setEstadoPago(EstadoPago.PENDIENTE);
            pago.setReferenciaExterna(intent.getId());
            pago.setFechaCreacion(LocalDateTime.now());
            pagoRepository.save(pago);

            PaymentIntentResponseDTO response = new PaymentIntentResponseDTO();
            response.setClientSecret(intent.getClientSecret());
            response.setPaymentIntentId(intent.getId());
            response.setPagoId(pago.getIdPago());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException("Error al crear el pago con Stripe: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void procesarWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Firma del webhook invalida.", e);
        }

        if (!"payment_intent.succeeded".equals(event.getType())) {
            return;
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (!deserializer.getObject().isPresent()) {
            return;
        }

        StripeObject stripeObject = deserializer.getObject().get();
        PaymentIntent intent = (PaymentIntent) stripeObject;

        Pago pago = pagoRepository.findByReferenciaExterna(intent.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con referencia: " + intent.getId()));

        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setFechaPago(LocalDateTime.now());
        pagoRepository.save(pago);

        Cita cita = pago.getCita();
        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);
    }

    @Transactional
    public PagoResponseDTO registrarPagoManual(Integer citaId, MetodoPago metodoPago, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        if (actual.getRol() != Rol.ADMIN) {
            throw new AccessDeniedException("Solo un administrador puede registrar pagos manuales.");
        }

        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + citaId));

        if (pagoRepository.findByCitaIdCita(citaId).isPresent()) {
            throw new IllegalArgumentException("Esta cita ya tiene un registro de pago.");
        }

        if (cita.getEstado() == EstadoCita.ANULADA) {
            throw new IllegalArgumentException("No se puede pagar una cita anulada.");
        }

        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setMonto(cita.getServicio().getPrecio());
        pago.setMetodoPago(metodoPago);
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setFechaCreacion(LocalDateTime.now());
        pago.setFechaPago(LocalDateTime.now());
        pagoRepository.save(pago);

        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);

        return PagoResponseDTO.desde(pago);
    }

    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPagoPorCita(Integer citaId, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + citaId));
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
            try {
                RefundCreateParams params = RefundCreateParams.builder()
                        .setPaymentIntent(pago.getReferenciaExterna())
                        .build();
                Refund.create(params);
            } catch (StripeException e) {
                throw new RuntimeException("Error al reembolsar en Stripe: " + e.getMessage(), e);
            }
        }

        pago.setEstadoPago(EstadoPago.REEMBOLSADO);
        pagoRepository.save(pago);
    }

    private Cita obtenerCitaPorId(Integer id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    private Usuario obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    private boolean esAdmin(Usuario usuario) {
        return usuario.getRol() == Rol.ADMIN;
    }

    private void verificarAcceso(Cita cita, Usuario actual) {
        if (!esAdmin(actual) && !cita.getUsuario().getIdUsuario().equals(actual.getIdUsuario())) {
            throw new AccessDeniedException("No tienes permiso para acceder a este recurso.");
        }
    }
}
