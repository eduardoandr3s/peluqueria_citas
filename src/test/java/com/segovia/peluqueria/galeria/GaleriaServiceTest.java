package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.almacen.AlmacenEnMemoria;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoResponseDTO;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoUpdateDTO;
import com.segovia.peluqueria.permiso.Permiso;
import com.segovia.peluqueria.permiso.PermisoService;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GaleriaServiceTest {

    private static final String EMAIL_ADMIN = "admin@peluqueria.com";
    private static final String EMAIL_ANA = "ana@peluqueria.com";
    private static final String EMAIL_LUIS = "luis@peluqueria.com";

    private GaleriaFotoRepository galeriaFotoRepository;
    private GaleriaService galeriaService;
    private AlmacenEnMemoria almacen;
    private AlmacenProperties almacenProperties;
    private UsuarioRepository usuarioRepository;
    private PermisoService permisoService;

    private Usuario admin;
    private Usuario ana;
    private Usuario luis;

    @BeforeEach
    void setUp() {
        galeriaFotoRepository = mock(GaleriaFotoRepository.class);
        almacen = new AlmacenEnMemoria();
        almacenProperties = new AlmacenProperties();
        usuarioRepository = mock(UsuarioRepository.class);
        permisoService = mock(PermisoService.class);
        galeriaService = new GaleriaService(galeriaFotoRepository, almacen,
                new ValidadorImagen(almacenProperties), almacenProperties, usuarioRepository, permisoService);
        when(galeriaFotoRepository.save(any(GaleriaFoto.class))).thenAnswer(i -> i.getArgument(0));

        admin = usuario(1, "Lalo", Rol.ADMIN, EMAIL_ADMIN);
        ana = usuario(2, "Ana", Rol.PELUQUERO, EMAIL_ANA);
        luis = usuario(3, "Luis", Rol.PELUQUERO, EMAIL_LUIS);
        // Un ADMIN los tiene todos por rol y no pasa por la matriz; los peluqueros nacen
        // sin ninguno, que es como se despliegan los permisos nuevos.
        when(permisoService.tienePermiso(eq(Rol.ADMIN), any())).thenReturn(true);
    }

    private Usuario usuario(Integer id, String nombre, Rol rol, String email) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setNombre(nombre);
        usuario.setRol(rol);
        usuario.setEmail(email);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private void conceder(Permiso permiso) {
        when(permisoService.tienePermiso(Rol.PELUQUERO, permiso)).thenReturn(true);
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

    private GaleriaFoto fotoDe(Usuario dueno) {
        GaleriaFoto foto = fotoBase();
        foto.setSubidoPor(dueno);
        return foto;
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
        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), jpeg("miniatura"), "Degradado", EMAIL_ADMIN);

        assertEquals(2, almacen.total());
        assertEquals("Degradado", dto.getTitulo());
        assertNotNull(dto.getUrlImagen());
        assertNotEquals(dto.getUrlImagen(), dto.getUrlMiniatura());
        // El bucket de la galeria es publico: se sirve por URL de lectura, no firmada.
        assertTrue(dto.getUrlMiniatura().startsWith("https://almacen.test/" + bucket() + "/"));
    }

    @Test
    void subirFoto_laClaveLaGeneraElServidorYSeparaTamanos() {
        galeriaService.subirFoto(jpeg("imagen"), jpeg("miniatura"), null, EMAIL_ADMIN);

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
        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), null, null, EMAIL_ADMIN);

        assertEquals(1, almacen.total());
        assertEquals(dto.getUrlImagen(), dto.getUrlMiniatura());
    }

    @Test
    void subirFoto_miniaturaVaciaSeTrataComoAusente() {
        MockMultipartFile vacia = new MockMultipartFile("miniatura", "m.jpg", "image/jpeg", new byte[0]);

        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), vacia, null, EMAIL_ADMIN);

        assertEquals(1, almacen.total());
        assertEquals(dto.getUrlImagen(), dto.getUrlMiniatura());
    }

    @Test
    void subirFoto_rechazaLoQueNoEsImagenAunqueLoDigaElContentType() {
        MockMultipartFile falsa = new MockMultipartFile("imagen", "virus.jpg", "image/jpeg",
                new byte[] { 0x4D, 0x5A, 0x00, 0x00 });

        assertThrows(IllegalArgumentException.class, () -> galeriaService.subirFoto(falsa, null, null, EMAIL_ADMIN));
        assertEquals(0, almacen.total());
    }

    @Test
    void subirFoto_rechazaUnaMiniaturaQueNoEsImagen() {
        MockMultipartFile falsa = new MockMultipartFile("miniatura", "m.jpg", "image/jpeg",
                new byte[] { 0x4D, 0x5A, 0x00, 0x00 });

        assertThrows(IllegalArgumentException.class, () -> galeriaService.subirFoto(jpeg("imagen"), falsa, null, EMAIL_ADMIN));
    }

    @Test
    void subirFoto_seColocaDetrasDeLaUltima() {
        GaleriaFoto ultima = fotoBase();
        ultima.setOrden(4);
        when(galeriaFotoRepository.findFirstByOrderByOrdenDescIdFotoDesc()).thenReturn(Optional.of(ultima));

        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), null, null, EMAIL_ADMIN);

        assertEquals(5, dto.getOrden());
    }

    @Test
    void subirFoto_laPrimeraEmpiezaEnCero() {
        when(galeriaFotoRepository.findFirstByOrderByOrdenDescIdFotoDesc()).thenReturn(Optional.empty());

        assertEquals(0, galeriaService.subirFoto(jpeg("imagen"), null, null, EMAIL_ADMIN).getOrden());
    }

    @Test
    void subirFoto_unTituloEnBlancoEsNoTenerTitulo() {
        assertNull(galeriaService.subirFoto(jpeg("imagen"), null, "   ", EMAIL_ADMIN).getTitulo());
    }

    @Test
    void listarFotos_devuelveElOrdenDelRepositorio() {
        GaleriaFoto primera = fotoBase();
        GaleriaFoto segunda = fotoBase();
        segunda.setIdFoto(2);
        segunda.setOrden(1);
        when(galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc()).thenReturn(List.of(primera, segunda));

        List<GaleriaFotoResponseDTO> fotos = galeriaService.listarFotos(EMAIL_ADMIN);

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

        GaleriaFotoResponseDTO dto = galeriaService.actualizarFoto(1, request, EMAIL_ADMIN);

        assertEquals("Corte con tijera", dto.getTitulo());
        assertEquals(3, dto.getOrden());
    }

    @Test
    void actualizarFoto_loQueNoVieneSeQuedaComoEstaba() {
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoBase()));

        GaleriaFotoResponseDTO dto = galeriaService.actualizarFoto(1, new GaleriaFotoUpdateDTO(), EMAIL_ADMIN);

        assertEquals("Degradado", dto.getTitulo());
        assertEquals(0, dto.getOrden());
    }

    @Test
    void actualizarFoto_noAceptaOrdenNegativo() {
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoBase()));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setOrden(-3);

        assertEquals(0, galeriaService.actualizarFoto(1, request, EMAIL_ADMIN).getOrden());
    }

    @Test
    void actualizarFoto_idInexistente() {
        when(galeriaFotoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> galeriaService.actualizarFoto(99, new GaleriaFotoUpdateDTO(), EMAIL_ADMIN));
    }

    @Test
    void eliminarFoto_borraLosDosObjetos() {
        GaleriaFoto foto = fotoBase();
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(foto));
        almacen.guardar(bucket(), foto.getImagenClave(), new byte[] { 1 }, "image/jpeg");
        almacen.guardar(bucket(), foto.getMiniaturaClave(), new byte[] { 1 }, "image/jpeg");

        galeriaService.eliminarFoto(1, EMAIL_ADMIN);

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

        galeriaService.eliminarFoto(1, EMAIL_ADMIN);

        assertEquals(0, almacen.total());
    }

    @Test
    void eliminarFoto_idInexistente() {
        when(galeriaFotoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> galeriaService.eliminarFoto(99, EMAIL_ADMIN));
        verify(galeriaFotoRepository, never()).delete(any());
    }

    // --- Dueno de la foto y permisos -------------------------------------------------

    @Test
    void subirFoto_sellaElDuenoConLaCuentaAutenticada() {
        conceder(Permiso.GALERIA_SUBIR);

        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), null, "Recogido", EMAIL_ANA);

        ArgumentCaptor<GaleriaFoto> guardada = ArgumentCaptor.forClass(GaleriaFoto.class);
        verify(galeriaFotoRepository).save(guardada.capture());
        // El dueno sale de la autenticacion: si viniera en el multipart, cualquiera podria
        // subir en nombre de otro.
        assertEquals(ana, guardada.getValue().getSubidoPor());
        assertEquals("Ana", dto.getSubidoPorNombre());
        assertTrue(dto.isMia());
    }

    @Test
    void subirFoto_unPeluqueroSinElPermisoNoPublicaEnElEscaparate() {
        assertThrows(AccessDeniedException.class,
                () -> galeriaService.subirFoto(jpeg("imagen"), null, null, EMAIL_ANA));

        // Y no deja el fichero huerfano en el bucket: se comprueba antes de guardar.
        assertEquals(0, almacen.total());
        verify(galeriaFotoRepository, never()).save(any());
    }

    @Test
    void subirFoto_unAdminNoNecesitaElPermiso() {
        GaleriaFotoResponseDTO dto = galeriaService.subirFoto(jpeg("imagen"), null, null, EMAIL_ADMIN);

        assertEquals("Lalo", dto.getSubidoPorNombre());
    }

    @Test
    void listarFotos_marcaComoSuyasSoloLasDeLaCuentaQuePregunta() {
        GaleriaFoto deAna = fotoDe(ana);
        GaleriaFoto deLuis = fotoDe(luis);
        deLuis.setIdFoto(2);
        when(galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc()).thenReturn(List.of(deAna, deLuis));

        List<GaleriaFotoResponseDTO> fotos = galeriaService.listarFotos(EMAIL_ANA);

        assertTrue(fotos.get(0).isMia());
        assertFalse(fotos.get(1).isMia());
        // El nombre se publica para saber de quien es el trabajo; el email y el id, nunca.
        assertEquals("Luis", fotos.get(1).getSubidoPorNombre());
    }

    @Test
    void listarFotos_sinCuentaSeSirveIgualYNingunaEsSuya() {
        when(galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc()).thenReturn(List.of(fotoDe(ana)));

        List<GaleriaFotoResponseDTO> fotos = galeriaService.listarFotos(null);

        assertEquals(1, fotos.size());
        assertFalse(fotos.get(0).isMia());
    }

    @Test
    void listarFotos_unaFotoDelNegocioNoTieneDueno() {
        when(galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc()).thenReturn(List.of(fotoBase()));

        // Las que ya existian se quedan sin dueno a proposito: son de la peluqueria.
        GaleriaFotoResponseDTO dto = galeriaService.listarFotos(EMAIL_ANA).get(0);

        assertNull(dto.getSubidoPorNombre());
        assertFalse(dto.isMia());
    }

    @Test
    void actualizarFoto_elDuenoConPermisoCambiaSuTitulo() {
        conceder(Permiso.GALERIA_EDITAR_PROPIA);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoDe(ana)));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setTitulo("Corte con tijera");

        assertEquals("Corte con tijera", galeriaService.actualizarFoto(1, request, EMAIL_ANA).getTitulo());
    }

    @Test
    void actualizarFoto_sinPermisoNoTocaNiLaSuya() {
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoDe(ana)));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setTitulo("Corte con tijera");

        assertThrows(AccessDeniedException.class, () -> galeriaService.actualizarFoto(1, request, EMAIL_ANA));
    }

    @Test
    void actualizarFoto_unPeluqueroNoTocaElTituloDeOtro() {
        conceder(Permiso.GALERIA_EDITAR_PROPIA);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoDe(luis)));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setTitulo("Mio ahora");

        // Es justo lo que se pidio evitar: editar las suyas si, las de un companero no.
        assertThrows(AccessDeniedException.class, () -> galeriaService.actualizarFoto(1, request, EMAIL_ANA));
        verify(galeriaFotoRepository, never()).save(any());
    }

    @Test
    void actualizarFoto_conElPermisoDeAjenaSiTocaLaDeOtro() {
        conceder(Permiso.GALERIA_EDITAR_AJENA);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoDe(luis)));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setTitulo("Retocado");

        assertEquals("Retocado", galeriaService.actualizarFoto(1, request, EMAIL_ANA).getTitulo());
    }

    @Test
    void actualizarFoto_lasDelNegocioCuentanComoAjenas() {
        conceder(Permiso.GALERIA_EDITAR_PROPIA);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoBase()));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setTitulo("Mio ahora");

        // Sin dueno no es de nadie, asi que tampoco es suya: con EDITAR_AJENA apagado solo
        // las toca un ADMIN.
        assertThrows(AccessDeniedException.class, () -> galeriaService.actualizarFoto(1, request, EMAIL_ANA));
    }

    @Test
    void actualizarFoto_reordenarNoDependeDelDueno() {
        conceder(Permiso.GALERIA_ORDENAR);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoDe(luis)));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setOrden(2);

        // Mover una foto renumera la rejilla de todos, asi que el permiso es sobre la
        // rejilla y no sobre la foto: con el encendido se mueve cualquiera.
        assertEquals(2, galeriaService.actualizarFoto(1, request, EMAIL_ANA).getOrden());
    }

    @Test
    void actualizarFoto_editarLasSuyasNoDaDerechoAReordenar() {
        conceder(Permiso.GALERIA_EDITAR_PROPIA);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(fotoDe(ana)));
        GaleriaFotoUpdateDTO request = new GaleriaFotoUpdateDTO();
        request.setOrden(5);

        assertThrows(AccessDeniedException.class, () -> galeriaService.actualizarFoto(1, request, EMAIL_ANA));
    }

    @Test
    void eliminarFoto_elDuenoConPermisoBorraLaSuya() {
        conceder(Permiso.GALERIA_EDITAR_PROPIA);
        GaleriaFoto foto = fotoDe(ana);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(foto));
        almacen.guardar(bucket(), foto.getImagenClave(), new byte[] { 1 }, "image/jpeg");
        almacen.guardar(bucket(), foto.getMiniaturaClave(), new byte[] { 1 }, "image/jpeg");

        galeriaService.eliminarFoto(1, EMAIL_ANA);

        assertEquals(0, almacen.total());
        verify(galeriaFotoRepository).delete(foto);
    }

    @Test
    void eliminarFoto_unPeluqueroNoBorraElTrabajoDeOtro() {
        conceder(Permiso.GALERIA_EDITAR_PROPIA);
        GaleriaFoto foto = fotoDe(luis);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(foto));
        almacen.guardar(bucket(), foto.getImagenClave(), new byte[] { 1 }, "image/jpeg");

        assertThrows(AccessDeniedException.class, () -> galeriaService.eliminarFoto(1, EMAIL_ANA));

        // Ni la fila ni el objeto del almacen: un borrado no se deshace.
        assertEquals(1, almacen.total());
        verify(galeriaFotoRepository, never()).delete(any());
    }

    @Test
    void eliminarFoto_unAdminBorraLaQueSea() {
        GaleriaFoto foto = fotoDe(luis);
        when(galeriaFotoRepository.findById(1)).thenReturn(Optional.of(foto));
        almacen.guardar(bucket(), foto.getImagenClave(), new byte[] { 1 }, "image/jpeg");
        almacen.guardar(bucket(), foto.getMiniaturaClave(), new byte[] { 1 }, "image/jpeg");

        galeriaService.eliminarFoto(1, EMAIL_ADMIN);

        assertEquals(0, almacen.total());
    }
}
