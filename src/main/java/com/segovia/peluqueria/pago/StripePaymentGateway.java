package com.segovia.peluqueria.pago;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Adaptador de {@link PaymentGateway} sobre el SDK de Stripe. Es el unico punto de la aplicacion
 * que toca el SDK: inicializa la clave global, crea/recupera/cancela PaymentIntents, emite
 * reembolsos y verifica la firma de los webhooks.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final String secretKey;
    private final String webhookSecret;

    public StripePaymentGateway(@Value("${stripe.secret-key}") String secretKey,
                                @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @PostConstruct
    void inicializarClave() {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("STRIPE_SECRET_KEY no configurada: los pagos online fallaran hasta definirla.");
        }
        Stripe.apiKey = secretKey;
    }

    @Override
    public IntentPasarela crearIntent(BigDecimal monto, String descripcion, Integer citaId) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(monto.movePointRight(2).longValueExact())
                    .setCurrency("eur")
                    .setDescription(descripcion)
                    // La citaId viaja como metadata para poder reconciliar desde el dashboard de Stripe.
                    .putMetadata("citaId", String.valueOf(citaId))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            return new IntentPasarela(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Error al crear el pago en Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    public IntentPasarela recuperarIntent(String id) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(id);
            return new IntentPasarela(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Error al recuperar el pago en Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelarIntent(String id) {
        try {
            PaymentIntent.retrieve(id).cancel();
        } catch (StripeException e) {
            throw new RuntimeException("Error al cancelar el pago en Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    public void reembolsar(String paymentIntentId) {
        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build());
        } catch (StripeException e) {
            throw new RuntimeException("Error al reembolsar en Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    public EventoPasarela validarWebhook(String payload, String firma) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, firma, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Firma del webhook invalida.", e);
        }
        return new EventoPasarela(event.getId(), event.getType(), extraerPaymentIntentId(event));
    }

    /**
     * Extrae el id del PaymentIntent del evento. Si la version del API de la cuenta no coincide
     * con la del SDK, {@code getObject()} viene vacio; se intenta la deserializacion laxa antes
     * de rendirse, para que un desfase de versiones no deje pagos sin confirmar en silencio.
     */
    private String extraerPaymentIntentId(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject objeto = deserializer.getObject().orElse(null);
        if (objeto == null) {
            try {
                objeto = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                log.error("No se pudo deserializar el objeto del evento {} (tipo {}): revisar la version del API de Stripe.",
                        event.getId(), event.getType(), e);
                return null;
            }
        }
        return objeto instanceof PaymentIntent intent ? intent.getId() : null;
    }
}
