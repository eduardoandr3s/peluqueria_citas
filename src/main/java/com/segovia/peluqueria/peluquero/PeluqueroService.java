package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.peluquero.dto.ComisionServicioDTO;
import com.segovia.peluqueria.peluquero.dto.ComisionesUpdateDTO;
import com.segovia.peluqueria.modulo.Modulo;
import com.segovia.peluqueria.modulo.ModuloService;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroGestionDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroRequestDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroUpdateDTO;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PeluqueroService {

    private final PeluqueroRepository peluqueroRepository;
    private final ComisionServicioRepository comisionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    /** Solo para montar la URL de la foto: el CV entero lo gobierna ese servicio. */
    private final PeluqueroCvService cvService;
    private final ModuloService moduloService;

    public PeluqueroService(PeluqueroRepository peluqueroRepository,
                            ComisionServicioRepository comisionRepository,
                            UsuarioRepository usuarioRepository,
                            ServicioRepository servicioRepository,
                            PeluqueroCvService cvService,
                            ModuloService moduloService) {
        this.peluqueroRepository = peluqueroRepository;
        this.comisionRepository = comisionRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicioRepository = servicioRepository;
        this.cvService = cvService;
        this.moduloService = moduloService;
    }

    @Transactional(readOnly = true)
    public List<PeluqueroResponseDTO> listarActivos() {
        return peluqueroRepository.findByActivoTrue().stream()
                .map(PeluqueroResponseDTO::desde)
                .toList();
    }

    /**
     * Todas las fichas, activas e inactivas, con comision y cuenta vinculada. Es la vista
     * del panel: por eso incluye las inactivas, que son las que hay que poder reactivar.
     */
    @Transactional(readOnly = true)
    public List<PeluqueroGestionDTO> listarParaGestion() {
        return peluqueroRepository.findAll().stream()
                .sorted(Comparator.comparing(Peluquero::getIdPeluquero))
                .map(p -> PeluqueroGestionDTO.desde(
                        p, comisionesGuardadas(p.getIdPeluquero()), cvDe(p), conComision()))
                .toList();
    }

    @Transactional
    public PeluqueroResponseDTO crear(PeluqueroRequestDTO request) {
        Peluquero peluquero = new Peluquero();
        peluquero.setNombre(request.getNombre().trim());
        return PeluqueroResponseDTO.desde(peluqueroRepository.save(peluquero));
    }

    @Transactional(readOnly = true)
    public PeluqueroResponseDTO obtenerPorId(Integer id) {
        return PeluqueroResponseDTO.desde(obtenerEntidadPorId(id));
    }

    @Transactional
    public PeluqueroGestionDTO actualizar(Integer id, PeluqueroUpdateDTO request) {
        Peluquero peluquero = obtenerEntidadPorId(id);
        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            peluquero.setNombre(request.getNombre().trim());
        }
        if (request.getActivo() != null) {
            peluquero.setActivo(request.getActivo());
        }
        if (request.getComisionPorcentaje() != null) {
            // Solo si de verdad intentan tocarla: la ficha se sigue editando con el modulo
            // apagado, lo que no existe ahi es el dinero.
            moduloService.exigir(Modulo.COMISIONES);
            peluquero.setComisionPorcentaje(request.getComisionPorcentaje());
        }
        if (request.getOrden() != null) {
            peluquero.setOrden(request.getOrden());
        }
        if (Boolean.TRUE.equals(request.getDesvincularUsuario())) {
            peluquero.setUsuario(null);
        } else if (request.getUsuarioId() != null) {
            peluquero.setUsuario(validarCuentaVinculable(request.getUsuarioId(), id));
        }
        Peluquero guardado = peluqueroRepository.save(peluquero);
        return PeluqueroGestionDTO.desde(guardado, comisionesGuardadas(guardado.getIdPeluquero()),
                cvDe(guardado), conComision());
    }

    /**
     * Reemplaza el conjunto de excepciones de comision del peluquero. Lo que no venga en la
     * lista se borra: la pantalla edita la tabla entera, asi que quitar una fila y no
     * mandarla es como se borra.
     */
    @Transactional
    public List<ComisionServicioDTO> reemplazarComisiones(Integer id, ComisionesUpdateDTO request) {
        moduloService.exigir(Modulo.COMISIONES);
        Peluquero peluquero = obtenerEntidadPorId(id);
        comisionRepository.deleteAll(comisionRepository.findByPeluqueroIdPeluquero(id));

        Set<Integer> vistos = new HashSet<>();
        for (ComisionServicioDTO fila : request.getComisiones()) {
            if (!vistos.add(fila.getServicioId())) {
                throw new IllegalArgumentException(
                        "El servicio con id " + fila.getServicioId() + " aparece dos veces en las comisiones.");
            }
            Servicio servicio = servicioRepository.findById(fila.getServicioId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Servicio no encontrado con ID: " + fila.getServicioId()));
            ComisionServicio comision = new ComisionServicio();
            comision.setPeluquero(peluquero);
            comision.setServicio(servicio);
            comision.setPorcentaje(fila.getPorcentaje());
            comisionRepository.save(comision);
        }
        return comisionesGuardadas(id);
    }

    /**
     * Las excepciones por servicio de un peluquero. Es el endpoint, asi que exige el modulo:
     * el que va anidado en la ficha no pasa por aqui, porque esa pantalla se sigue abriendo
     * con las comisiones apagadas.
     */
    @Transactional(readOnly = true)
    public List<ComisionServicioDTO> comisionesDe(Integer idPeluquero) {
        moduloService.exigir(Modulo.COMISIONES);
        return comisionesGuardadas(idPeluquero);
    }

    /**
     * El CV anidado en la ficha, o null si el negocio no publica fichas del equipo: ahi esa
     * pestana del panel no existe, igual que no existe la pantalla de la app.
     */
    private PeluqueroCvDTO cvDe(Peluquero peluquero) {
        return moduloService.estaActivo(Modulo.EQUIPO_CV)
                ? PeluqueroCvDTO.desde(peluquero, cvService.urlFoto(peluquero))
                : null;
    }

    /** Si el negocio comisiona. Apagado, la ficha sale sin porcentaje y sin excepciones. */
    private boolean conComision() {
        return moduloService.estaActivo(Modulo.COMISIONES);
    }

    private List<ComisionServicioDTO> comisionesGuardadas(Integer idPeluquero) {
        return comisionRepository.findByPeluqueroIdPeluquero(idPeluquero).stream()
                .sorted(Comparator.comparing(c -> c.getServicio().getIdServicio()))
                .map(ComisionServicioDTO::desde)
                .toList();
    }

    /**
     * Comision que le corresponde al peluquero por un servicio: la excepcion si existe, y
     * si no la de su ficha. Es lo que se congela en la cita al cerrarla.
     */
    @Transactional(readOnly = true)
    public BigDecimal porcentajeAplicable(Integer idPeluquero, Integer idServicio) {
        return comisionRepository
                .findByPeluqueroIdPeluqueroAndServicioIdServicio(idPeluquero, idServicio)
                .map(ComisionServicio::getPorcentaje)
                .orElseGet(() -> peluqueroRepository.findById(idPeluquero)
                        .map(Peluquero::getComisionPorcentaje)
                        .orElse(BigDecimal.ZERO));
    }

    /** Ficha del peluquero que corresponde a una cuenta, vacia si esa cuenta no lo es. */
    @Transactional(readOnly = true)
    public Optional<Peluquero> fichaDeUsuario(Integer idUsuario) {
        return peluqueroRepository.findByUsuarioIdUsuario(idUsuario);
    }

    @Transactional
    public void eliminar(Integer id) {
        Peluquero peluquero = obtenerEntidadPorId(id);
        peluquero.setActivo(false);
        peluqueroRepository.save(peluquero);
    }

    /**
     * Una cuenta se puede vincular si existe, esta activa, no es un cliente y no esta ya en
     * otra ficha. Lo del rol no es cosmetico: vincular a un USER dejaria una ficha que
     * parece operativa y cuyo dueno no ve ni una cita, porque el rol es lo que abre las
     * pantallas. Se avisa en vez de cambiarle el rol por la espalda.
     */
    private Usuario validarCuentaVinculable(Integer idUsuario, Integer idPeluquero) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + idUsuario));
        if (!usuario.getActivo()) {
            throw new IllegalArgumentException("No se puede vincular una cuenta desactivada.");
        }
        if (usuario.getRol() == Rol.USER) {
            throw new IllegalArgumentException(
                    "La cuenta " + usuario.getEmail() + " tiene rol USER: cambiale el rol a PELUQUERO antes de vincularla.");
        }
        peluqueroRepository.findByUsuarioIdUsuario(idUsuario).ifPresent(otra -> {
            if (!otra.getIdPeluquero().equals(idPeluquero)) {
                throw new IllegalArgumentException(
                        "La cuenta " + usuario.getEmail() + " ya esta vinculada al peluquero '" + otra.getNombre() + "'.");
            }
        });
        return usuario;
    }

    Peluquero obtenerEntidadPorId(Integer id) {
        return peluqueroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Peluquero no encontrado con id: " + id));
    }
}
