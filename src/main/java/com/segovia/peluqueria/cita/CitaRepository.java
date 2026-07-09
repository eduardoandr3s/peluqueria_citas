package com.segovia.peluqueria.cita;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    Page<Cita> findByUsuarioIdUsuario(Integer idUsuario, Pageable pageable);

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

    @Query(value = """
            SELECT COUNT(*) FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            WHERE c.estado <> 'ANULADA'
            AND c.fecha_hora < CAST(:nuevoFin AS TIMESTAMP)
            AND (c.fecha_hora + (s.duracion * interval '1 minute')) > CAST(:nuevoInicio AS TIMESTAMP)
            AND (c.peluquero_id IS NULL OR c.peluquero_id = :peluqueroId)
            """, nativeQuery = true)
    int contarConflictosConPeluquero(@Param("nuevoInicio") LocalDateTime nuevoInicio,
                                     @Param("nuevoFin") LocalDateTime nuevoFin,
                                     @Param("peluqueroId") Integer peluqueroId);

    @Query(value = """
            SELECT COUNT(*) FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            WHERE c.estado <> 'ANULADA'
            AND c.id_cita <> :idExcluir
            AND c.fecha_hora < CAST(:nuevoFin AS TIMESTAMP)
            AND (c.fecha_hora + (s.duracion * interval '1 minute')) > CAST(:nuevoInicio AS TIMESTAMP)
            AND (c.peluquero_id IS NULL OR c.peluquero_id = :peluqueroId)
            """, nativeQuery = true)
    int contarConflictosExcluyendoConPeluquero(@Param("nuevoInicio") LocalDateTime nuevoInicio,
                                               @Param("nuevoFin") LocalDateTime nuevoFin,
                                               @Param("idExcluir") Integer idExcluir,
                                               @Param("peluqueroId") Integer peluqueroId);

    @Query(value = """
            SELECT c.estado, COUNT(*) AS total
            FROM citas c
            WHERE c.fecha_hora >= CAST(:desde AS TIMESTAMP)
            AND c.fecha_hora < CAST(:hasta AS TIMESTAMP)
            GROUP BY c.estado
            """, nativeQuery = true)
    List<Object[]> contarCitasPorEstado(@Param("desde") LocalDateTime desde,
                                         @Param("hasta") LocalDateTime hasta);

    @Query(value = """
            SELECT s.nombre, COUNT(*) AS total
            FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            WHERE c.estado <> 'ANULADA'
            AND c.fecha_hora >= CAST(:desde AS TIMESTAMP)
            AND c.fecha_hora < CAST(:hasta AS TIMESTAMP)
            GROUP BY s.id_servicio, s.nombre
            ORDER BY total DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> topServicios(@Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta);

    List<Cita> findByEstadoAndRecordatorioEnviadoFalseAndFechaHoraBetween(
            EstadoCita estado, LocalDateTime inicio, LocalDateTime fin);
}
