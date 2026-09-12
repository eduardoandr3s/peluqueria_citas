package com.segovia.peluqueria.produccion;

import com.segovia.peluqueria.cita.Cita;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas de produccion y comision. Vive en su dominio y no en CitaRepository porque
 * cruza cuatro tablas (citas, servicios, pagos y peluqueros) y no le interesa a nadie mas.
 *
 * <p>Dos reglas de negocio estan metidas en el SQL a proposito, porque son la definicion
 * misma de "produccion":
 * <ul>
 *   <li>Cuentan las citas COMPLETADA, y si el negocio cobra por la aplicacion ademas tienen
 *       que estar PAGADO: se cuenta el dinero cobrado, no el prometido. Eso es lo que dice
 *       {@code exigeCobro}, que sale del modulo PAGOS. <b>Con los pagos apagados nada
 *       llegaria nunca a PAGADO y toda la produccion seria cero</b>, asi que ahi se cuenta
 *       lo realizado a secas. Es un caso real, no una hipotesis: una peluqueria que cobra
 *       en el local y no registra el cobro.
 *   <li>El importe sale de {@code precio_aplicado}, congelado al cerrar la cita. El
 *       COALESCE con {@code s.precio} es solo una red por si alguna fila antigua no lo
 *       tiene: sin el, una cita sin congelar sumaria cero y el total mentiria por lo bajo.
 * </ul>
 *
 * <p>El JOIN con pagos es LEFT justamente por lo anterior: con {@code exigeCobro} en falso
 * una cita sin pago tiene que contar igual. Una cita tiene como mucho un pago, asi que no
 * duplica filas.
 */
public interface ProduccionRepository extends Repository<Cita, Integer> {

    String PRODUCIDAS = """
            FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            LEFT JOIN pagos p ON p.cita_id = c.id_cita
            WHERE c.estado = 'COMPLETADA'
            AND (CAST(:exigeCobro AS BOOLEAN) = FALSE OR p.estado_pago = 'PAGADO')
            AND c.fecha_hora >= CAST(:desde AS TIMESTAMP)
            AND c.fecha_hora < CAST(:hasta AS TIMESTAMP)
            """;

    @Query(value = """
            SELECT COUNT(*),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)), 0),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)
                                * COALESCE(c.comision_porcentaje_aplicado, 0) / 100), 0)
            """ + PRODUCIDAS + """
            AND c.peluquero_id = :peluqueroId
            """, nativeQuery = true)
    List<Object[]> resumen(@Param("peluqueroId") Integer peluqueroId,
                           @Param("desde") LocalDateTime desde,
                           @Param("hasta") LocalDateTime hasta,
                           @Param("exigeCobro") boolean exigeCobro);

    @Query(value = """
            SELECT s.nombre,
                   COUNT(*),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)), 0),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)
                                * COALESCE(c.comision_porcentaje_aplicado, 0) / 100), 0)
            """ + PRODUCIDAS + """
            AND c.peluquero_id = :peluqueroId
            GROUP BY s.id_servicio, s.nombre
            ORDER BY 3 DESC, s.nombre
            """, nativeQuery = true)
    List<Object[]> porServicio(@Param("peluqueroId") Integer peluqueroId,
                               @Param("desde") LocalDateTime desde,
                               @Param("hasta") LocalDateTime hasta,
                               @Param("exigeCobro") boolean exigeCobro);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', c.fecha_hora), 'YYYY-MM'),
                   COUNT(*),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)), 0),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)
                                * COALESCE(c.comision_porcentaje_aplicado, 0) / 100), 0)
            """ + PRODUCIDAS + """
            AND c.peluquero_id = :peluqueroId
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> porMes(@Param("peluqueroId") Integer peluqueroId,
                          @Param("desde") LocalDateTime desde,
                          @Param("hasta") LocalDateTime hasta,
                          @Param("exigeCobro") boolean exigeCobro);

    /**
     * Trabajo hecho y todavia sin cobrar. No suma en la produccion, pero se devuelve
     * aparte porque es dinero pendiente: sin este numero, una cita completada que nadie
     * cobro desaparece de todas las pantallas.
     *
     * <p>No lleva {@code exigeCobro} porque con los pagos apagados este numero no existe:
     * no hay "sin cobrar" donde no se registra ningun cobro, y ahi esas citas ya estan
     * contadas en la produccion. Quien no lo llama es {@code ProduccionService}.
     */
    @Query(value = """
            SELECT COUNT(*),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)), 0)
            FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            LEFT JOIN pagos p ON p.cita_id = c.id_cita
            WHERE c.estado = 'COMPLETADA'
            AND (p.id_pago IS NULL OR p.estado_pago <> 'PAGADO')
            AND c.fecha_hora >= CAST(:desde AS TIMESTAMP)
            AND c.fecha_hora < CAST(:hasta AS TIMESTAMP)
            AND c.peluquero_id = :peluqueroId
            """, nativeQuery = true)
    List<Object[]> sinCobrar(@Param("peluqueroId") Integer peluqueroId,
                             @Param("desde") LocalDateTime desde,
                             @Param("hasta") LocalDateTime hasta);

    /**
     * Comparativa de toda la plantilla. Solo la ve el ADMIN.
     *
     * <p>No reutiliza {@code PRODUCIDAS} porque necesita un JOIN mas, el de peluqueros, para
     * poder devolver el nombre.
     */
    @Query(value = """
            SELECT pel.id_peluquero,
                   pel.nombre,
                   COUNT(*),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)), 0),
                   COALESCE(SUM(COALESCE(c.precio_aplicado, s.precio)
                                * COALESCE(c.comision_porcentaje_aplicado, 0) / 100), 0)
            FROM citas c
            JOIN servicios s ON c.servicio_id = s.id_servicio
            LEFT JOIN pagos p ON p.cita_id = c.id_cita
            JOIN peluqueros pel ON pel.id_peluquero = c.peluquero_id
            WHERE c.estado = 'COMPLETADA'
            AND (CAST(:exigeCobro AS BOOLEAN) = FALSE OR p.estado_pago = 'PAGADO')
            AND c.fecha_hora >= CAST(:desde AS TIMESTAMP)
            AND c.fecha_hora < CAST(:hasta AS TIMESTAMP)
            GROUP BY pel.id_peluquero, pel.nombre
            ORDER BY 4 DESC, pel.nombre
            """, nativeQuery = true)
    List<Object[]> comparativa(@Param("desde") LocalDateTime desde,
                               @Param("hasta") LocalDateTime hasta,
                               @Param("exigeCobro") boolean exigeCobro);
}
