package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.calendario.CalendarioService;
import com.segovia.peluqueria.calendario.dto.DiaCerradoDTO;
import com.segovia.peluqueria.cita.dto.CitaCierreDTO;
import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import com.segovia.peluqueria.exception.ConflictoHorarioException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.permiso.Permiso;
import com.segovia.peluqueria.permiso.PermisoService;
import com.segovia.peluqueria.notificacion.evento.CitaAgendadaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaAnuladaEvent;
import com.segovia.peluqueria.notificacion.evento.CitaModificadaEvent;
import com.segovia.peluqueria.pago.EstadoPago;
import com.segovia.peluqueria.pago.Pago;
import com.segovia.peluqueria.pago.PagoRepository;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.peluquero.PeluqueroRepository;
import com.segovia.peluqueria.peluquero.PeluqueroService;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.servicio.ServicioRepository;
import com.segovia.peluqueria.servicio.dto.ServicioResponseDTO;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CitaService {

    private static final int PASO_SLOT_MINUTOS = 30;
    /** Meses que cubre /dias-cerrados si no se indica 'hasta', y tope del rango pedido. */
    private static final int MESES_RANGO_CIERRES = 3;
    private static final int MAX_MESES_RANGO_CIERRES = 12;

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final PeluqueroRepository peluqueroRepository;
    private final PeluqueroService peluqueroService;
    private final PagoRepository pagoRepository;
    private final HorarioProperties horario;
    private final CalendarioService calendario;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final PermisoService permisoService;

    public CitaService(CitaRepository citaRepository,
                       UsuarioRepository usuarioRepository,
                       ServicioRepository servicioRepository,
                       PeluqueroRepository peluqueroRepository,
                       PeluqueroService peluqueroService,
                       PagoRepository pagoRepository,
                       HorarioProperties horario,
                       CalendarioService calendario,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock,
                       PermisoService permisoService) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicioRepository = servicioRepository;
        this.peluqueroRepository = peluqueroRepository;
        this.peluqueroService = peluqueroService;
        this.pagoRepository = pagoRepository;
        this.horario = horario;
        this.calendario = calendario;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.permisoService = permisoService;
    }

    @Transactional(readOnly = true)
    public Page<CitaResponseDTO> listarCitas(String emailAutenticado, Pageable pageable) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        // Un ADMIN ve todas las citas, un PELUQUERO su agenda (las que tiene asignadas) y
        // un USER solo las suyas. La agenda del peluquero es lo asignado a su FICHA, no lo
        // que el haya reservado como cliente: son dos cosas distintas y la que necesita
        // para trabajar es la primera.
        Page<Cita> citas;
        if (esAdmin(actual)) {
            citas = citaRepository.findAll(pageable);
        } else if (actual.getRol() == Rol.PELUQUERO) {
            citas = fichaDe(actual)
                    .map(ficha -> citaRepository.findByPeluqueroIdPeluquero(ficha.getIdPeluquero(), pageable))
                    // Cuenta con rol PELUQUERO y sin ficha vinculada: no tiene agenda, y una
                    // pagina vacia es mas honesto que un error de servidor.
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
        } else {
            citas = citaRepository.findByUsuarioIdUsuario(actual.getIdUsuario(), pageable);
        }

        // Pago de todas las citas de la pagina en una sola consulta (evita N+1).
        Map<Integer, Pago> pagos = pagosDe(citas.map(Cita::getIdCita).getContent());
        boolean gestion = puedeGestionar(actual);
        return citas.map(cita -> mapearAResponseDTO(cita, pagos.get(cita.getIdCita()), gestion));
    }

    /** Mapa citaId -> pago para las citas dadas (solo las que tienen pago aparecen). */
    private Map<Integer, Pago> pagosDe(List<Integer> citaIds) {
        if (citaIds.isEmpty()) {
            return Map.of();
        }
        return pagoRepository.findByCitaIdCitaIn(citaIds).stream()
                .collect(Collectors.toMap(p -> p.getCita().getIdCita(), p -> p));
    }

    /** Pago de una sola cita, o null si no tiene. */
    private Pago pagoDe(Integer citaId) {
        return pagoRepository.findByCitaIdCita(citaId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<String> obtenerDisponibilidad(LocalDate fecha, Integer idServicio, Integer peluqueroId) {
        if (fecha.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("No se puede consultar disponibilidad en una fecha pasada.");
        }

        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + idServicio));
        if (!servicio.getActivo()) {
            throw new IllegalArgumentException("El servicio no esta disponible.");
        }

        // Si se filtra por peluquero, debe existir y estar activo.
        validarPeluqueroActivo(peluqueroId);

        // Dia cerrado (domingo o festivo/cierre puntual): no hay slots.
        if (calendario.esCerrado(fecha)) {
            return List.of();
        }

        int duracion = servicio.getDuracion();
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<String> slotsLibres = new ArrayList<>();

        LocalTime inicio = horario.getApertura();
        // El último inicio válido es aquel cuya cita aún termina a la hora de cierre o antes.
        while (!inicio.plusMinutes(duracion).isAfter(horario.getCierre())) {
            LocalDateTime inicioSlot = fecha.atTime(inicio);
            LocalDateTime finSlot = inicioSlot.plusMinutes(duracion);

            // Para el día de hoy, descartar los slots cuya hora de inicio ya pasó.
            boolean yaPaso = inicioSlot.isBefore(ahora);
            if (!yaPaso && !hayConflicto(inicioSlot, finSlot, null, peluqueroId)) {
                slotsLibres.add(inicio.toString());
            }

            inicio = inicio.plusMinutes(PASO_SLOT_MINUTOS);
        }

        return slotsLibres;
    }

    /**
     * Días cerrados del rango (domingos + festivos/cierres puntuales), para que el
     * cliente pueda deshabilitarlos en el calendario en vez de dejar elegir un día
     * que luego no tendría ninguna hora libre.
     */
    @Transactional(readOnly = true)
    public List<DiaCerradoDTO> obtenerDiasCerrados(LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde != null ? desde : LocalDate.now(clock);
        LocalDate fin = hasta != null ? hasta : inicio.plusMonths(MESES_RANGO_CIERRES);
        if (inicio.plusMonths(MAX_MESES_RANGO_CIERRES).isBefore(fin)) {
            throw new IllegalArgumentException("El rango no puede superar los " + MAX_MESES_RANGO_CIERRES + " meses.");
        }
        return calendario.diasCerrados(inicio, fin);
    }

    /**
     * Valida que el peluquero indicado exista y esté activo. Devuelve la entidad,
     * o {@code null} si no se especifica peluquero (cita sin asignar).
     */
    private Peluquero validarPeluqueroActivo(Integer peluqueroId) {
        if (peluqueroId == null) {
            return null;
        }
        Peluquero peluquero = peluqueroRepository.findById(peluqueroId)
                .orElseThrow(() -> new ResourceNotFoundException("Peluquero no encontrado con ID: " + peluqueroId));
        if (!peluquero.getActivo()) {
            throw new IllegalArgumentException("No se puede usar un peluquero inactivo.");
        }
        return peluquero;
    }

    private boolean hayConflicto(LocalDateTime inicio, LocalDateTime fin, Integer idExcluir, Integer peluqueroId) {
        int conflictos;
        if (peluqueroId != null) {
            conflictos = idExcluir != null
                    ? citaRepository.contarConflictosExcluyendoConPeluquero(inicio, fin, idExcluir, peluqueroId)
                    : citaRepository.contarConflictosConPeluquero(inicio, fin, peluqueroId);
        } else {
            conflictos = idExcluir != null
                    ? citaRepository.contarConflictosExcluyendo(inicio, fin, idExcluir)
                    : citaRepository.contarConflictos(inicio, fin);
        }
        return conflictos > 0;
    }

    @Transactional
    public CitaResponseDTO agendarCita(CitaRequestDTO request, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        // Un USER solo puede agendar citas para sí mismo; un ADMIN puede agendar para cualquiera.
        Integer idUsuarioObjetivo = esAdmin(actual) ? request.getUsuarioId() : actual.getIdUsuario();

        Usuario usuarioCompleto = usuarioRepository.findById(idUsuarioObjetivo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + idUsuarioObjetivo));
        if (!usuarioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un usuario inactivo.");
        }

        Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
        if (!servicioCompleto.getActivo()) {
            throw new IllegalArgumentException("No se puede agendar una cita con un servicio inactivo.");
        }

        Peluquero peluquero = validarPeluqueroActivo(request.getPeluqueroId());

        validarFechaFutura(request.getFechaHora());
        validarHorarioLaboral(request.getFechaHora(), servicioCompleto.getDuracion());

        validarConflictoHorario(request.getFechaHora(), servicioCompleto.getDuracion(), null, request.getPeluqueroId());

        Cita cita = new Cita();
        cita.setUsuario(usuarioCompleto);
        cita.setServicio(servicioCompleto);
        cita.setPeluquero(peluquero);
        cita.setFechaHora(request.getFechaHora());
        cita.setEstado(EstadoCita.PENDIENTE);

        Cita guardada = citaRepository.save(cita);
        eventPublisher.publishEvent(new CitaAgendadaEvent(
                usuarioCompleto.getNombre(), usuarioCompleto.getEmail(),
                servicioCompleto.getNombre(), guardada.getFechaHora()));
        // Cita recien creada: aun no puede tener pago asociado.
        return mapearAResponseDTO(guardada, null, puedeGestionar(actual));
    }

    @Transactional(readOnly = true)
    public CitaResponseDTO obtenerCitaPorId(Integer id, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = obtenerEntidadPorId(id);
        verificarAcceso(cita, actual);
        return mapearAResponseDTO(cita, pagoDe(cita.getIdCita()), puedeGestionar(actual));
    }

    @Transactional
    public CitaResponseDTO actualizarCita(Integer id, CitaUpdateDTO request, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita citaExistente = obtenerEntidadPorId(id);
        verificarAcceso(citaExistente, actual);

        if (request.getFechaHora() != null) {
            verificarPuedeReprogramar(actual);
            citaExistente.setFechaHora(request.getFechaHora());
        }

        if (request.getEstado() != null) {
            // COMPLETADA y NO_ASISTIO no entran por aqui: son cierres, y cerrar congela el
            // importe y la comision. Si se permitiera por este camino quedarian citas
            // completadas sin precio congelado, que es exactamente el agujero que la
            // produccion no puede tener.
            if (esCierreDeTrabajo(request.getEstado())) {
                throw new IllegalArgumentException(
                        "Para marcar una cita como " + request.getEstado() + " usa el cierre de cita (PATCH /api/citas/{id}/cierre).");
            }
            citaExistente.setEstado(request.getEstado());
        }

        // Solo un ADMIN puede reasignar una cita a otro usuario.
        if (request.getUsuarioId() != null && esAdmin(actual)) {
            Usuario usuarioCompleto = usuarioRepository.findById(request.getUsuarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));
            citaExistente.setUsuario(usuarioCompleto);
        }

        if (request.getServicioId() != null) {
            Servicio servicioCompleto = servicioRepository.findById(request.getServicioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + request.getServicioId()));
            citaExistente.setServicio(servicioCompleto);
        }

        if (request.getPeluqueroId() != null) {
            citaExistente.setPeluquero(validarPeluqueroActivo(request.getPeluqueroId()));
        }

        Integer peluqueroIdParaConflicto = citaExistente.getPeluquero() != null
                ? citaExistente.getPeluquero().getIdPeluquero() : null;

        if (request.getFechaHora() != null || request.getServicioId() != null || request.getPeluqueroId() != null) {
            validarFechaFutura(citaExistente.getFechaHora());
            validarHorarioLaboral(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion());
            validarConflictoHorario(citaExistente.getFechaHora(), citaExistente.getServicio().getDuracion(), id, peluqueroIdParaConflicto);
        }

        boolean anulada = request.getEstado() == EstadoCita.ANULADA;
        boolean reprogramada = request.getFechaHora() != null || request.getServicioId() != null;

        if (anulada) {
            // Anular por PUT sigue valiendo (es lo que hace el boton de siempre del panel),
            // pero deja el mismo rastro que el cierre: quien y cuando.
            citaExistente.setFechaCierre(LocalDateTime.now(clock));
            citaExistente.setCerradaPor(actual);
        }

        Cita guardada = citaRepository.save(citaExistente);

        Usuario cliente = guardada.getUsuario();
        if (anulada) {
            eventPublisher.publishEvent(new CitaAnuladaEvent(
                    cliente.getNombre(), cliente.getEmail(),
                    guardada.getServicio().getNombre(), guardada.getFechaHora()));
        } else if (reprogramada) {
            eventPublisher.publishEvent(new CitaModificadaEvent(
                    cliente.getNombre(), cliente.getEmail(),
                    guardada.getServicio().getNombre(), guardada.getFechaHora()));
        }

        return mapearAResponseDTO(guardada, pagoDe(guardada.getIdCita()), puedeGestionar(actual));
    }

    /**
     * Cierra una cita: realizada, el cliente no vino, o anulada.
     *
     * <p>Al completar se congelan el importe y el porcentaje de comision (ver {@link Cita}).
     * Al anular se avisa al cliente por email aunque se marque que ya se le contacto: si ya
     * lo sabe, el correo no molesta, y si el aviso humano no llego a producirse, es lo unico
     * que le queda.
     */
    @Transactional
    public CitaResponseDTO cerrarCita(Integer id, CitaCierreDTO request, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita cita = obtenerEntidadPorId(id);
        verificarAcceso(cita, actual);

        EstadoCita nuevo = request.getEstado();
        if (nuevo != EstadoCita.ANULADA && !esCierreDeTrabajo(nuevo)) {
            throw new IllegalArgumentException(
                    "El cierre solo admite COMPLETADA, NO_ASISTIO o ANULADA; llego " + nuevo + ".");
        }
        // Un cliente puede renunciar a su cita, pero no declarar que se realizo: eso lo
        // dice quien trabaja, porque es lo que genera produccion y comision.
        if (actual.getRol() == Rol.USER && nuevo != EstadoCita.ANULADA) {
            throw new AccessDeniedException("Un cliente solo puede anular su cita.");
        }
        // Reabrir o corregir un cierre es de ADMIN. Si no, el mismo que cobra la comision
        // podria reescribir a posteriori lo que hizo.
        if (estaCerrada(cita.getEstado()) && !esAdmin(actual)) {
            throw new AccessDeniedException(
                    "La cita ya se cerro como " + cita.getEstado() + ". Solo un administrador puede corregirlo.");
        }
        if (nuevo == EstadoCita.COMPLETADA && cita.getFechaHora().isAfter(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("No se puede dar por realizada una cita que todavia no ha empezado.");
        }

        cita.setEstado(nuevo);
        cita.setObservaciones(normalizar(request.getObservaciones()));
        cita.setClienteContactado(Boolean.TRUE.equals(request.getClienteContactado()));
        cita.setFechaCierre(LocalDateTime.now(clock));
        cita.setCerradaPor(actual);

        if (nuevo == EstadoCita.COMPLETADA) {
            congelarImportes(cita);
        } else {
            // Un cierre corregido de COMPLETADA a otra cosa tiene que dejar de sumar.
            cita.setPrecioAplicado(null);
            cita.setComisionPorcentajeAplicado(null);
        }

        Cita guardada = citaRepository.save(cita);
        if (nuevo == EstadoCita.ANULADA) {
            Usuario cliente = guardada.getUsuario();
            eventPublisher.publishEvent(new CitaAnuladaEvent(
                    cliente.getNombre(), cliente.getEmail(),
                    guardada.getServicio().getNombre(), guardada.getFechaHora()));
        }
        return mapearAResponseDTO(guardada, pagoDe(guardada.getIdCita()), puedeGestionar(actual));
    }

    /**
     * Copia a la cita el precio del servicio y la comision que le toca al peluquero. Es una
     * copia y no una lectura en vivo: la produccion de marzo no puede cambiar porque en
     * junio se suba la tarifa o se renegocie el porcentaje.
     */
    private void congelarImportes(Cita cita) {
        cita.setPrecioAplicado(cita.getServicio().getPrecio());
        Peluquero peluquero = cita.getPeluquero();
        cita.setComisionPorcentajeAplicado(peluquero == null
                // Cita sin peluquero asignado (las hay, la FK es nullable desde la V7): hay
                // venta, pero no hay a quien comisionar.
                ? BigDecimal.ZERO
                : peluqueroService.porcentajeAplicable(peluquero.getIdPeluquero(), cita.getServicio().getIdServicio()));
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    /** COMPLETADA y NO_ASISTIO: cierres que hablan del trabajo, no de la reserva. */
    private boolean esCierreDeTrabajo(EstadoCita estado) {
        return estado == EstadoCita.COMPLETADA || estado == EstadoCita.NO_ASISTIO;
    }

    private boolean estaCerrada(EstadoCita estado) {
        return estado == EstadoCita.ANULADA || esCierreDeTrabajo(estado);
    }

    @Transactional
    public void eliminarCita(Integer id, String emailAutenticado) {
        Usuario actual = obtenerUsuarioPorEmail(emailAutenticado);
        Cita citaExistente = obtenerEntidadPorId(id);
        verificarAcceso(citaExistente, actual);

        // Capturamos los datos antes de borrar para poder notificar la anulacion.
        Usuario cliente = citaExistente.getUsuario();
        CitaAnuladaEvent evento = new CitaAnuladaEvent(
                cliente.getNombre(), cliente.getEmail(),
                citaExistente.getServicio().getNombre(), citaExistente.getFechaHora());

        citaRepository.delete(citaExistente);
        eventPublisher.publishEvent(evento);
    }

    private Cita obtenerEntidadPorId(Integer id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    private Usuario obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    /**
     * Mover una cita de fecha descuadra el hueco de un companero, asi que para un
     * PELUQUERO va detras del permiso CITA_REPROGRAMAR, que nace apagado. No toca al
     * cliente, que sigue pudiendo mover la suya, ni al ADMIN, que ve la agenda entera.
     *
     * <p>El permiso solo estrecha: quien llega hasta aqui ya paso por verificarAcceso.
     */
    private void verificarPuedeReprogramar(Usuario actual) {
        if (actual.getRol() == Rol.PELUQUERO
                && !permisoService.tienePermiso(Rol.PELUQUERO, Permiso.CITA_REPROGRAMAR)) {
            throw new AccessDeniedException(
                    "Cambiar la fecha de una cita no esta habilitado para tu rol. Pideselo a un administrador.");
        }
    }

    private boolean esAdmin(Usuario usuario) {
        return usuario.getRol() == Rol.ADMIN;
    }

    /** Quien ve los datos internos de la cita: observaciones, comision y rastro de cierre. */
    private boolean puedeGestionar(Usuario usuario) {
        return usuario.getRol() != Rol.USER;
    }

    private Optional<Peluquero> fichaDe(Usuario usuario) {
        return peluqueroService.fichaDeUsuario(usuario.getIdUsuario());
    }

    /**
     * Pueden ver y tocar una cita: un ADMIN, el cliente dueño, y el peluquero que la tiene
     * asignada. Un peluquero NO llega a las citas de un companero: el rol da acceso a su
     * agenda, no a la de la casa.
     */
    private void verificarAcceso(Cita cita, Usuario actual) {
        if (esAdmin(actual)) {
            return;
        }
        if (cita.getUsuario().getIdUsuario().equals(actual.getIdUsuario())) {
            return;
        }
        if (esSuAgenda(cita, actual)) {
            return;
        }
        throw new AccessDeniedException("No tienes permiso para acceder a este recurso.");
    }

    private boolean esSuAgenda(Cita cita, Usuario actual) {
        if (actual.getRol() != Rol.PELUQUERO || cita.getPeluquero() == null) {
            return false;
        }
        return fichaDe(actual)
                .map(ficha -> ficha.getIdPeluquero().equals(cita.getPeluquero().getIdPeluquero()))
                .orElse(false);
    }

    /**
     * @param pago el pago de la cita, o null si no tiene. Se recibe el pago entero y no solo
     *             su estado para poder exponer tambien su id: es lo que necesitan los
     *             clientes para pedir el recibo, y asi no cuesta una peticion por cita.
     */
    private CitaResponseDTO mapearAResponseDTO(Cita cita, Pago pago, boolean incluirGestion) {
        CitaResponseDTO dto = new CitaResponseDTO();
        dto.setIdCita(cita.getIdCita());
        dto.setFechaHora(cita.getFechaHora());
        dto.setEstado(cita.getEstado());
        dto.setUsuario(UsuarioResponseDTO.desde(cita.getUsuario()));
        dto.setServicio(ServicioResponseDTO.desde(cita.getServicio()));
        dto.setPeluquero(PeluqueroResponseDTO.desde(cita.getPeluquero()));
        dto.setEstadoPago(pago != null ? pago.getEstadoPago() : null);
        dto.setIdPago(pago != null ? pago.getIdPago() : null);
        if (incluirGestion) {
            dto.setFechaCierre(cita.getFechaCierre());
            dto.setObservaciones(cita.getObservaciones());
            dto.setClienteContactado(cita.getClienteContactado());
            dto.setCerradaPor(cita.getCerradaPor() != null ? cita.getCerradaPor().getNombre() : null);
            dto.setPrecioAplicado(cita.getPrecioAplicado());
            dto.setComisionPorcentajeAplicado(cita.getComisionPorcentajeAplicado());
        }
        return dto;
    }

    private void validarFechaFutura(LocalDateTime fechaHora) {
        if (fechaHora.isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("No se puede agendar una cita en el pasado.");
        }
    }

    private void validarHorarioLaboral(LocalDateTime inicio, Integer duracionMinutos) {
        LocalTime horaInicio = inicio.toLocalTime();
        LocalTime horaFin = horaInicio.plusMinutes(duracionMinutos);

        String motivoCierre = calendario.motivoCierre(inicio.toLocalDate());
        if (motivoCierre != null) {
            throw new IllegalArgumentException("La peluqueria no abre el " + inicio.toLocalDate() + ": " + motivoCierre + ".");
        }

        if (horaInicio.isBefore(horario.getApertura())) {
            throw new IllegalArgumentException("La cita no puede ser antes de las " + horario.getApertura() + ".");
        }

        if (horaFin.isAfter(horario.getCierre())) {
            throw new IllegalArgumentException("La cita (incluyendo la duracion del servicio) no puede terminar despues de las " + horario.getCierre() + ".");
        }
    }

    private void validarConflictoHorario(LocalDateTime inicio, Integer duracionMinutos, Integer idExcluir, Integer peluqueroId) {
        if (hayConflicto(inicio, inicio.plusMinutes(duracionMinutos), idExcluir, peluqueroId)) {
            throw new ConflictoHorarioException("Ya existe una cita agendada en ese horario. Por favor elige otro horario.");
        }
    }
}
