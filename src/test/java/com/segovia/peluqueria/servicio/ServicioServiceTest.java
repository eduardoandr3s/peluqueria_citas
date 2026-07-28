package com.segovia.peluqueria.servicio;

import com.segovia.peluqueria.almacen.AlmacenEnMemoria;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.servicio.dto.ServicioRequestDTO;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.servicio.dto.ServicioUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioServiceTest {

    private ServicioRepository servicioRepository;
    private ServicioService servicioService;
    private AlmacenEnMemoria almacen;
    private AlmacenProperties almacenProperties;

    @BeforeEach
    void setUp() {
        servicioRepository = mock(ServicioRepository.class);
        almacen = new AlmacenEnMemoria();
        almacenProperties = new AlmacenProperties();
        servicioService = new ServicioService(servicioRepository, almacen,
                new ValidadorImagen(almacenProperties), almacenProperties);
    }

    private String bucket() {
        return almacenProperties.getBucketServicios();
    }

    /** JPEG minimo valido: solo importa la firma de los primeros bytes. */
    private static MockMultipartFile jpeg() {
        byte[] datos = new byte[64];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;
        return new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", datos);
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

    @Test
    void listarServicios_devuelveSoloActivos() {
        Servicio s1 = crearServicioBase();
        Servicio s2 = crearServicioBase();
        s2.setIdServicio(2);
        s2.setNombre("Tinte");

        when(servicioRepository.findByActivoTrue()).thenReturn(List.of(s1, s2));

        List<ServicioResponseDTO> resultado = servicioService.listarServicios();

        assertEquals(2, resultado.size());
        verify(servicioRepository).findByActivoTrue();
    }

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

        ServicioResponseDTO resultado = servicioService.crearServicio(request);

        assertEquals("Corte clasico", resultado.getNombre());
        assertEquals(new BigDecimal("15.00"), resultado.getPrecio());
        assertEquals(30, resultado.getDuracion());
        verify(servicioRepository).save(any(Servicio.class));
    }

    @Test
    void obtenerServicioPorId_exitoso() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        ServicioResponseDTO resultado = servicioService.obtenerServicioPorId(1);

        assertEquals("Corte clasico", resultado.getNombre());
    }

    @Test
    void obtenerServicioPorId_noExiste_lanzaExcepcion() {
        when(servicioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> servicioService.obtenerServicioPorId(99));
    }

    @Test
    void actualizarServicio_soloNombre() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        ServicioUpdateDTO request = new ServicioUpdateDTO();
        request.setNombre("Corte premium");

        ServicioResponseDTO resultado = servicioService.actualizarServicio(1, request);

        assertEquals("Corte premium", resultado.getNombre());
        assertEquals(new BigDecimal("15.00"), resultado.getPrecio());
        assertEquals(30, resultado.getDuracion());
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

        ServicioResponseDTO resultado = servicioService.actualizarServicio(1, request);

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

    @Test
    void servicioSinFoto_devuelveUrlImagenNula() {
        when(servicioRepository.findById(1)).thenReturn(Optional.of(crearServicioBase()));

        assertNull(servicioService.obtenerServicioPorId(1).getUrlImagen());
    }

    @Test
    void subirImagen_guardaLaClaveYDevuelveLaUrl() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        ServicioResponseDTO resultado = servicioService.subirImagen(1, jpeg());

        // En la fila queda la clave, no la URL: cambiar de bucket no migra datos.
        assertNotNull(servicio.getImagenClave());
        assertTrue(servicio.getImagenClave().startsWith("1/"));
        assertTrue(almacen.contiene(bucket(), servicio.getImagenClave()));
        assertEquals("image/jpeg", almacen.obtener(bucket(), servicio.getImagenClave()).contentType());
        // Y el cliente recibe una URL ya montada, nunca la clave.
        assertEquals(almacen.urlDeLectura(bucket(), servicio.getImagenClave()), resultado.getUrlImagen());
    }

    @Test
    void subirImagen_sustituyeYBorraLaAnterior() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        servicioService.subirImagen(1, jpeg());
        String claveAnterior = servicio.getImagenClave();
        servicioService.subirImagen(1, jpeg());

        assertNotEquals(claveAnterior, servicio.getImagenClave());
        // Sin esto cada sustitucion dejaria un objeto huerfano comiendo cuota.
        assertFalse(almacen.contiene(bucket(), claveAnterior));
        assertTrue(almacen.contiene(bucket(), servicio.getImagenClave()));
        assertEquals(1, almacen.total());
    }

    @Test
    void subirImagen_ficheroQueNoEsImagen_noTocaNada() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        MockMultipartFile falso = new MockMultipartFile("imagen", "foto.jpg", "image/jpeg",
                new byte[] { 0x4D, 0x5A, 0x00, 0x00 });

        assertThrows(IllegalArgumentException.class, () -> servicioService.subirImagen(1, falso));

        assertNull(servicio.getImagenClave());
        assertEquals(0, almacen.total());
        verify(servicioRepository, never()).save(any(Servicio.class));
    }

    @Test
    void subirImagen_servicioQueNoExiste_lanzaExcepcion() {
        when(servicioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicioService.subirImagen(99, jpeg()));
        assertEquals(0, almacen.total());
    }

    @Test
    void borrarImagen_quitaLaClaveYElObjeto() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));
        servicioService.subirImagen(1, jpeg());
        String clave = servicio.getImagenClave();

        ServicioResponseDTO resultado = servicioService.borrarImagen(1);

        assertNull(servicio.getImagenClave());
        assertNull(resultado.getUrlImagen());
        assertFalse(almacen.contiene(bucket(), clave));
    }

    @Test
    void borrarImagen_sinFoto_noFalla() {
        Servicio servicio = crearServicioBase();
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));

        assertNull(servicioService.borrarImagen(1).getUrlImagen());
        verify(servicioRepository, never()).save(any(Servicio.class));
    }
}
