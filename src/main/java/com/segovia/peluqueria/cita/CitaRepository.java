package com.segovia.peluqueria.cita;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    @Query(value = """
            SELECT COUNT(*) FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            WHERE c.estado <> 'ANULADA'
            AND c.fecha_hora < CAST(:nuevoFin AS TIMESTAMP)
            AND (c.fecha_hora + (s.duracion * interval '1 minute')) > CAST(:nuevoInicio AS TIMESTAMP)
            """, nativeQuery = true)
    int contarConflictos(@Param("nuevoInicio") LocalDateTime nuevoInicio,
                         @Param("nuevoFin") LocalDateTime nuevoFin);

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
