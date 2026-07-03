package com.segovia.peluqueria.pago;

import java.math.BigDecimal;

/**
 * Puerto hacia la pasarela de pagos. {@link PagoService} solo conoce esta interfaz; el detalle
 * de Stripe vive en el adaptador {@link StripePaymentGateway}. Ademas de aislar el dominio,
 * permite mockear la pasarela en los tests sin tocar los estaticos del SDK.
 */
public interface PaymentGateway {

    /** Intent de pago en la pasarela: id externo, secreto para el cliente y estado actual. */
    record IntentPasarela(String id, String clientSecret, String estado) {
    }

    /** Evento de webhook ya verificado: id del evento, tipo y PaymentIntent asociado (o null). */
    record EventoPasarela(String id, String tipo, String paymentIntentId) {
    }

    /** Crea un intent de pago por el importe indicado (el importe SIEMPRE sale de la BD, nunca del cliente). */
    IntentPasarela crearIntent(BigDecimal monto, String descripcion, Integer citaId);

    /** Recupera el estado actual de un intent existente. */
    IntentPasarela recuperarIntent(String id);

    /** Cancela un intent pendiente para que el cliente ya no pueda completarlo. */
    void cancelarIntent(String id);

    /** Reembolsa el importe total de un intent pagado. */
    void reembolsar(String paymentIntentId);

    /**
     * Verifica la firma del webhook y lo traduce a un {@link EventoPasarela}.
     *
     * @throws IllegalArgumentException si la firma no es valida (el controller respondera 400)
     */
    EventoPasarela validarWebhook(String payload, String firma);
}
