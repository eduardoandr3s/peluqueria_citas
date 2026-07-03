package com.segovia.peluqueria.pago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de eventos de webhook de Stripe ya procesados. Stripe reintenta la entrega mientras
 * no reciba un 2xx y puede entregar el mismo evento mas de una vez; si el id ya esta aqui,
 * el webhook responde 200 sin reprocesar (idempotencia).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stripe_evento")
public class StripeEvento {

    @Id
    @Column(name = "id_evento", length = 255)
    private String idEvento;

    @Column(length = 100, nullable = false)
    private String tipo;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDateTime fechaRecepcion;

    public StripeEvento(String idEvento, String tipo, LocalDateTime fechaRecepcion) {
        this.idEvento = idEvento;
        this.tipo = tipo;
        this.fechaRecepcion = fechaRecepcion;
    }
}
