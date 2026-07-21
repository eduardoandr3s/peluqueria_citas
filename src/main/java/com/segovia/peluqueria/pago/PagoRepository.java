package com.segovia.peluqueria.pago;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Integer> {
    Optional<Pago> findByCitaIdCita(Integer citaId);
    Optional<Pago> findByReferenciaExterna(String referenciaExterna);

    // Pagos de un conjunto de citas en una sola consulta (evita N+1 al listar citas con su estado de pago).
    List<Pago> findByCitaIdCitaIn(Collection<Integer> citaIds);

    @Query(value = """
            SELECT COALESCE(SUM(monto), 0) FROM pagos
            WHERE estado_pago = 'PAGADO'
            AND fecha_pago >= CAST(:desde AS TIMESTAMP)
            AND fecha_pago < CAST(:hasta AS TIMESTAMP)
            """, nativeQuery = true)
    BigDecimal sumIngresos(@Param("desde") LocalDateTime desde,
                           @Param("hasta") LocalDateTime hasta);

    @Query(value = """
            SELECT metodo_pago, COALESCE(SUM(monto), 0) AS total
            FROM pagos
            WHERE estado_pago = 'PAGADO'
            AND fecha_pago >= CAST(:desde AS TIMESTAMP)
            AND fecha_pago < CAST(:hasta AS TIMESTAMP)
            GROUP BY metodo_pago
            """, nativeQuery = true)
    List<Object[]> ingresosPorMetodoPago(@Param("desde") LocalDateTime desde,
                                          @Param("hasta") LocalDateTime hasta);
}
