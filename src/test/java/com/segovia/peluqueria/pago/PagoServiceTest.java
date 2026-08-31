package com.segovia.peluqueria.pago;

import com.segovia.peluqueria.cita.Cita;
import com.segovia.peluqueria.cita.CitaRepository;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.exception.EstadoInvalidoException;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.notificacion.evento.PagoConfirmadoEvent;
import com.segovia.peluqueria.pago.PaymentGateway.EventoPasarela;
import com.segovia.peluqueria.pago.PaymentGateway.IntentPasarela;
import com.segovia.peluqueria.pago.dto.PagoResponseDTO;
import com.segovia.peluqueria.pago.dto.PaymentIntentResponseDTO;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.peluquero.PeluqueroRepository;
import com.segovia.peluqueria.permiso.Permiso;
import com.segovia.peluqueria.permiso.PermisoService;
import com.segovia.peluqueria.usuario.Rol;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoServiceTest {

    private static final String EMAIL_ADMIN = "admin@test.com";
    private static final String EMAIL_CLIENTE = "carlos@test.com";
    private static final String EMAIL_OTRO = "otro@test.com";
    private static final String EMAIL_PELUQUERO = "laura@test.com";
    private static final String INTENT_ID = "pi_123";
    private static final String EVENTO_ID = "evt_123";
    private static final String FIRMA = "firma-test";
    private static final String PAYLOAD = "{}";

    private PagoRepository pagoRepository;
    private CitaRepository citaRepository;
    private UsuarioRepository usuarioRepository;
    private StripeEventoRepository stripeEventoRepository;
    private PaymentGateway paymentGateway;
    private ApplicationEventPublisher eventPublisher;
    private ReciboPdfGenerador reciboPdfGenerador;
    private PeluqueroRepository peluqueroRepository;
    private PermisoService permisoService;
    private PagoService pagoService;

    @BeforeEach
    void setUp() {
        pagoRepository = mock(PagoRepository.class);
        citaRepository = mock(CitaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        stripeEventoRepository = mock(StripeEventoRepository.class);
        paymentGateway = mock(PaymentGateway.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        // El generador va mockeado: aqui se prueba quien puede pedir un recibo y cuando, no
        // como sale el PDF. De eso se encarga ReciboPdfGeneradorTest, que si lo renderiza.
        reciboPdfGenerador = mock(ReciboPdfGenerador.class);
        peluqueroRepository = mock(PeluqueroRepository.class);
        permisoService = mock(PermisoService.class);
        // Por defecto ninguna cuenta tiene ficha: los tests que cobran como peluquero la
        // stubbean. Un ADMIN no pasa por aqui, asi que el resto de la clase no se entera.
        when(peluqueroRepository.findByUsuarioIdUsuario(anyInt())).thenReturn(Optional.empty());
        pagoService = new PagoService(pagoRepository, citaRepository, usuarioRepository,
                stripeEventoRepository, paymentGateway, eventPublisher, reciboPdfGenerador,
                peluqueroRepository, permisoService);

        Usuario admin = new Usuario();
        admin.setIdUsuario(99);
        admin.setEmail(EMAIL_ADMIN);
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(admin));
    }

    private Usuario crearCliente() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setNombre("Carlos");
        usuario.setEmail(EMAIL_CLIENTE);
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }

    private Cita crearCitaPendiente() {
        Servicio servicio = new Servicio();
        servicio.setIdServicio(1);
        servicio.setNombre("Corte");
        servicio.setDuracion(30);
        servicio.setPrecio(new BigDecimal("15.00"));
        servicio.setActivo(true);

        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setUsuario(crearCliente());
        cita.setServicio(servicio);
        cita.setFechaHora(LocalDateTime.now().plusDays(3));
        cita.setEstado(EstadoCita.PENDIENTE);
        return cita;
    }

    private Pago crearPagoPendiente(Cita cita) {
        Pago pago = new Pago();
        pago.setIdPago(10);
        pago.setCita(cita);
        pago.setMonto(cita.getServicio().getPrecio());
        pago.setMetodoPago(MetodoPago.TARJETA);
        pago.setEstadoPago(EstadoPago.PENDIENTE);
        pago.setReferenciaExterna(INTENT_ID);
        pago.setFechaCreacion(LocalDateTime.now());
        return pago;
    }

    private EventoPasarela evento(String tipo, String paymentIntentId) {
        return new EventoPasarela(EVENTO_ID, tipo, paymentIntentId);
    }

    // ---------- crearPaymentIntent ----------

    @Test
    void crearPaymentIntent_exitoso_cobraElPrecioDelServicio() {
        Cita cita = crearCitaPendiente();
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.empty());
        when(paymentGateway.crearIntent(any(), any(), any()))
                .thenReturn(new IntentPasarela(INTENT_ID, "secret_abc", "requires_payment_method"));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            p.setIdPago(10);
            return p;
        });

        PaymentIntentResponseDTO resultado = pagoService.crearPaymentIntent(1, EMAIL_CLIENTE);

        assertEquals("secret_abc", resultado.getClientSecret());
        assertEquals(INTENT_ID, resultado.getPaymentIntentId());
        assertEquals(10, resultado.getPagoId());
        // El importe sale del precio del servicio en BD, nunca del cliente.
        verify(paymentGateway).crearIntent(eq(new BigDecimal("15.00")), any(), eq(1));

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(EstadoPago.PENDIENTE, captor.getValue().getEstadoPago());
        assertEquals(MetodoPago.TARJETA, captor.getValue().getMetodoPago());
        assertEquals(INTENT_ID, captor.getValue().getReferenciaExterna());
    }

    @Test
    void crearPaymentIntent_citaAnulada_lanzaError() {
        Cita cita = crearCitaPendiente();
        cita.setEstado(EstadoCita.ANULADA);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.crearPaymentIntent(1, EMAIL_CLIENTE));
        verify(paymentGateway, never()).crearIntent(any(), any(), any());
    }

    @Test
    void crearPaymentIntent_citaDeOtroUsuario_lanzaAccessDenied() {
        Cita cita = crearCitaPendiente();
        Usuario otro = new Usuario();
        otro.setIdUsuario(2);
        otro.setEmail(EMAIL_OTRO);
        otro.setRol(Rol.USER);
        otro.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_OTRO)).thenReturn(Optional.of(otro));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        assertThrows(AccessDeniedException.class,
                () -> pagoService.crearPaymentIntent(1, EMAIL_OTRO));
    }

    @Test
    void crearPaymentIntent_citaYaPagada_lanzaError() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        pago.setEstadoPago(EstadoPago.PAGADO);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.crearPaymentIntent(1, EMAIL_CLIENTE));
        verify(paymentGateway, never()).crearIntent(any(), any(), any());
    }

    @Test
    void crearPaymentIntent_conIntentPendienteVivo_reutilizaElMismoIntent() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));
        when(paymentGateway.recuperarIntent(INTENT_ID))
                .thenReturn(new IntentPasarela(INTENT_ID, "secret_abc", "requires_payment_method"));

        PaymentIntentResponseDTO resultado = pagoService.crearPaymentIntent(1, EMAIL_CLIENTE);

        assertEquals("secret_abc", resultado.getClientSecret());
        assertEquals(INTENT_ID, resultado.getPaymentIntentId());
        verify(paymentGateway, never()).crearIntent(any(), any(), any());
    }

    @Test
    void crearPaymentIntent_conIntentCanceladoEnStripe_creaOtroSobreElMismoRegistro() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));
        when(paymentGateway.recuperarIntent(INTENT_ID))
                .thenReturn(new IntentPasarela(INTENT_ID, "secret_abc", "canceled"));
        when(paymentGateway.crearIntent(any(), any(), any()))
                .thenReturn(new IntentPasarela("pi_456", "secret_def", "requires_payment_method"));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentResponseDTO resultado = pagoService.crearPaymentIntent(1, EMAIL_CLIENTE);

        assertEquals("pi_456", resultado.getPaymentIntentId());
        assertEquals("pi_456", pago.getReferenciaExterna());
        assertEquals(EstadoPago.PENDIENTE, pago.getEstadoPago());
    }

    @Test
    void crearPaymentIntent_conIntentYaCobrado_lanzaError() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));
        // El webhook aun no ha llegado pero Stripe ya lo da por cobrado.
        when(paymentGateway.recuperarIntent(INTENT_ID))
                .thenReturn(new IntentPasarela(INTENT_ID, "secret_abc", "succeeded"));

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.crearPaymentIntent(1, EMAIL_CLIENTE));
        verify(paymentGateway, never()).crearIntent(any(), any(), any());
    }

    // ---------- procesarWebhook ----------

    @Test
    void procesarWebhook_firmaInvalida_lanzaError() {
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenThrow(new IllegalArgumentException("Firma del webhook invalida."));

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.procesarWebhook(PAYLOAD, FIRMA));
        verifyNoInteractions(stripeEventoRepository, pagoRepository);
    }

    @Test
    void procesarWebhook_eventoDuplicado_noReprocesa() {
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenReturn(evento("payment_intent.succeeded", INTENT_ID));
        when(stripeEventoRepository.existsById(EVENTO_ID)).thenReturn(true);

        pagoService.procesarWebhook(PAYLOAD, FIRMA);

        verifyNoInteractions(pagoRepository, citaRepository, eventPublisher);
        verify(stripeEventoRepository, never()).save(any());
    }

    @Test
    void procesarWebhook_pagoCompletado_confirmaCitaYNotifica() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenReturn(evento("payment_intent.succeeded", INTENT_ID));
        when(stripeEventoRepository.existsById(EVENTO_ID)).thenReturn(false);
        when(pagoRepository.findByReferenciaExterna(INTENT_ID)).thenReturn(Optional.of(pago));

        pagoService.procesarWebhook(PAYLOAD, FIRMA);

        assertEquals(EstadoPago.PAGADO, pago.getEstadoPago());
        assertNotNull(pago.getFechaPago());
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
        verify(citaRepository).save(cita);
        verify(stripeEventoRepository).save(any(StripeEvento.class));

        ArgumentCaptor<PagoConfirmadoEvent> captor = ArgumentCaptor.forClass(PagoConfirmadoEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(EMAIL_CLIENTE, captor.getValue().clienteEmail());
        assertEquals("Corte", captor.getValue().servicioNombre());
    }

    @Test
    void procesarWebhook_pagoYaMarcadoComoPagado_noVuelveANotificar() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        pago.setEstadoPago(EstadoPago.PAGADO);
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenReturn(evento("payment_intent.succeeded", INTENT_ID));
        when(stripeEventoRepository.existsById(EVENTO_ID)).thenReturn(false);
        when(pagoRepository.findByReferenciaExterna(INTENT_ID)).thenReturn(Optional.of(pago));

        pagoService.procesarWebhook(PAYLOAD, FIRMA);

        verifyNoInteractions(eventPublisher);
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void procesarWebhook_intentDesconocido_respondeSinErrorYRegistraElEvento() {
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenReturn(evento("payment_intent.succeeded", "pi_ajeno"));
        when(stripeEventoRepository.existsById(EVENTO_ID)).thenReturn(false);
        when(pagoRepository.findByReferenciaExterna("pi_ajeno")).thenReturn(Optional.empty());

        // Un intent que no es nuestro no debe provocar 4xx/5xx: Stripe reintentaria durante dias.
        assertDoesNotThrow(() -> pagoService.procesarWebhook(PAYLOAD, FIRMA));
        verify(stripeEventoRepository).save(any(StripeEvento.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void procesarWebhook_intentCancelado_marcaElPagoComoCancelado() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenReturn(evento("payment_intent.canceled", INTENT_ID));
        when(stripeEventoRepository.existsById(EVENTO_ID)).thenReturn(false);
        when(pagoRepository.findByReferenciaExterna(INTENT_ID)).thenReturn(Optional.of(pago));

        pagoService.procesarWebhook(PAYLOAD, FIRMA);

        assertEquals(EstadoPago.CANCELADO, pago.getEstadoPago());
        verify(pagoRepository).save(pago);
        // La cita sigue viva: puede pagarse con otro intent o en el local.
        assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void procesarWebhook_sinObjetoDeserializable_noRegistraElEvento() {
        when(paymentGateway.validarWebhook(PAYLOAD, FIRMA))
                .thenReturn(evento("payment_intent.succeeded", null));
        when(stripeEventoRepository.existsById(EVENTO_ID)).thenReturn(false);

        assertDoesNotThrow(() -> pagoService.procesarWebhook(PAYLOAD, FIRMA));

        // Sin registrar: un reenvio del evento debe poder reintentarlo.
        verify(stripeEventoRepository, never()).save(any());
        verifyNoInteractions(pagoRepository);
    }

    // ---------- registrarPagoManual ----------

    @Test
    void registrarPagoManual_sinSerAdmin_lanzaAccessDenied() {
        Usuario cliente = crearCliente();
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cliente));

        assertThrows(AccessDeniedException.class,
                () -> pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_CLIENTE));
    }

    @Test
    void registrarPagoManual_exitoso_confirmaCitaYNotifica() {
        Cita cita = crearCitaPendiente();
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.empty());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        PagoResponseDTO resultado = pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_ADMIN);

        assertEquals(EstadoPago.PAGADO, resultado.getEstadoPago());
        assertEquals(MetodoPago.EFECTIVO, resultado.getMetodoPago());
        assertEquals(new BigDecimal("15.00"), resultado.getMonto());
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
        verify(eventPublisher).publishEvent(any(PagoConfirmadoEvent.class));
    }

    @Test
    void registrarPagoManual_conIntentOnlinePendiente_loCancelaEnStripe() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_ADMIN);

        // Evita el doble cobro: el cliente ya no puede completar el pago online abandonado.
        verify(paymentGateway).cancelarIntent(INTENT_ID);
        assertEquals(EstadoPago.PAGADO, pago.getEstadoPago());
        assertEquals(MetodoPago.EFECTIVO, pago.getMetodoPago());
    }

    @Test
    void registrarPagoManual_citaYaPagada_lanzaError() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        pago.setEstadoPago(EstadoPago.PAGADO);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_ADMIN));
    }

    // ---------- el permiso PAGO_MANUAL_REGISTRAR ----------

    @Test
    void registrarPagoManual_peluqueroSinElPermiso_lanzaAccessDenied() {
        crearPeluqueroConFicha();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_PELUQUERO));

        assertTrue(ex.getMessage().contains("no esta habilitado"));
        // Se corta antes de tocar la base de datos: el permiso es del rol, no de la cita.
        verify(citaRepository, never()).findById(anyInt());
    }

    @Test
    void registrarPagoManual_peluqueroConElPermisoYSuCita_cobra() {
        Peluquero ficha = crearPeluqueroConFicha();
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR)).thenReturn(true);

        Cita cita = crearCitaPendiente();
        cita.setPeluquero(ficha);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.empty());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        PagoResponseDTO resultado = pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_PELUQUERO);

        assertEquals(EstadoPago.PAGADO, resultado.getEstadoPago());
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void registrarPagoManual_peluqueroConElPermisoPeroCitaDeOtro_lanzaAccessDenied() {
        crearPeluqueroConFicha();
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR)).thenReturn(true);

        Peluquero companero = new Peluquero();
        companero.setIdPeluquero(77);
        Cita cita = crearCitaPendiente();
        cita.setPeluquero(companero);
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));

        // El permiso dice que su rol puede cobrar, no que pueda cobrar lo de un companero.
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_PELUQUERO));
        assertTrue(ex.getMessage().contains("tu agenda"));
    }

    @Test
    void registrarPagoManual_peluqueroConElPermisoYCitaSinAsignar_lanzaAccessDenied() {
        crearPeluqueroConFicha();
        when(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR)).thenReturn(true);
        when(citaRepository.findById(1)).thenReturn(Optional.of(crearCitaPendiente()));

        assertThrows(AccessDeniedException.class,
                () -> pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_PELUQUERO));
    }

    @Test
    void registrarPagoManual_adminNoPasaPorElPermiso() {
        Cita cita = crearCitaPendiente();
        when(citaRepository.findById(1)).thenReturn(Optional.of(cita));
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.empty());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        // Cobra una cita sin peluquero asignado y con todos los permisos apagados.
        pagoService.registrarPagoManual(1, MetodoPago.EFECTIVO, EMAIL_ADMIN);

        verify(permisoService, never()).tienePermiso(any(), any());
    }

    /** Cuenta con rol PELUQUERO vinculada a la ficha 5, ya registrada en los mocks. */
    private Peluquero crearPeluqueroConFicha() {
        Usuario cuenta = new Usuario();
        cuenta.setIdUsuario(50);
        cuenta.setNombre("Laura");
        cuenta.setEmail(EMAIL_PELUQUERO);
        cuenta.setRol(Rol.PELUQUERO);
        cuenta.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_PELUQUERO)).thenReturn(Optional.of(cuenta));

        Peluquero ficha = new Peluquero();
        ficha.setIdPeluquero(5);
        ficha.setNombre("Laura");
        when(peluqueroRepository.findByUsuarioIdUsuario(50)).thenReturn(Optional.of(ficha));
        return ficha;
    }

    // ---------- reembolsar ----------

    @Test
    void reembolsar_sinSerAdmin_lanzaAccessDenied() {
        Usuario cliente = crearCliente();
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cliente));

        assertThrows(AccessDeniedException.class,
                () -> pagoService.reembolsar(1, EMAIL_CLIENTE));
    }

    @Test
    void reembolsar_pagoNoRealizado_lanzaError() {
        Pago pago = crearPagoPendiente(crearCitaPendiente());
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.reembolsar(1, EMAIL_ADMIN));
        verify(paymentGateway, never()).reembolsar(any());
    }

    @Test
    void reembolsar_pagoConTarjeta_reembolsaEnStripe() {
        Pago pago = crearPagoPendiente(crearCitaPendiente());
        pago.setEstadoPago(EstadoPago.PAGADO);
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));

        pagoService.reembolsar(1, EMAIL_ADMIN);

        verify(paymentGateway).reembolsar(INTENT_ID);
        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstadoPago());
    }

    @Test
    void reembolsar_pagoEnEfectivo_noLlamaAStripe() {
        Pago pago = crearPagoPendiente(crearCitaPendiente());
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setMetodoPago(MetodoPago.EFECTIVO);
        pago.setReferenciaExterna(null);
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.of(pago));

        pagoService.reembolsar(1, EMAIL_ADMIN);

        verify(paymentGateway, never()).reembolsar(any());
        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstadoPago());
    }

    @Test
    void reembolsar_sinPagoRegistrado_lanzaNotFound() {
        when(pagoRepository.findByCitaIdCita(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pagoService.reembolsar(1, EMAIL_ADMIN));
    }

    // ---------- listarPagos ----------
    // El filtrado real (rango, estado, metodo) se ejercita contra Postgres en
    // PagosIntegrationTest; aqui solo la validacion del rango y el mapeo a DTO.

    @Test
    void listarPagos_devuelveLosPagosMapeadosADTO() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        pago.setEstadoPago(EstadoPago.PAGADO);
        when(pagoRepository.findAll(ArgumentMatchers.<Specification<Pago>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pago)));

        Page<PagoResponseDTO> resultado = pagoService.listarPagos(
                null, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, resultado.getTotalElements());
        PagoResponseDTO dto = resultado.getContent().get(0);
        assertEquals(10, dto.getIdPago());
        assertEquals(1, dto.getCitaId());
        assertEquals(EstadoPago.PAGADO, dto.getEstadoPago());
        assertEquals(new BigDecimal("15.00"), dto.getMonto());
    }

    @Test
    void listarPagos_rangoInvertido_lanzaError() {
        LocalDateTime desde = LocalDateTime.of(2026, 6, 30, 0, 0);
        LocalDateTime hasta = LocalDateTime.of(2026, 6, 1, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.listarPagos(desde, hasta, null, null, PageRequest.of(0, 20)));
        verify(pagoRepository, never()).findAll(ArgumentMatchers.<Specification<Pago>>any(), any(Pageable.class));
    }

    // ---------- generarRecibo ----------

    /** Pago cobrado, listo para pedirle el recibo, y su cliente registrado en el mock. */
    private Pago prepararPagoCobrado() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setFechaPago(LocalDateTime.now());
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(pagoRepository.findById(10)).thenReturn(Optional.of(pago));
        return pago;
    }

    @Test
    void generarRecibo_elDuenoLoDescarga() {
        Pago pago = prepararPagoCobrado();
        when(reciboPdfGenerador.generar(pago)).thenReturn(new byte[] { 1, 2, 3 });
        when(reciboPdfGenerador.nombreFichero(pago)).thenReturn("recibo-10.pdf");

        var recibo = pagoService.generarRecibo(10, EMAIL_CLIENTE);

        assertArrayEquals(new byte[] { 1, 2, 3 }, recibo.contenido());
        assertEquals("recibo-10.pdf", recibo.nombreFichero());
    }

    @Test
    void generarRecibo_unAdminDescargaElDeCualquiera() {
        Pago pago = prepararPagoCobrado();
        when(reciboPdfGenerador.generar(pago)).thenReturn(new byte[] { 1 });

        assertDoesNotThrow(() -> pagoService.generarRecibo(10, EMAIL_ADMIN));
    }

    @Test
    void generarRecibo_deOtroCliente_devuelve403() {
        prepararPagoCobrado();
        Usuario otro = new Usuario();
        otro.setIdUsuario(2);
        otro.setEmail(EMAIL_OTRO);
        otro.setRol(Rol.USER);
        otro.setActivo(true);
        when(usuarioRepository.findByEmail(EMAIL_OTRO)).thenReturn(Optional.of(otro));

        assertThrows(AccessDeniedException.class, () -> pagoService.generarRecibo(10, EMAIL_OTRO));
        // Ni siquiera se llega a generar: el PDF llevaria datos de otro cliente.
        verify(reciboPdfGenerador, never()).generar(any());
    }

    @Test
    void generarRecibo_pagoInexistente_devuelve404() {
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(crearCliente()));
        when(pagoRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pagoService.generarRecibo(999, EMAIL_CLIENTE));
    }

    @Test
    void generarRecibo_pagoReembolsado_siTieneRecibo() {
        Pago pago = prepararPagoCobrado();
        pago.setEstadoPago(EstadoPago.REEMBOLSADO);
        when(reciboPdfGenerador.generar(pago)).thenReturn(new byte[] { 1 });

        assertDoesNotThrow(() -> pagoService.generarRecibo(10, EMAIL_CLIENTE));
    }

    @Test
    void generarRecibo_pagoPendiente_devuelve409() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(pagoRepository.findById(10)).thenReturn(Optional.of(pago));

        // Un justificante de un pago que no ha entrado afirmaria algo falso.
        assertThrows(EstadoInvalidoException.class, () -> pagoService.generarRecibo(10, EMAIL_CLIENTE));
        verify(reciboPdfGenerador, never()).generar(any());
    }

    @Test
    void generarRecibo_pagoCancelado_devuelve409() {
        Cita cita = crearCitaPendiente();
        Pago pago = crearPagoPendiente(cita);
        pago.setEstadoPago(EstadoPago.CANCELADO);
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(cita.getUsuario()));
        when(pagoRepository.findById(10)).thenReturn(Optional.of(pago));

        assertThrows(EstadoInvalidoException.class, () -> pagoService.generarRecibo(10, EMAIL_CLIENTE));
    }
}
