package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.peluquero.dto.PeluqueroRequestDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeluqueroServiceTest {

    private PeluqueroRepository peluqueroRepository;
    private PeluqueroService peluqueroService;

    @BeforeEach
    void setUp() {
        peluqueroRepository = mock(PeluqueroRepository.class);
        peluqueroService = new PeluqueroService(peluqueroRepository);
    }

    private Peluquero crearPeluqueroBase() {
        Peluquero p = new Peluquero();
        p.setIdPeluquero(1);
        p.setNombre("Lalo");
        p.setActivo(true);
        return p;
    }

    @Test
    void listarActivos_devuelveSoloActivos() {
        Peluquero p1 = crearPeluqueroBase();
        Peluquero p2 = crearPeluqueroBase();
        p2.setIdPeluquero(2);
        p2.setNombre("Pepe");

        when(peluqueroRepository.findByActivoTrue()).thenReturn(List.of(p1, p2));

        List<PeluqueroResponseDTO> resultado = peluqueroService.listarActivos();

        assertEquals(2, resultado.size());
        verify(peluqueroRepository).findByActivoTrue();
    }

    @Test
    void crear_exitoso() {
        PeluqueroRequestDTO request = new PeluqueroRequestDTO();
        request.setNombre("Lalo");

        when(peluqueroRepository.save(any(Peluquero.class))).thenAnswer(invocation -> {
            Peluquero p = invocation.getArgument(0);
            p.setIdPeluquero(1);
            return p;
        });

        PeluqueroResponseDTO resultado = peluqueroService.crear(request);

        assertEquals("Lalo", resultado.getNombre());
        assertTrue(resultado.getActivo());
        verify(peluqueroRepository).save(any(Peluquero.class));
    }

    @Test
    void obtenerPorId_exitoso() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));

        PeluqueroResponseDTO resultado = peluqueroService.obtenerPorId(1);

        assertEquals("Lalo", resultado.getNombre());
    }

    @Test
    void obtenerPorId_noExiste_lanzaExcepcion() {
        when(peluqueroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> peluqueroService.obtenerPorId(99));
    }

    @Test
    void actualizar_exitoso() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        when(peluqueroRepository.save(any(Peluquero.class))).thenAnswer(i -> i.getArgument(0));

        PeluqueroUpdateDTO request = new PeluqueroUpdateDTO();
        request.setNombre("Lalo Updated");

        PeluqueroResponseDTO resultado = peluqueroService.actualizar(1, request);

        assertEquals("Lalo Updated", resultado.getNombre());
    }

    @Test
    void eliminar_marcaInactivo() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        when(peluqueroRepository.save(any(Peluquero.class))).thenAnswer(i -> i.getArgument(0));

        peluqueroService.eliminar(1);

        assertFalse(p.getActivo());
        verify(peluqueroRepository).save(p);
    }

    @Test
    void eliminar_noExiste_lanzaExcepcion() {
        when(peluqueroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> peluqueroService.eliminar(99));
    }
}
