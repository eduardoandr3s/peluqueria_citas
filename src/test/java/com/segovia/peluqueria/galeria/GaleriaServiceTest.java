package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.almacen.AlmacenEnMemoria;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoResponseDTO;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GaleriaServiceTest {

    private GaleriaFotoRepository galeriaFotoRepository;
    private GaleriaService galeriaService;
    private AlmacenEnMemoria almacen;
    private AlmacenProperties almacenProperties;

    @BeforeEach
    void setUp() {
        galeriaFotoRepository = mock(GaleriaFotoRepository.class);
        almacen = new AlmacenEnMemoria();
        almacenProperties = new AlmacenProperties();
        galeriaService = new GaleriaService(galeriaFotoRepository, almacen,
                new ValidadorImagen(almacenProperties), almacenProperties);
        when(galeriaFotoRepository.save(any(GaleriaFoto.class))).thenAnswer(i -> i.getArgument(0));
    }

    private String bucket() {
        return almacenProperties.getBucketGaleria();
    }

    /** JPEG minimo valido: solo importa la firma de los primeros bytes. */
    private static MockMultipartFile jpeg(String campo) {
        byte[] datos = new byte[64];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;
        return new MockMultipartFile(campo, campo + ".jpg", "image/jpeg", datos);
    }

    private static GaleriaFoto fotoBase() {
        GaleriaFoto foto = new GaleriaFoto();
        foto.setIdFoto(1);
        foto.setImagenClave("fotos/grande.jpg");
        foto.setMiniaturaClave("miniaturas/pequena.jpg");
        foto.setTitulo("Degradado");
        foto.setOrden(0);
        return foto;
    }

    @Test
    void subirFoto_guardaImagenYMiniaturaEnElBucketDeGaleria() {
        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), jpeg("miniatura"), "Degradado");

        assertEquals(2, almacen.total());
        assertEquals("Degradado", dto.getTitulo());
        assertNotNull(dto.getUrlImagen());
        assertNotEquals(dto.getUrlImagen(), dto.getUrlMiniatura());
        // El bucket de la galeria es publico: se sirve por URL de lectura, no firmada.
        assertTrue(dto.getUrlMiniatura().startsWith("https://almacen.test/" + bucket() + "/"));
    }

    @Test
    void subirFoto_laClaveLaGeneraElServidorYSeparaTamanos() {
        galeriaService.subirFoto(jpeg("imagen"), jpeg("miniatura"), null);

        ArgumentCaptor<GaleriaFoto> guardada = ArgumentCaptor.forClass(GaleriaFoto.class);
        verify(galeriaFotoRepository).save(guardada.capture());

        // El nombre del fichero que manda el cliente no se usa nunca como clave, y
        // los dos tamanos viven en carpetas distintas del bucket.
        assertTrue(guardada.getValue().getImagenClave().startsWith("fotos/"));
        assertTrue(guardada.getValue().getMiniaturaClave().startsWith("miniaturas/"));
        assertTrue(guardada.getValue().getImagenClave().endsWith(".jpg"));
    }

    @Test
    void subirFoto_sinMiniaturaCaeALaImagenGrande() {
        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), null, null);

        assertEquals(1, almacen.total());
        assertEquals(dto.getUrlImagen(), dto.getUrlMiniatura());
    }

    @Test
    void subirFoto_miniaturaVaciaSeTrataComoAusente() {
        MockMultipartFile vacia = new MockMultipartFile("miniatura", "m.jpg", "image/jpeg", new byte[0]);

        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), vacia, null);

        assertEquals(1, almacen.total());
        assertEquals(dto.getUrlImagen(), dto.getUrlMiniatura());
    }

    @Test
    void subirFoto_rechazaLoQueNoEsImagenAunqueLoDigaElContentType() {
        MockMultipartFile falsa = new MockMultipartFile("imagen", "virus.jpg", "image/jpeg",
                new byte[] { 0x4D, 0x5A, 0x00, 0x00 });

        assertThrows(IllegalArgumentException.class, () -> galeriaService.subirFoto(falsa, null, null));
        assertEquals(0, almacen.total());
    }

    @Test
    void subirFoto_rechazaUnaMiniaturaQueNoEsImagen() {
        MockMultipartFile falsa = new MockMultipartFile("miniatura", "m.jpg", "image/jpeg",
                new byte[] { 0x4D, 0x5A, 0x00, 0x00 });

        assertThrows(IllegalArgumentException.class, () -> galeriaService.subirFoto(jpeg("imagen"), falsa, null));
    }

    @Test
    void subirFoto_seColocaDetrasDeLaUltima() {
        GaleriaFoto ultima = fotoBase();
        ultima.setOrden(4);
        when(galeriaFotoRepository.findFirstByOrderByOrdenDescIdFotoDesc()).thenReturn(Optional.of(ultima));

        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), null, null);

        assertEquals(5, dto.getOrden());
    }

    @Test
    void subirFoto_laPrimeraEmpiezaEnCero() {
        when(galeriaFotoRepository.findFirstByOrderByOrdenDescIdFotoDesc()).thenReturn(Optional.empty());

        assertEquals(0, galeriaService.subirFoto(jpeg("imagen"), null, null).getOrden());
    }

    @Test
    void subirFoto_unTituloEnBlancoEsNoTenerTitulo() {
        assertNull(galeriaService.subirFoto(jpeg("imagen"), null, "   ").getTitulo());
    }

    @Test
    void listarFotos_devuelveElOrdenDelRepositorio() {
        GaleriaFoto primera = fotoBase();
        GaleriaFoto segunda = fotoBase();
        segunda.setIdFoto(2);
        segunda.setOrden(1);
        when(galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc()).thenReturn(List.of(primera, segunda));

        List<GaleriaFotoResponseDTO> fotos = galeriaService.listarFotos();

        assertEquals(2, fotos.size());
        assertEquals(1, fotos.get(0).getIdFoto());
        assertEquals(2, fotos.get(1).getIdFoto());
        verify(galeriaFotoRepository).findAllByOrderByOrdenAscIdFotoAsc();
    }

    @Test
    void actualizarFoto_cambiaTituloYOrden() {
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoBase()));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setTitulo("Corte con tijera");
        request.setOrden(3);

        GaleriaFotoResponseDTO dto = galeriaService.actualizarFoto(1, request);

        assertEquals("Corte con tijera", dto.getTitulo());
        assertEquals(3, dto.getOrden());
    }

    @Test
    void actualizarFoto_loQueNoVieneSeQuedaComoEstaba() {
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoBase()));

        GaleriaFotoResponseDTO dto = galeriaService.actualizarFoto(1, new GaleriaFotoUpdateDTO());

        assertEquals("Degradado", dto.getTitulo());
        assertEquals(0, dto.getOrden());
    }

    @Test
    void actualizarFoto_noAceptaOrdenNegativo() {
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoBase()));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setOrden(-3);

        assertEquals(0, galeriaService.actualizarFoto(1, request).getOrden());
    }

    @Test
    void actualizarFoto_idInexistente() {
        when(galeriaFotoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> galeriaService.actualizarFoto(99, new GaleriaFotoUpdateDTO()));
    }

    @Test
    void eliminarFoto_borraLosDosObjetos() {
        GaleriaFoto foto = fotoBase();
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(foto));
        almacen.guardar(bucket(), foto.getImagenClave(), new byte[] { 1 }, "image/jpeg");
        almacen.guardar(bucket(), foto.getMiniaturaClave(), new byte[] { 1 }, "image/jpeg");

        galeriaService.eliminarFoto(1);

        // Si solo se borrara la grande, la miniatura seguiria ocupando cuota sin
        // que ninguna fila la referencie.
        assertEquals(0, almacen.total());
        verify(galeriaFotoRepository).delete(foto);
    }

    @Test
    void eliminarFoto_sinMiniaturaNoFalla() {
        GaleriaFoto foto = fotoBase();
        foto.setMiniaturaClave(null);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(foto));
        almacen.guardar(bucket(), foto.getImagenClave(), new byte[] { 1 }, "image/jpeg");

        galeriaService.eliminarFoto(1);

        assertEquals(0, almacen.total());
    }

    @Test
    void eliminarFoto_idInexistente() {
        when(galeriaFotoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> galeriaService.eliminarFoto(99));
        verify(galeriaFotoRepository, never()).delete(any());
    }
}
