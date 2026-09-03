package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.almacen.AlmacenFicheros;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GaleriaService {

    /** Carpetas logicas dentro del bucket, para no mezclar tamanos. */
    private static final String PREFIJO_IMAGEN = "fotos";
    private static final String PREFIJO_MINIATURA = "miniaturas";

    private final GaleriaFotoRepository galeriaFotoRepository;
    private final AlmacenFicheros almacen;
    private final ValidadorImagen validadorImagen;
    private final AlmacenProperties almacenProperties;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    public GaleriaService(GaleriaFotoRepository galeriaFotoRepository,
                          AlmacenFicheros almacen,
                          ValidadorImagen validadorImagen,
                          AlmacenProperties almacenProperties,
                          UsuarioRepository usuarioRepository,
                          PermisoService permisoService) {
        this.galeriaFotoRepository = galeriaFotoRepository;
        this.almacen = almacen;
        this.validadorImagen = validadorImagen;
        this.almacenProperties = almacenProperties;
        this.usuarioRepository = usuarioRepository;
        this.permisoService = permisoService;
    }

    /**
     * El escaparate completo. Se sirve igual sin cuenta; el email solo entra para marcar
     * cuales son suyas y que el frontend sepa que acciones pintar. Un token de una cuenta
     * que ya no existe no rompe el listado: se responde como a un anonimo.
     */
    @Transactional(readOnly = true)
    public List<GaleriaFotoResponseDTO> listarFotos(String emailAutenticado) {
        Integer idActual = idDeCuentaOpcional(emailAutenticado);
        return galeriaFotoRepository.findAllByOrderByOrdenAscIdFotoAsc().stream()
                .map(foto -> aDTO(foto, idActual))
                .toList();
    }

    /**
     * Sube una foto nueva al final de la rejilla.
     *
     * <p>La miniatura viene generada por el cliente, que es donde ya se redimensiona
     * la foto de servicio y el avatar: el servidor tiene 0,1 CPU en produccion y
     * escalar imagenes ahi seria gastarla en algo que el navegador hace gratis. Se
     * valida igual que la grande (por los bytes, no por lo que diga la peticion) y,
     * si no llega, la fila queda sin miniatura y al leer se cae a la imagen grande.
     *
     * <p>La foto queda sellada con la cuenta que la sube, y ese sello es lo que despues
     * decide quien puede editarla. Se toma de la autenticacion y nunca de la peticion:
     * si el dueno viniera en el multipart, cualquiera podria subir en nombre de otro.
     */
    @Transactional
    public GaleriaFotoResponseDTO subirFoto(MultipartFile imagen, MultipartFile miniatura, String titulo,
                                            String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        verificarPuedeSubir(actual);

        ValidadorImagen.ImagenValidada grande = validadorImagen.validar(imagen, PREFIJO_IMAGEN);
        String bucket = almacenProperties.getBucketGaleria();

        GaleriaFoto foto = new GaleriaFoto();
        foto.setImagenClave(almacen.guardar(bucket, grande.clave(), grande.contenido(), grande.contentType()));

        if (miniatura != null && !miniatura.isEmpty()) {
            ValidadorImagen.ImagenValidada pequena = validadorImagen.validar(miniatura, PREFIJO_MINIATURA);
            foto.setMiniaturaClave(
                    almacen.guardar(bucket, pequena.clave(), pequena.contenido(), pequena.contentType()));
        }

        foto.setTitulo(normalizar(titulo));
        foto.setOrden(siguienteOrden());
        foto.setSubidoPor(actual);
        return aDTO(galeriaFotoRepository.save(foto), actual.getIdUsuario());
    }

    /**
     * Cambia titulo u orden. Lo que no venga en el DTO se queda como estaba.
     *
     * <p>Los dos campos se comprueban por separado porque no cuestan lo mismo: el titulo
     * es de la foto y lo gobierna el dueno, mientras que el orden es de la rejilla entera
     * y mover una foto renumera las de todos. Un peluquero con permiso para ordenar puede
     * mover cualquier foto sin poder tocar el titulo de una ajena.
     */
    @Transactional
    public GaleriaFotoResponseDTO actualizarFoto(Integer id, GaleriaFotoUpdateDTO request,
                                                 String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        GaleriaFoto foto = obtenerEntidadPorId(id);

        if (request.getTitulo() != null) {
            verificarPuedeEditar(foto, actual, "cambiar el titulo de");
            foto.setTitulo(normalizar(request.getTitulo()));
        }
        if (request.getOrden() != null) {
            verificarPuedeOrdenar(actual);
            foto.setOrden(Math.max(0, request.getOrden()));
        }
        return aDTO(galeriaFotoRepository.save(foto), actual.getIdUsuario());
    }

    /**
     * Borra la foto y sus DOS objetos del almacen. Si solo se borrara la grande, la
     * miniatura se quedaria comiendo cuota sin que nada la referencie.
     */
    @Transactional
    public void eliminarFoto(Integer id, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        GaleriaFoto foto = obtenerEntidadPorId(id);
        verificarPuedeEditar(foto, actual, "borrar");

        String bucket = almacenProperties.getBucketGaleria();
        galeriaFotoRepository.delete(foto);

        almacen.borrar(bucket, foto.getImagenClave());
        if (foto.getMiniaturaClave() != null && !foto.getMiniaturaClave().isBlank()) {
            almacen.borrar(bucket, foto.getMiniaturaClave());
        }
    }

    /**
     * Si esa cuenta puede publicar en el escaparate. Un ADMIN siempre; un PELUQUERO solo
     * con {@link Permiso#GALERIA_SUBIR} encendido.
     *
     * <p>El orden es el de siempre: la regla de rol ya la aplico SecurityConfig y el
     * permiso solo estrecha. Encenderlo no le abre la galeria a nadie a quien su rol no se
     * la dejara al alcance.
     */
    private void verificarPuedeSubir(Usuario actual) {
        if (actual.getRol() == Rol.ADMIN) {
            return;
        }
        exigirPermiso(actual, Permiso.GALERIA_SUBIR,
                "Subir fotos a la galeria no esta habilitado para tu rol. Pideselo a un administrador.");
    }

    /**
     * Si esa cuenta puede editar o borrar esa foto concreta. Es la regla que se pidio: cada
     * uno con las suyas, y las de otro solo con el permiso que se deja apagado.
     *
     * <p>Una foto sin dueno es del negocio y cuenta como ajena, no como de nadie: la subio
     * la peluqueria y quitarla es decidir por el escaparate. Con
     * {@link Permiso#GALERIA_EDITAR_AJENA} apagado, que es como nace, solo las toca un
     * ADMIN.
     */
    private void verificarPuedeEditar(GaleriaFoto foto, Usuario actual, String accion) {
        if (actual.getRol() == Rol.ADMIN) {
            return;
        }
        if (esSuya(foto, actual)) {
            exigirPermiso(actual, Permiso.GALERIA_EDITAR_PROPIA,
                    "Editar tus fotos de la galeria no esta habilitado para tu rol."
                            + " Pideselo a un administrador.");
            return;
        }
        exigirPermiso(actual, Permiso.GALERIA_EDITAR_AJENA,
                "No puedes " + accion + " una foto que no has subido tu.");
    }

    /**
     * Si esa cuenta puede mover fotos en la rejilla. No mira el dueno a proposito: colocar
     * la propia en otro sitio corre de posicion las de los demas, asi que el permiso es
     * sobre la rejilla y no sobre una foto.
     */
    private void verificarPuedeOrdenar(Usuario actual) {
        if (actual.getRol() == Rol.ADMIN) {
            return;
        }
        exigirPermiso(actual, Permiso.GALERIA_ORDENAR,
                "Reordenar la galeria no esta habilitado para tu rol. Pideselo a un administrador.");
    }

    private void exigirPermiso(Usuario actual, Permiso permiso, String mensaje) {
        if (!permisoService.tienePermiso(actual.getRol(), permiso)) {
            throw new AccessDeniedException(mensaje);
        }
    }

    /** Sin dueno no es de nadie, asi que tampoco es suya. */
    private boolean esSuya(GaleriaFoto foto, Usuario actual) {
        return foto.getSubidoPor() != null
                && foto.getSubidoPor().getIdUsuario().equals(actual.getIdUsuario());
    }

    /** null si no hay cuenta autenticada, o si el token trae una que ya no esta. */
    private Integer idDeCuentaOpcional(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return usuarioRepository.findByEmail(email)
                .map(Usuario::getIdUsuario)
                .orElse(null);
    }

    private Usuario obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    private GaleriaFoto obtenerEntidadPorId(Integer id) {
        return galeriaFotoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Foto de galeria no encontrada con id: " + id));
    }

    private int siguienteOrden() {
        return galeriaFotoRepository.findFirstByOrderByOrdenDescIdFotoDesc()
                .map(ultima -> ultima.getOrden() + 1)
                .orElse(0);
    }

    /** Un titulo en blanco es no tener titulo; no se guardan cadenas vacias. */
    private String normalizar(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return null;
        }
        return titulo.trim();
    }

    /** Traduce las claves guardadas a URLs. El bucket es publico: material promocional. */
    private GaleriaFotoResponseDTO aDTO(GaleriaFoto foto, Integer idActual) {
        String bucket = almacenProperties.getBucketGaleria();
        String urlImagen = almacen.urlDeLectura(bucket, foto.getImagenClave());
        String urlMiniatura = (foto.getMiniaturaClave() == null || foto.getMiniaturaClave().isBlank())
                ? null
                : almacen.urlDeLectura(bucket, foto.getMiniaturaClave());

        Usuario dueno = foto.getSubidoPor();
        boolean mia = dueno != null && idActual != null && idActual.equals(dueno.getIdUsuario());
        return GaleriaFotoResponseDTO.desde(foto, urlImagen, urlMiniatura,
                dueno != null ? dueno.getNombre() : null, mia);
    }
}
