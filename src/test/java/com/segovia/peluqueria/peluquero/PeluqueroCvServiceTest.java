package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.almacen.AlmacenFicheros;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvUpdateDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroPublicoDTO;
import com.segovia.peluqueria.permiso.Permiso;
import com.segovia.peluqueria.permiso.PermisoService;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PeluqueroCvServiceTest {

    private PeluqueroRepository peluqueroRepository;
    private UsuarioRepository usuarioRepository;
    private PermisoService permisoService;
    private AlmacenFicheros almacen;
    private ValidadorImagen validadorImagen;
    private PeluqueroCvService cvService;

    private Usuario cuentaAna;
    private Peluquero fichaAna;

    @BeforeEach
    void setUp() {
        peluqueroRepository = mock(PeluqueroRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        permisoService = mock(PermisoService.class);
        almacen = mock(AlmacenFicheros.class);
        validadorImagen = mock(ValidadorImagen.class);

        AlmacenProperties propiedades = new AlmacenProperties();
        cvService = new PeluqueroCvService(peluqueroRepository, usuarioRepository, permisoService,
                almacen, validadorImagen, propiedades);

        cuentaAna = cuenta(10, "ana@test.com", Rol.PELUQUERO);
        fichaAna = ficha(1, "Ana", cuentaAna);

        when(usuarioRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(cuentaAna));
        when(peluqueroRepository.findByUsuarioIdUsuario(10)).thenReturn(Optional.of(fichaAna));
        when(peluqueroRepository.findById(1)).thenReturn(Optional.of(fichaAna));
        when(peluqueroRepository.save(any(Peluquero.class))).thenAnswer(i -> i.getArgument(0));
        when(almacen.urlDeLectura(anyString(), anyString()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(1));
    }

    // ---- El escaparate publico ----

    @Test
    void listarPublicosNoPublicaNadaDeLaCuenta() {
        fichaAna.setPresentacion("Llevo la barberia desde 2015");
        fichaAna.setEspecialidades("Degradados, Barba");
        fichaAna.setAniosExperiencia(9);
        fichaAna.setFotoClave("peluqueros/ana.jpg");
        fichaAna.setInstagram("ana.corta");
        when(peluqueroRepository.findByActivoTrueOrderByOrdenAscNombreAsc()).thenReturn(List.of(fichaAna));

        List<PeluqueroPublicoDTO> publicos = cvService.listarPublicos();

        assertEquals(1, publicos.size());
        PeluqueroPublicoDTO dto = publicos.get(0);
        assertEquals("Ana", dto.getNombre());
        assertEquals(List.of("Degradados", "Barba"), dto.getEspecialidades());
        assertEquals("https://cdn.test/peluqueros/ana.jpg", dto.getFotoUrl());
        // Lo que importa de este DTO es lo que NO trae: se sirve sin token.
        assertFalse(dto.toString().contains("ana@test.com"), "El email no puede salir en el listado publico");
        assertFalse(dto.toString().contains("usuario"), "La cuenta vinculada no puede salir en el listado publico");
        assertFalse(dto.toString().contains("comision"), "La comision no puede salir en el listado publico");
    }

    @Test
    void listarPublicosSaleDelRepositorioQueYaFiltraYordena() {
        when(peluqueroRepository.findByActivoTrueOrderByOrdenAscNombreAsc()).thenReturn(List.of());

        assertTrue(cvService.listarPublicos().isEmpty());
        // Y no por el findByActivoTrue de siempre, que no garantiza ningun orden.
        verify(peluqueroRepository).findByActivoTrueOrderByOrdenAscNombreAsc();
        verify(peluqueroRepository, never()).findByActivoTrue();
    }

    @Test
    void unaFichaSinFotoNoInventaUnaUrl() {
        when(peluqueroRepository.findByActivoTrueOrderByOrdenAscNombreAsc()).thenReturn(List.of(fichaAna));

        assertNull(cvService.listarPublicos().get(0).getFotoUrl());
        verify(almacen, never()).urlDeLectura(anyString(), anyString());
    }

    // ---- Su propio CV ----

    @Test
    void unaCuentaSinFichaNoTieneCv() {
        Usuario huerfano = cuenta(11, "sinficha@test.com", Rol.PELUQUERO);
        when(usuarioRepository.findByEmail("sinficha@test.com")).thenReturn(Optional.of(huerfano));
        when(peluqueroRepository.findByUsuarioIdUsuario(11)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cvService.cvPropio("sinficha@test.com"));
    }

    @Test
    void sinElPermisoNoRellenaSuPropioCv() {
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PERFIL_CV_EDITAR)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> cvService.actualizarCvPropio("ana@test.com", cambio("Hola", null)));
        verify(peluqueroRepository, never()).save(any());
    }

    @Test
    void conElPermisoRellenaSuPropioCv() {
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PERFIL_CV_EDITAR)).thenReturn(true);

        PeluqueroCvUpdateDTO cambio = cambio("  Llevo quince anos  ", "ana.corta");
        cambio.setEspecialidades(List.of(" Degradados ", "Barba"));
        cambio.setAniosExperiencia(15);

        PeluqueroCvDTO dto = cvService.actualizarCvPropio("ana@test.com", cambio);

        assertEquals("Llevo quince anos", dto.getPresentacion(), "La presentacion se guarda sin espacios de sobra");
        assertEquals(List.of("Degradados", "Barba"), dto.getEspecialidades());
        assertEquals(15, dto.getAniosExperiencia());
        assertEquals("ana.corta", dto.getInstagram());
    }

    @Test
    void unAdminRellenaCualquierCvSinPasarPorLaMatrizDePermisos() {
        Usuario jefe = cuenta(1, "jefe@test.com", Rol.ADMIN);
        when(usuarioRepository.findByEmail("jefe@test.com")).thenReturn(Optional.of(jefe));
        when(peluqueroRepository.findByUsuarioIdUsuario(1)).thenReturn(Optional.of(ficha(2, "Jefe", jefe)));

        assertDoesNotThrow(() -> cvService.actualizarCvPropio("jefe@test.com", cambio("Soy el jefe", null)));
        // El CV de otro tampoco: no hay permiso que lo abra, es de ADMIN por ruta.
        assertDoesNotThrow(() -> cvService.actualizarCvDe(1, cambio("El de Ana, escrito por el jefe", null)));
        verifyNoInteractions(permisoService);
    }

    @Test
    void unCampoEnBlancoBorraEnVezDeGuardarEspacios() {
        // En este DTO un null SI borra: es la unica forma de vaciar una presentacion.
        fichaAna.setPresentacion("Algo que ya no vale");
        fichaAna.setEspecialidades("Barba");
        fichaAna.setAniosExperiencia(9);
        fichaAna.setInstagram("ana.corta");

        PeluqueroCvDTO dto = cvService.actualizarCvDe(1, cambio("   ", null));

        assertNull(dto.getPresentacion());
        assertTrue(dto.getEspecialidades().isEmpty());
        assertNull(dto.getAniosExperiencia());
        assertNull(dto.getInstagram());
    }

    // ---- Instagram ----

    @Test
    void elInstagramSeQuedaEnElUsuarioAunqueSePegueLaUrl() {
        assertEquals("peluqueria.lalo",
                cvService.actualizarCvDe(1, cambio(null, "https://www.instagram.com/peluqueria.lalo/?hl=es"))
                        .getInstagram());
        assertEquals("peluqueria.lalo",
                cvService.actualizarCvDe(1, cambio(null, "@peluqueria.lalo")).getInstagram());
        assertEquals("peluqueria.lalo",
                cvService.actualizarCvDe(1, cambio(null, "instagram.com/peluqueria.lalo")).getInstagram());
    }

    @Test
    void unInstagramQueNoLoEsSeRechaza() {
        // Un 400 con un mensaje que se entienda, no una cadena rara guardada para siempre.
        assertThrows(IllegalArgumentException.class,
                () -> cvService.actualizarCvDe(1, cambio(null, "no es un usuario!")));
        assertThrows(IllegalArgumentException.class,
                () -> cvService.actualizarCvDe(1, cambio(null, "https://facebook.com/otracosa")));
    }

    // ---- La foto ----

    @Test
    void unPeluqueroNoTocaLaFotoDeUnCompaneroNiConElPermisoEncendido() {
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PERFIL_CV_EDITAR)).thenReturn(true);
        Peluquero deLuis = ficha(2, "Luis", cuenta(20, "luis@test.com", Rol.PELUQUERO));
        when(peluqueroRepository.findById(2)).thenReturn(Optional.of(deLuis));

        assertThrows(AccessDeniedException.class,
                () -> cvService.subirFoto(2, jpeg(), "ana@test.com"));
        assertThrows(AccessDeniedException.class, () -> cvService.borrarFoto(2, "ana@test.com"));
        verifyNoInteractions(almacen);
    }

    @Test
    void unaFichaSinCuentaSoloLaTocaElAdmin() {
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PERFIL_CV_EDITAR)).thenReturn(true);
        Peluquero sinCuenta = ficha(3, "Ficha del negocio", null);
        when(peluqueroRepository.findById(3)).thenReturn(Optional.of(sinCuenta));

        assertThrows(AccessDeniedException.class, () -> cvService.borrarFoto(3, "ana@test.com"));

        Usuario jefe = cuenta(1, "jefe@test.com", Rol.ADMIN);
        when(usuarioRepository.findByEmail("jefe@test.com")).thenReturn(Optional.of(jefe));
        assertDoesNotThrow(() -> cvService.borrarFoto(3, "jefe@test.com"));
    }

    @Test
    void reemplazarLaFotoBorraLaAnteriorYnoAlReves() {
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PERFIL_CV_EDITAR)).thenReturn(true);
        fichaAna.setFotoClave("peluqueros/vieja.jpg");
        when(validadorImagen.validar(any(), eq("peluqueros")))
                .thenReturn(new ValidadorImagen.ImagenValidada(new byte[] { 1 }, "image/jpeg", "peluqueros/nueva.jpg"));
        when(almacen.guardar(anyString(), anyString(), any(), anyString()))
                .thenAnswer(i -> i.getArgument(1));

        PeluqueroCvDTO dto = cvService.subirFoto(1, jpeg(), "ana@test.com");

        assertEquals("https://cdn.test/peluqueros/nueva.jpg", dto.getFotoUrl());
        // El orden importa: si se borrara antes de guardar, un fallo al subir dejaria la
        // ficha apuntando a un objeto que ya no existe.
        InOrder orden = inOrder(almacen);
        orden.verify(almacen).guardar(anyString(), eq("peluqueros/nueva.jpg"), any(), anyString());
        orden.verify(almacen).borrar(anyString(), eq("peluqueros/vieja.jpg"));
    }

    @Test
    void borrarUnaFotoQueNoHayNoHaceNada() {
        Usuario jefe = cuenta(1, "jefe@test.com", Rol.ADMIN);
        when(usuarioRepository.findByEmail("jefe@test.com")).thenReturn(Optional.of(jefe));

        assertNull(cvService.borrarFoto(1, "jefe@test.com").getFotoUrl());
        verify(almacen, never()).borrar(anyString(), anyString());
        verify(peluqueroRepository, never()).save(any());
    }

    // ---- Helpers ----

    private PeluqueroCvUpdateDTO cambio(String presentacion, String instagram) {
        PeluqueroCvUpdateDTO dto = new PeluqueroCvUpdateDTO();
        dto.setPresentacion(presentacion);
        dto.setInstagram(instagram);
        return dto;
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("foto", "yo.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF });
    }

    private Usuario cuenta(Integer id, String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setEmail(email);
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private Peluquero ficha(Integer id, String nombre, Usuario usuario) {
        Peluquero peluquero = new Peluquero();
        peluquero.setIdPeluquero(id);
        peluquero.setNombre(nombre);
        peluquero.setActivo(true);
        peluquero.setUsuario(usuario);
        return peluquero;
    }
}
