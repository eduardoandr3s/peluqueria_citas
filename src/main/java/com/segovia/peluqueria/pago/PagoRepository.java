package com.segovia.peluqueria.pago;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Integer> {
    Optional<Pago> findByCitaIdCita(Integer citaId);
    Optional<Pago> findByReferenciaExterna(String referenciaExterna);
}
