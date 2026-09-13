package com.segovia.peluqueria.produccion;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.modulo.Modulo;
import com.segovia.peluqueria.modulo.ModuloDesactivadoException;
import com.segovia.peluqueria.modulo.ModuloService;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.peluquero.PeluqueroRepository;
import com.segovia.peluqueria.produccion.dto.ProduccionPeluqueroDTO;
import com.segovia.peluqueria.produccion.dto.ProduccionResponseDTO;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProduccionServiceTest {

    private static final String EMAIL = "lalo@test.com";
    private static final LocalDate DESDE = LocalDate.of(2026, 8, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 8, 31);

    private ProduccionRepository produccionRepository;
    private PeluqueroRepository peluqueroRepository;
    private UsuarioRepository usuarioRepository;
    private ModuloService moduloService;
    private ProduccionService produccionService;

    @BeforeEach
    void setUp() {
        produccionRepository = mock(ProduccionRepository.class);
        // Todos los modulos encendidos: es como nace un negocio. Los tests que apagan
        // alguno lo dicen.
        moduloService = mock(ModuloService.class);
        when(moduloService.estaActivo(any())).thenReturn(true);
        peluqueroRepository = mock(PeluqueroRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        // Por defecto, sin datos: los tests que los necesitan los stubbean.
        when(produccionRepository.resumen(any(), any(), any(), anyBoolean())).thenReturn(List.of());
        when(produccionRepository.sinCobrar(any(), any(), any())).thenReturn(List.of());
        when(produccionRepository.porServicio(any(), any(), any(), anyBoolean())).thenReturn(List.of());
        when(produccionRepository.porMes(any(), any(), any(), anyBoolean())).thenReturn(List.of());
        produccionService = new ProduccionService(produccionRepository, peluqueroRepository, usuarioRepository,
                moduloService);
    }

    private Usuario autenticar() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(7);
        usuario.setEmail(EMAIL);
        usuario.setRol(Rol.PELUQUERO);
        usuario.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private Peluquero ficha() {
        Peluquero peluquero = new Peluquero();
        peluquero.setIdPeluquero(1);
        peluquero.setNombre("Lalo");
        peluquero.setActivo(true);
        peluquero.setComisionPorcentaje(new BigDecimal("20.00"));
        return peluquero;
    }

    @Test
    void produccionPropia_mapeaResumenDesglosesYPendiente() {
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));
        when(produccionRepository.resumen(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{12L, new BigDecimal("300.00"), new BigDecimal("60.00")}));
        when(produccionRepository.sinCobrar(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, new BigDecimal("30.00")}));
        when(produccionRepository.porServicio(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{"Corte", 10L, new BigDecimal("150.00"), new BigDecimal("30.00")}));
        when(produccionRepository.porMes(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{"2026-08", 12L, new BigDecimal("300.00"), new BigDecimal("60.00")}));

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertEquals(1, resultado.getIdPeluquero());
        assertEquals("Lalo", resultado.getNombre());
        assertEquals(12, resultado.getServiciosRealizados());
        assertEquals(new BigDecimal("300.00"), resultado.getImporteVendido());
        assertEquals(new BigDecimal("60.00"), resultado.getComision());
        // El trabajo hecho y no cobrado va aparte y NO suma en lo vendido.
        assertEquals(2, resultado.getServiciosSinCobrar());
        assertEquals(new BigDecimal("30.00"), resultado.getImporteSinCobrar());
        assertEquals("Corte", resultado.getPorServicio().get(0).getEtiqueta());
        assertEquals("2026-08", resultado.getPorMes().get(0).getEtiqueta());
    }

    @Test
    void produccionPropia_sinDatosEnElRango_devuelveCerosYNoRevienta() {
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertEquals(0, resultado.getServiciosRealizados());
        assertEquals(0, resultado.getImporteVendido().compareTo(BigDecimal.ZERO));
        assertTrue(resultado.getPorServicio().isEmpty());
    }

    @Test
    void produccionPropia_cuentaSinFichaVinculada_lanzaResourceNotFound() {
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> produccionService.produccionPropia(EMAIL, DESDE, HASTA));
        assertTrue(ex.getMessage().contains("no esta vinculada"));
    }

    @Test
    void produccionPropia_usuarioQueNoExiste_lanzaResourceNotFound() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> produccionService.produccionPropia(EMAIL, DESDE, HASTA));
    }

    @Test
    void produccionDePeluquero_fichaQueNoExiste_lanzaResourceNotFound() {
        when(peluqueroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> produccionService.produccionDePeluquero(99, DESDE, HASTA));
    }

    @Test
    void rangoInvertido_lanzaExcepcion() {
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(ficha()));

        assertThrows(IllegalArgumentException.class,
                () -> produccionService.produccionDePeluquero(1, HASTA, DESDE));
    }

    @Test
    void rangoDeMasDeDosAnos_lanzaExcepcion() {
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(ficha()));

        assertThrows(IllegalArgumentException.class,
                () -> produccionService.produccionDePeluquero(1, DESDE, DESDE.plusYears(3)));
    }

    @Test
    void importes_seRedondeanADosDecimales() {
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));
        // Lo que devuelve Postgres al multiplicar importe * porcentaje / 100 no viene con dos
        // decimales: sin redondear, la pantalla mostraria 12,749999 y las lineas no cuadrarian.
        when(produccionRepository.resumen(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, new BigDecimal("63.7499"), new BigDecimal("12.749999")}));

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertEquals(new BigDecimal("63.75"), resultado.getImporteVendido());
        assertEquals(new BigDecimal("12.75"), resultado.getComision());
    }

    @Test
    void comparativa_mapeaUnaFilaPorPeluquero() {
        when(produccionRepository.comparativa(any(), any(), anyBoolean())).thenReturn(List.<Object[]>of(
                new Object[]{1, "Lalo", 12L, new BigDecimal("300.00"), new BigDecimal("60.00")},
                new Object[]{2, "Pepe", 5L, new BigDecimal("100.00"), new BigDecimal("10.00")}));

        List<ProduccionPeluqueroDTO> resultado = produccionService.comparativa(DESDE, HASTA);

        assertEquals(2, resultado.size());
        assertEquals("Lalo", resultado.get(0).getNombre());
        assertEquals(new BigDecimal("300.00"), resultado.get(0).getImporteVendido());
        assertEquals(5, resultado.get(1).getServiciosRealizados());
    }

    // ---------- los modulos ----------

    @Test
    void conLosPagosEncendidos_produccionExigeQueEsteCobrada() {
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertTrue(resultado.isExigeCobro());
        verify(produccionRepository).resumen(any(), any(), any(), eq(true));
    }

    @Test
    void conLosPagosApagados_cuentaLoRealizadoAunqueNoEsteCobrado() {
        // Sin esto, apagar el modulo de pagos pondria toda la produccion a cero: nada
        // llegaria nunca a PAGADO. Paso una vez de verdad y costo media sesion entenderlo;
        // con un check seria un clic.
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));
        when(moduloService.estaActivo(Modulo.PAGOS)).thenReturn(false);
        when(produccionRepository.resumen(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{5L, new BigDecimal("100.00"), new BigDecimal("20.00")}));

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertFalse(resultado.isExigeCobro());
        assertEquals(5, resultado.getServiciosRealizados());
        verify(produccionRepository).resumen(any(), any(), any(), eq(false));
    }

    @Test
    void conLosPagosApagados_noHayTrabajoSinCobrar() {
        // Esas citas ya estan contadas arriba: repetirlas abajo seria contar dos veces el
        // mismo trabajo.
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));
        when(moduloService.estaActivo(Modulo.PAGOS)).thenReturn(false);

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertEquals(0, resultado.getServiciosSinCobrar());
        assertEquals(0, resultado.getImporteSinCobrar().compareTo(BigDecimal.ZERO));
        verify(produccionRepository, never()).sinCobrar(any(), any(), any());
    }

    @Test
    void conLasComisionesApagadas_laComisionNoViajaNiEnElTotalNiEnLasLineas() {
        // Null y no cero: cero seria "trabaja al 0 %" y esto es "aqui no se comisiona".
        autenticar();
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(ficha()));
        when(moduloService.estaActivo(Modulo.COMISIONES)).thenReturn(false);
        when(produccionRepository.resumen(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{12L, new BigDecimal("300.00"), new BigDecimal("60.00")}));
        when(produccionRepository.porServicio(any(), any(), any(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{"Corte", 10L, new BigDecimal("150.00"), new BigDecimal("30.00")}));

        ProduccionResponseDTO resultado = produccionService.produccionPropia(EMAIL, DESDE, HASTA);

        assertNull(resultado.getComision());
        assertNull(resultado.getPorServicio().get(0).getComision());
        // El importe vendido no se toca: lo que se apaga es la comision, no la venta.
        assertEquals(new BigDecimal("300.00"), resultado.getImporteVendido());
    }

    @Test
    void conLasComisionesApagadas_laComparativaTampocoLaLleva() {
        when(moduloService.estaActivo(Modulo.COMISIONES)).thenReturn(false);
        when(produccionRepository.comparativa(any(), any(), anyBoolean())).thenReturn(List.<Object[]>of(
                new Object[]{1, "Lalo", 10L, new BigDecimal("200.00"), new BigDecimal("40.00")}));

        List<ProduccionPeluqueroDTO> comparativa = produccionService.comparativa(DESDE, HASTA);

        assertNull(comparativa.get(0).getComision());
        assertEquals(new BigDecimal("200.00"), comparativa.get(0).getImporteVendido());
    }

    // ---- El modulo de produccion ----

    @Test
    void sinModuloNoHayPRODUCCIONNIPARAUNADMIN() {
        // Sin pagos y sin comisiones dentro no queda mas que un recuento de citas hechas:
        // este modulo existe para poder quitar la pantalla entera en vez de dejarla vacia.
        when(moduloService.estaActivo(Modulo.PRODUCCION)).thenReturn(false);
        doThrow(new ModuloDesactivadoException(Modulo.PRODUCCION))
                .when(moduloService).exigir(Modulo.PRODUCCION);

        assertThrows(ModuloDesactivadoException.class,
                () -> produccionService.produccionPropia(EMAIL, DESDE, HASTA));
        assertThrows(ModuloDesactivadoException.class,
                () -> produccionService.comparativa(DESDE, HASTA));
        verify(produccionRepository, never()).resumen(any(), any(), any(), anyBoolean());
    }
}
