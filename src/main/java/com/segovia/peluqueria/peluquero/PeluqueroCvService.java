package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.almacen.AlmacenFicheros;
import com.segovia.peluqueria.almacen.AlmacenProperties;
import com.segovia.peluqueria.almacen.ValidadorImagen;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.peluquero.dto.Especialidades;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvUpdateDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroPublicoDTO;
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

/**
 * El CV publico del profesional: lo que el cliente lee para elegir con quien agendar, y lo
 * que el propio peluquero o el administrador rellenan.
 *
 * <p>Va en un servicio aparte de {@link PeluqueroService} porque no comparte nada con el:
 * ese gobierna la plantilla y el dinero (fichas, comisiones, cuentas vinculadas) y este
 * gobierna material promocional que se sirve sin token. Es tambien el unico sitio del
 * dominio que habla con el almacen de ficheros.
 */
@Service
public class PeluqueroCvService {

    /**
     * Carpeta logica de las fotos de CV. Van en el bucket de la galeria, no en uno propio,
     * y es deliberado: es el bucket publico que ya existe y tiene exactamente los permisos
     * que hace falta —lectura anonima de material promocional—. Un bucket nuevo obligaria a
     * crearlo a mano en Supabase y a anadir una variable de entorno en Render, o sea dos
     * pasos manuales cuyo olvido no se nota hasta que alguien sube una foto en produccion.
     */
    private static final String PREFIJO_FOTO = "peluqueros";

    private final PeluqueroRepository peluqueroRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;
    private final AlmacenFicheros almacen;
    private final ValidadorImagen validadorImagen;
    private final AlmacenProperties almacenProperties;

    public PeluqueroCvService(PeluqueroRepository peluqueroRepository,
                              UsuarioRepository usuarioRepository,
                              PermisoService permisoService,
                              AlmacenFicheros almacen,
                              ValidadorImagen validadorImagen,
                              AlmacenProperties almacenProperties) {
        this.peluqueroRepository = peluqueroRepository;
        this.usuarioRepository = usuarioRepository;
        this.permisoService = permisoService;
        this.almacen = almacen;
        this.validadorImagen = validadorImagen;
        this.almacenProperties = almacenProperties;
    }

    /**
     * El equipo, para que un cliente elija antes de tener cuenta. Se sirve sin token, asi
     * que la responsabilidad de que no se filtre nada personal esta en
     * {@link PeluqueroPublicoDTO} y no aqui: por eso es un DTO nuevo y no uno reutilizado.
     *
     * <p>Solo los activos: una ficha desactivada es alguien que ya no trabaja aqui, y
     * seguir presentandolo llevaria al cliente a pedir cita con quien no esta.
     */
    @Transactional(readOnly = true)
    public List<PeluqueroPublicoDTO> listarPublicos() {
        return peluqueroRepository.findByActivoTrueOrderByOrdenAscNombreAsc().stream()
                .map(p -> PeluqueroPublicoDTO.desde(p, urlFoto(p)))
                .toList();
    }

    /**
     * El CV de la ficha vinculada a la cuenta autenticada.
     *
     * <p>Sin id por parametro, igual que {@code /api/produccion/mia}: asi no existe la
     * version del endpoint en la que alguien pide "el suyo" pasando el id de otro.
     */
    @Transactional(readOnly = true)
    public PeluqueroCvDTO cvPropio(String emailAutenticado) {
        Peluquero ficha = fichaDeLaCuenta(emailAutenticado);
        return PeluqueroCvDTO.desde(ficha, urlFoto(ficha));
    }

    /**
     * Reemplaza el CV de la ficha de la cuenta autenticada. Lo que no venga se borra: es la
     * unica forma de poder vaciar una presentacion (ver {@link PeluqueroCvUpdateDTO}).
     */
    @Transactional
    public PeluqueroCvDTO actualizarCvPropio(String emailAutenticado, PeluqueroCvUpdateDTO request) {
        Usuario actual = cuenta(emailAutenticado);
        Peluquero ficha = fichaDe(actual);
        verificarPuedeEditarElSuyo(actual);
        return guardarCv(ficha, request);
    }

    /**
     * Reemplaza el CV de cualquier ficha. Es de ADMIN por ruta y no lleva comprobacion de
     * permiso: el CV de un companero no lo abre ningun flag, ni siquiera
     * {@link Permiso#PERFIL_CV_EDITAR}, que alcanza solo al propio.
     */
    @Transactional
    public PeluqueroCvDTO actualizarCvDe(Integer idPeluquero, PeluqueroCvUpdateDTO request) {
        return guardarCv(porId(idPeluquero), request);
    }

    /**
     * Pone (o reemplaza) la foto del CV. La clave la genera el validador a partir del
     * contenido real del fichero, nunca de lo que diga la peticion.
     *
     * <p>La anterior se borra despues de guardar la nueva y solo si la nueva se guardo: al
     * reves, un fallo al subir dejaria la ficha apuntando a un objeto que ya no existe.
     */
    @Transactional
    public PeluqueroCvDTO subirFoto(Integer idPeluquero, MultipartFile foto, String emailAutenticado) {
        Peluquero ficha = porId(idPeluquero);
        verificarPuedeEditar(ficha, emailAutenticado);

        ValidadorImagen.ImagenValidada validada = validadorImagen.validar(foto, PREFIJO_FOTO);
        String bucket = bucket();

        String claveAnterior = ficha.getFotoClave();
        String clave = almacen.guardar(bucket, validada.clave(), validada.contenido(), validada.contentType());
        ficha.setFotoClave(clave);
        Peluquero guardado = peluqueroRepository.save(ficha);

        if (claveAnterior != null && !claveAnterior.isBlank() && !claveAnterior.equals(clave)) {
            almacen.borrar(bucket, claveAnterior);
        }
        return PeluqueroCvDTO.desde(guardado, urlFoto(guardado));
    }

    /** Quita la foto. Idempotente: sin foto no hace nada, como el borrado del catalogo. */
    @Transactional
    public PeluqueroCvDTO borrarFoto(Integer idPeluquero, String emailAutenticado) {
        Peluquero ficha = porId(idPeluquero);
        verificarPuedeEditar(ficha, emailAutenticado);

        String clave = ficha.getFotoClave();
        if (clave == null || clave.isBlank()) {
            return PeluqueroCvDTO.desde(ficha, null);
        }
        ficha.setFotoClave(null);
        Peluquero guardado = peluqueroRepository.save(ficha);
        almacen.borrar(bucket(), clave);
        return PeluqueroCvDTO.desde(guardado, null);
    }

    /** La URL de la foto de una ficha, o null si no tiene. La usa tambien la vista de gestion. */
    String urlFoto(Peluquero peluquero) {
        String clave = peluquero.getFotoClave();
        if (clave == null || clave.isBlank()) {
            return null;
        }
        return almacen.urlDeLectura(bucket(), clave);
    }

    private PeluqueroCvDTO guardarCv(Peluquero ficha, PeluqueroCvUpdateDTO request) {
        ficha.setPresentacion(textoONulo(request.getPresentacion()));
        ficha.setEspecialidades(Especialidades.aColumna(request.getEspecialidades()));
        ficha.setAniosExperiencia(request.getAniosExperiencia());
        ficha.setInstagram(normalizarInstagram(request.getInstagram()));
        Peluquero guardado = peluqueroRepository.save(ficha);
        return PeluqueroCvDTO.desde(guardado, urlFoto(guardado));
    }

    /**
     * Quien puede tocar la foto de una ficha: un ADMIN cualquiera, y el peluquero la suya
     * si tiene el permiso encendido. La comprobacion no puede vivir en {@code
     * SecurityConfig} porque depende de a que cuenta esta vinculada la ficha, que no se
     * sabe hasta cargarla.
     */
    private void verificarPuedeEditar(Peluquero ficha, String emailAutenticado) {
        Usuario actual = cuenta(emailAutenticado);
        if (actual.getRol() == Rol.ADMIN) {
            return;
        }
        if (ficha.getUsuario() == null
                || !ficha.getUsuario().getIdUsuario().equals(actual.getIdUsuario())) {
            throw new AccessDeniedException("Solo puedes editar tu propia ficha.");
        }
        verificarPuedeEditarElSuyo(actual);
    }

    private void verificarPuedeEditarElSuyo(Usuario actual) {
        if (actual.getRol() == Rol.ADMIN) {
            return;
        }
        if (!permisoService.tienePermiso(actual.getRol(), Permiso.PERFIL_CV_EDITAR)) {
            throw new AccessDeniedException(
                    "Rellenar tu perfil publico no esta habilitado para tu rol. Pideselo a un administrador.");
        }
    }

    /**
     * Deja el usuario de Instagram a secas. Se acepta lo que pegaria una persona —{@code
     * @nombre}, {@code instagram.com/nombre} o la URL completa con parametros— porque el
     * campo se rellena copiando del navegador; guardar la URL entera romperia el enlace que
     * monta el frontend y ademas se saldria de los 100 caracteres.
     */
    private String normalizarInstagram(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String usuario = valor.trim();
        usuario = usuario.replaceFirst("^(?i)(https?://)?(www\\.)?instagram\\.com/", "");
        usuario = usuario.replaceFirst("^@", "");
        // Lo que va detras de una barra o de un ? es la ruta o los parametros, no el usuario.
        int corte = usuario.indexOf('/');
        if (corte >= 0) {
            usuario = usuario.substring(0, corte);
        }
        corte = usuario.indexOf('?');
        if (corte >= 0) {
            usuario = usuario.substring(0, corte);
        }
        if (usuario.isBlank()) {
            return null;
        }
        if (!usuario.matches("[A-Za-z0-9._]{1,30}")) {
            throw new IllegalArgumentException(
                    "'" + valor + "' no parece un usuario de Instagram. Pon solo el usuario, por ejemplo"
                            + " peluqueria.lalo");
        }
        return usuario;
    }

    private String textoONulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private String bucket() {
        return almacenProperties.getBucketGaleria();
    }

    private Usuario cuenta(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    private Peluquero fichaDeLaCuenta(String email) {
        return fichaDe(cuenta(email));
    }

    private Peluquero fichaDe(Usuario usuario) {
        return peluqueroRepository.findByUsuarioIdUsuario(usuario.getIdUsuario())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tu cuenta no esta vinculada a ninguna ficha de peluquero. Pideselo a un administrador."));
    }

    private Peluquero porId(Integer id) {
        return peluqueroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Peluquero no encontrado con id: " + id));
    }
}
