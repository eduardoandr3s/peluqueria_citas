package com.segovia.peluqueria.estadistica;

import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.estadistica.dto.CitasPorEstadoDTO;
import com.segovia.peluqueria.estadistica.dto.EstadisticasResponseDTO;
import com.segovia.peluqueria.estadistica.dto.IngresosDTO;
import com.segovia.peluqueria.estadistica.dto.TopServicioDTO;
import com.segovia.peluqueria.pago.PagoRepository;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EstadisticasService {

    private final CitaRepository citaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;

    public EstadisticasService(CitaRepository citaRepository, PagoRepository pagoRepository, UsuarioRepository usuarioRepository) {
        this.citaRepository = citaRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public EstadisticasResponseDTO obtenerEstadisticas(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' debe ser anterior o igual a 'hasta'");
        }

        LocalDateTime desdeDateTime = desde.atStartOfDay();
        LocalDateTime hastaDateTime = hasta.plusDays(1).atStartOfDay();

        List<CitasPorEstadoDTO> citasPorEstado = mapearCitasPorEstado(
                citaRepository.contarCitasPorEstado(desdeDateTime, hastaDateTime));

        IngresosDTO ingresos = mapearIngresos(
                pagoRepository.sumIngresos(desdeDateTime, hastaDateTime),
                pagoRepository.ingresosPorMetodoPago(desdeDateTime, hastaDateTime));

        List<TopServicioDTO> topServicios = mapearTopServicios(
                citaRepository.topServicios(desdeDateTime, hastaDateTime));

        long nuevosClientes = usuarioRepository.countByFechaRegistroBetween(desde, hasta);

        return new EstadisticasResponseDTO(citasPorEstado, ingresos, topServicios, nuevosClientes);
    }

    private List<CitasPorEstadoDTO> mapearCitasPorEstado(List<Object[]> filas) {
        return filas.stream()
                .map(f -> new CitasPorEstadoDTO((String) f[0], ((Number) f[1]).longValue()))
                .collect(Collectors.toList());
    }

    private IngresosDTO mapearIngresos(BigDecimal total, List<Object[]> filas) {
        Map<String, BigDecimal> porMetodoPago = new LinkedHashMap<>();
        for (Object[] fila : filas) {
            porMetodoPago.put((String) fila[0], (BigDecimal) fila[1]);
        }
        return new IngresosDTO(total, porMetodoPago);
    }

    private List<TopServicioDTO> mapearTopServicios(List<Object[]> filas) {
        return filas.stream()
                .map(f -> new TopServicioDTO((String) f[0], ((Number) f[1]).longValue()))
                .collect(Collectors.toList());
    }
}
