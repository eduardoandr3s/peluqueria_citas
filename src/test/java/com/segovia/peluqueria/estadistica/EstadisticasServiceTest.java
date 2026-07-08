package com.segovia.peluqueria.estadistica;

import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.estadistica.dto.CitasPorEstadoDTO;
import com.segovia.peluqueria.estadistica.dto.EstadisticasResponseDTO;
import com.segovia.peluqueria.estadistica.dto.IngresosDTO;
import com.segovia.peluqueria.estadistica.dto.TopServicioDTO;
import com.segovia.peluqueria.pago.PagoRepository;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EstadisticasServiceTest {

    private CitaRepository citaRepository;
    private PagoRepository pagoRepository;
    private UsuarioRepository usuarioRepository;
    private EstadisticasService estadisticasService;

    @BeforeEach
    void setUp() {
        citaRepository = mock(CitaRepository.class);
        pagoRepository = mock(PagoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        estadisticasService = new EstadisticasService(citaRepository, pagoRepository, usuarioRepository);
    }

    @Test
    void obtenerEstadisticas_conDatos_retornaDTOCompleto() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);

        when(citaRepository.contarCitasPorEstado(any(), any())).thenReturn(List.of(
                new Object[]{"CONFIRMADA", 15L},
                new Object[]{"PENDIENTE", 5L},
                new Object[]{"ANULADA", 2L}
        ));
        when(pagoRepository.sumIngresos(any(), any())).thenReturn(new BigDecimal("450.00"));
        when(pagoRepository.ingresosPorMetodoPago(any(), any())).thenReturn(List.of(
                new Object[]{"TARJETA", new BigDecimal("300.00")},
                new Object[]{"EFECTIVO", new BigDecimal("150.00")}
        ));
        when(citaRepository.topServicios(any(), any())).thenReturn(List.of(
                new Object[]{"Corte", 20L},
                new Object[]{"Tinte", 10L}
        ));
        when(usuarioRepository.countByFechaRegistroBetween(desde, hasta)).thenReturn(8L);

        EstadisticasResponseDTO result = estadisticasService.obtenerEstadisticas(desde, hasta);

        assertNotNull(result);
        assertEquals(3, result.getCitasPorEstado().size());
        assertEquals(15L, result.getCitasPorEstado().stream()
                .filter(c -> c.getEstado().equals("CONFIRMADA")).findFirst().get().getTotal());
        assertEquals(new BigDecimal("450.00"), result.getIngresos().getTotal());
        assertEquals(2, result.getIngresos().getPorMetodoPago().size());
        assertEquals(new BigDecimal("300.00"), result.getIngresos().getPorMetodoPago().get("TARJETA"));
        assertEquals(2, result.getTopServicios().size());
        assertEquals("Corte", result.getTopServicios().get(0).getNombre());
        assertEquals(8L, result.getNuevosClientes());
    }

    @Test
    void obtenerEstadisticas_sinDatos_devuelveValoresVacios() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);

        when(citaRepository.contarCitasPorEstado(any(), any())).thenReturn(List.of());
        when(pagoRepository.sumIngresos(any(), any())).thenReturn(BigDecimal.ZERO);
        when(pagoRepository.ingresosPorMetodoPago(any(), any())).thenReturn(List.of());
        when(citaRepository.topServicios(any(), any())).thenReturn(List.of());
        when(usuarioRepository.countByFechaRegistroBetween(desde, hasta)).thenReturn(0L);

        EstadisticasResponseDTO result = estadisticasService.obtenerEstadisticas(desde, hasta);

        assertNotNull(result);
        assertTrue(result.getCitasPorEstado().isEmpty());
        assertEquals(BigDecimal.ZERO, result.getIngresos().getTotal());
        assertTrue(result.getIngresos().getPorMetodoPago().isEmpty());
        assertTrue(result.getTopServicios().isEmpty());
        assertEquals(0L, result.getNuevosClientes());
    }

    @Test
    void obtenerEstadisticas_fechaDesdeInvalida_lanzaExcepcion() {
        LocalDate desde = LocalDate.of(2026, 6, 30);
        LocalDate hasta = LocalDate.of(2026, 6, 1);

        assertThrows(IllegalArgumentException.class,
                () -> estadisticasService.obtenerEstadisticas(desde, hasta));
    }
}
