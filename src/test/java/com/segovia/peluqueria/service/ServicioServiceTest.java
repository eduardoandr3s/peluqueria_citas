package com.segovia.peluqueria.service;

import com.segovia.peluqueria.dto.ServicioRequestDTO;
import com.segovia.peluqueria.dto.ServicioUpdateDTO;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioServiceTest {

    private ServicioRepository servicioRepository;
    private ServicioService servicioService;

    @BeforeEach
    void setUp() {
        servicioRepository = mock(ServicioRepository.class);
        servicioService = new ServicioService(servicioRepository);
    }

    private Servicio crearServicioBase() {
        Servicio servicio = new Servicio();
        servicio.setIdServicio(1);
        servicio.setNombre("Corte clasico");
        servicio.setDescripcion("Corte de cabello clasico");
        servicio.setPrecio(new BigDecimal("15.00"));
        servicio.setDuracion(30);
        servicio.setActivo(true);
        return servicio;
    }

    // --- listarServicios ---

    @Test
    void listarServicios_devuelveSoloActivos() {
        Servicio s1 = crearServicioBase();
        Servicio s2 = crearServicioBase();
        s2.setIdServicio(2);
        s2.setNombre("Tinte");

        when(servicioRepository.findByActivoTrue()).thenReturn(List.of(s1, s2));

        List<Servicio> resultado = servicioService.listarServicios();

        assertEquals(2, resultado.size());
        verify(servicioRepository).findByActivoTrue();
    }

    // --- crearServicio ---

    @Test
    void crearServicio_exitoso() {
        ServicioRequestDTO request = new ServicioRequestDTO();
        request.setNombre("Corte clasico");
        request.setDescripcion("Corte de cabello clasico");
        request.setPrecio(new BigDecimal("15.00"));
        request.setDuracion(30);

        when(servicioRepository.save(any(Servicio.class))).thenAnswer(invocation -> {
            Servicio s = invocation.getArgument(0);
            s.setIdServicio(1);
            return s;
        });

        Servicio resultado = servicioService.crearServicio(request);

        assertEquals("Corte clasico", resultado.getNombre());
        assertEquals(new BigDecimal("15.00"), resultado.getPrecio());
        assertEquals(30, resultado.getDuracion());
        verify(servicioRepository).save(any(Servicio.class));
    }

    // --- obtenerServicioPorId ---

    @Test
    void obtenerServicioPorId_exitoso() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        Servicio resultado = servicioService.obtenerServicioPorId(1);

        assertEquals("Corte clasico", resultado.getNombre());
    }

    @Test
    void obtenerServicioPorId_noExiste_lanzaExcepcion() {
        when(servicioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> servicioService.obtenerServicioPorId(99));
    }

    // --- actualizarServicio ---

    @Test
    void actualizarServicio_soloNombre() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        ServicioUpdateDTO request = new ServicioUpdateDTO();
        request.setNombre("Corte premium");

        Servicio resultado = servicioService.actualizarServicio(1, request);

        assertEquals("Corte premium", resultado.getNombre());
        assertEquals(new BigDecimal("15.00"), resultado.getPrecio()); // no cambio
        assertEquals(30, resultado.getDuracion()); // no cambio
    }

    @Test
    void actualizarServicio_todosLosCampos() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        ServicioUpdateDTO request = new ServicioUpdateDTO();
        request.setNombre("Tinte completo");
        request.setDescripcion("Tinte de cabello completo");
        request.setPrecio(new BigDecimal("45.00"));
        request.setDuracion(90);

        Servicio resultado = servicioService.actualizarServicio(1, request);

        assertEquals("Tinte completo", resultado.getNombre());
        assertEquals("Tinte de cabello completo", resultado.getDescripcion());
        assertEquals(new BigDecimal("45.00"), resultado.getPrecio());
        assertEquals(90, resultado.getDuracion());
    }

    @Test
    void actualizarServicio_noExiste_lanzaExcepcion() {
        when(servicioRepository.findById(99)).thenReturn(Optional.empty());

        ServicioUpdateDTO request = new ServicioUpdateDTO();

        assertThrows(ResourceNotFoundException.class,
                () -> servicioService.actualizarServicio(99, request));
    }

    // --- eliminarServicio ---

    @Test
    void eliminarServicio_marcaInactivo() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        servicioService.eliminarServicio(1);

        assertFalse(servicio.getActivo());
        verify(servicioRepository).save(servicio);
    }

    @Test
    void eliminarServicio_noExiste_lanzaExcepcion() {
        when(servicioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> servicioService.eliminarServicio(99));
    }
}
