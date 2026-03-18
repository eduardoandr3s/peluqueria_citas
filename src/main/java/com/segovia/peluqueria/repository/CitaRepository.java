package com.segovia.peluqueria.repository;

import com.segovia.peluqueria.model.Cita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    // Cuenta citas que se solapan con el rango [nuevoInicio, nuevoFin), excluyendo citas anuladas
    // Logica: existenteInicio < nuevoFin AND existenteFin > nuevoInicio
    @Query(value = """
            SELECT COUNT(*) FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            WHERE c.estado <> 'ANULADA'
            AND c.fecha_hora < CAST(:nuevoFin AS TIMESTAMP)
            AND (c.fecha_hora + (s.duracion * interval '1 minute')) > CAST(:nuevoInicio AS TIMESTAMP)
            """, nativeQuery = true)
    int contarConflictos(@Param("nuevoInicio") LocalDateTime nuevoInicio,
                         @Param("nuevoFin") LocalDateTime nuevoFin);

    // Igual pero excluye una cita especifica (para usar en actualizaciones)
    @Query(value = """
            SELECT COUNT(*) FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            WHERE c.estado <> 'ANULADA'
            AND c.id_cita <> :idExcluir
            AND c.fecha_hora < CAST(:nuevoFin AS TIMESTAMP)
            AND (c.fecha_hora + (s.duracion * interval '1 minute')) > CAST(:nuevoInicio AS TIMESTAMP)
            """, nativeQuery = true)
    int contarConflictosExcluyendo(@Param("nuevoInicio") LocalDateTime nuevoInicio,
                                   @Param("nuevoFin") LocalDateTime nuevoFin,
                                   @Param("idExcluir") Integer idExcluir);
}
