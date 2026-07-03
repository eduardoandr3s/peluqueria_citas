package com.segovia.peluqueria.pago;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventoRepository extends JpaRepository<StripeEvento, String> {
}
