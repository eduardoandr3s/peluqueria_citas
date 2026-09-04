package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.peluquero.dto.PeluqueroGestionDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroRequestDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.peluquero.dto.ComisionServicioDTO;
import com.segovia.peluqueria.peluquero.dto.ComisionesUpdateDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroUpdateDTO;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeluqueroServiceTest {

    private PeluqueroRepository peluqueroRepository;
    private ComisionServicioRepository comisionRepository;
    private UsuarioRepository usuarioRepository;
    private ServicioRepository servicioRepository;
    private PeluqueroCvService cvService;
    private PeluqueroService peluqueroService;

    @BeforeEach
    void setUp() {
        peluqueroRepository = mock(PeluqueroRepository.class);
        comisionRepository = mock(ComisionServicioRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        servicioRepository = mock(ServicioRepository.class);
        // Solo se le pide la URL de la foto, y en estos tests ninguna ficha tiene: el CV se
        // prueba en PeluqueroCvServiceTest.
        cvService = mock(PeluqueroCvService.class);
        // Sin excepciones de comision salvo donde el test las ponga.
        when(comisionRepository.findByPeluqueroIdPeluquero(anyInt())).thenReturn(List.of());
        peluqueroService = new PeluqueroService(peluqueroRepository, comisionRepository,
                usuarioRepository, servicioRepository, cvService);
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

        PeluqueroGestionDTO resultado = peluqueroService.actualizar(1, request);

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

    // ----------------------------------------------------------------------------------
    // Vinculo con la cuenta y comisiones
    // ----------------------------------------------------------------------------------

    private Usuario cuenta(Integer id, Rol rol, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setNombre("Lalo");
        usuario.setEmail("lalo@test.com");
        usuario.setRol(rol);
        usuario.setActivo(activo);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private Servicio servicio(Integer id, String nombre) {
        Servicio servicio = new Servicio();
        servicio.setIdServicio(id);
        servicio.setNombre(nombre);
        when(servicioRepository.findById(id)).thenReturn(Optional.of(servicio));
        return servicio;
    }

    private ComisionServicioDTO fila(Integer servicioId, String porcentaje) {
        ComisionServicioDTO dto = new ComisionServicioDTO();
        dto.setServicioId(servicioId);
        dto.setPorcentaje(new BigDecimal(porcentaje));
        return dto;
    }

    private ComisionesUpdateDTO comisiones(ComisionServicioDTO... filas) {
        ComisionesUpdateDTO dto = new ComisionesUpdateDTO();
        dto.setComisiones(List.of(filas));
        return dto;
    }

    @Test
    void actualizar_vinculaCuentaConRolPeluquero() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        when(peluqueroRepository.save(any(Peluquero.class))).thenAnswer(i -> i.getArgument(0));
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.empty());
        cuenta(7, Rol.PELUQUERO, true);

        PeluqueroUpdateDTO request = new PeluqueroUpdateDTO();
        request.setUsuarioId(7);
        request.setComisionPorcentaje(new BigDecimal("30.00"));

        PeluqueroGestionDTO resultado = peluqueroService.actualizar(1, request);

        assertEquals(7, resultado.getUsuarioId());
        assertEquals("lalo@test.com", resultado.getUsuarioEmail());
        assertEquals(new BigDecimal("30.00"), resultado.getComisionPorcentaje());
    }

    @Test
    void actualizar_vincularCuentaConRolUser_lanzaExcepcion() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        cuenta(7, Rol.USER, true);

        PeluqueroUpdateDTO request = new PeluqueroUpdateDTO();
        request.setUsuarioId(7);

        // Vincular a un USER dejaria una ficha que parece operativa y cuyo dueno no ve ni
        // una cita, porque el rol es lo que abre las pantallas.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> peluqueroService.actualizar(1, request));
        assertTrue(ex.getMessage().contains("rol USER"));
        verify(peluqueroRepository, never()).save(any(Peluquero.class));
    }

    @Test
    void actualizar_vincularCuentaDesactivada_lanzaExcepcion() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        cuenta(7, Rol.PELUQUERO, false);

        PeluqueroUpdateDTO request = new PeluqueroUpdateDTO();
        request.setUsuarioId(7);

        assertThrows(IllegalArgumentException.class, () -> peluqueroService.actualizar(1, request));
    }

    @Test
    void actualizar_vincularCuentaYaUsadaPorOtraFicha_lanzaExcepcion() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        cuenta(7, Rol.PELUQUERO, true);

        Peluquero otra = crearPeluqueroBase();
        otra.setIdPeluquero(2);
        otra.setNombre("Pepe");
        when(peluqueroRepository.findByUsuarioIdUsuario(7)).thenReturn(Optional.of(otra));

        PeluqueroUpdateDTO request = new PeluqueroUpdateDTO();
        request.setUsuarioId(7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> peluqueroService.actualizar(1, request));
        assertTrue(ex.getMessage().contains("Pepe"));
    }

    @Test
    void actualizar_desvincularCuenta() {
        Peluquero p = crearPeluqueroBase();
        p.setUsuario(cuenta(7, Rol.PELUQUERO, true));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        when(peluqueroRepository.save(any(Peluquero.class))).thenAnswer(i -> i.getArgument(0));

        PeluqueroUpdateDTO request = new PeluqueroUpdateDTO();
        request.setDesvincularUsuario(true);

        PeluqueroGestionDTO resultado = peluqueroService.actualizar(1, request);

        assertNull(resultado.getUsuarioId());
        assertNull(p.getUsuario());
    }

    @Test
    void porcentajeAplicable_conExcepcionDeServicio_ganaLaExcepcion() {
        Peluquero p = crearPeluqueroBase();
        p.setComisionPorcentaje(new BigDecimal("10.00"));
        ComisionServicio excepcion = new ComisionServicio();
        excepcion.setPeluquero(p);
        excepcion.setServicio(servicio(3, "Tinte"));
        excepcion.setPorcentaje(new BigDecimal("15.00"));
        when(comisionRepository.findByPeluqueroIdPeluqueroAndServicioIdServicio(1, 3))
                .thenReturn(Optional.of(excepcion));

        assertEquals(new BigDecimal("15.00"), peluqueroService.porcentajeAplicable(1, 3));
        // No hace falta ni leer la ficha si hay excepcion.
        verify(peluqueroRepository, never()).findById(1);
    }

    @Test
    void porcentajeAplicable_sinExcepcion_usaElDeLaFicha() {
        Peluquero p = crearPeluqueroBase();
        p.setComisionPorcentaje(new BigDecimal("10.00"));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        when(comisionRepository.findByPeluqueroIdPeluqueroAndServicioIdServicio(1, 3))
                .thenReturn(Optional.empty());

        assertEquals(new BigDecimal("10.00"), peluqueroService.porcentajeAplicable(1, 3));
    }

    @Test
    void porcentajeAplicable_fichaQueNoExiste_devuelveCero() {
        when(comisionRepository.findByPeluqueroIdPeluqueroAndServicioIdServicio(99, 3))
                .thenReturn(Optional.empty());
        when(peluqueroRepository.findById(99)).thenReturn(Optional.empty());

        assertEquals(BigDecimal.ZERO, peluqueroService.porcentajeAplicable(99, 3));
    }

    @Test
    void reemplazarComisiones_borraLasAnterioresYGuardaLasNuevas() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));

        ComisionServicio anterior = new ComisionServicio();
        anterior.setPeluquero(p);
        anterior.setServicio(servicio(9, "Peinado"));
        anterior.setPorcentaje(new BigDecimal("5.00"));
        // La primera llamada devuelve lo que habia (para borrarlo) y la segunda, lo guardado.
        ComisionServicio guardada = new ComisionServicio();
        guardada.setPeluquero(p);
        guardada.setServicio(servicio(3, "Tinte"));
        guardada.setPorcentaje(new BigDecimal("15.00"));
        when(comisionRepository.findByPeluqueroIdPeluquero(1))
                .thenReturn(List.of(anterior), List.of(guardada));

        List<ComisionServicioDTO> resultado =
                peluqueroService.reemplazarComisiones(1, comisiones(fila(3, "15.00")));

        verify(comisionRepository).deleteAll(List.of(anterior));
        verify(comisionRepository).save(any(ComisionServicio.class));
        assertEquals(1, resultado.size());
        assertEquals("Tinte", resultado.get(0).getServicioNombre());
    }

    @Test
    void reemplazarComisiones_servicioRepetido_lanzaExcepcion() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        servicio(3, "Tinte");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> peluqueroService.reemplazarComisiones(1, comisiones(fila(3, "15.00"), fila(3, "20.00"))));
        assertTrue(ex.getMessage().contains("dos veces"));
    }

    @Test
    void reemplazarComisiones_servicioQueNoExiste_lanzaExcepcion() {
        Peluquero p = crearPeluqueroBase();
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(p));
        when(servicioRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> peluqueroService.reemplazarComisiones(1, comisiones(fila(404, "10.00"))));
    }

    @Test
    void listarParaGestion_incluyeLasFichasInactivas() {
        Peluquero activo = crearPeluqueroBase();
        Peluquero inactivo = crearPeluqueroBase();
        inactivo.setIdPeluquero(2);
        inactivo.setNombre("Pepe");
        inactivo.setActivo(false);
        when(peluqueroRepository.findAll()).thenReturn(List.of(inactivo, activo));

        List<PeluqueroGestionDTO> resultado = peluqueroService.listarParaGestion();

        // Ordenado por id, y con la inactiva dentro: es la que hay que poder reactivar.
        assertEquals(2, resultado.size());
        assertEquals(1, resultado.get(0).getIdPeluquero());
        assertFalse(resultado.get(1).getActivo());
    }
}
