package com.segovia.peluqueria.pago;

import com.segovia.peluqueria.pago.dto.CrearPaymentIntentDTO;
import com.segovia.peluqueria.pago.dto.PagoManualRequestDTO;
import com.segovia.peluqueria.pago.dto.PagoResponseDTO;
import com.segovia.peluqueria.pago.dto.PaymentIntentResponseDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/crear-intent")
    public PaymentIntentResponseDTO crearPaymentIntent(@Valid @RequestBody CrearPaymentIntentDTO request,
                                                        Authentication authentication) {
        return pagoService.crearPaymentIntent(request.getCitaId(), authentication.getName());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader("Stripe-Signature") String sigHeader) {
        pagoService.procesarWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/manual")
    public PagoResponseDTO registrarPagoManual(@Valid @RequestBody PagoManualRequestDTO request,
                                                Authentication authentication) {
        return pagoService.registrarPagoManual(request.getCitaId(), request.getMetodoPago(), authentication.getName());
    }

    /**
     * Listado de pagos para el panel de administracion. Todos los filtros son opcionales:
     * sin ninguno devuelve los ultimos pagos registrados. Las fechas son dias (ISO), y el
     * rango es [desde, hasta] con ambos extremos incluidos.
     */
    @GetMapping
    public Page<PagoResponseDTO> listarPagos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoPago estado,
            @RequestParam(required = false) MetodoPago metodo,
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return pagoService.listarPagos(
                desde != null ? desde.atStartOfDay() : null,
                // 'hasta' llega como dia inclusive; el servicio corta en exclusivo, asi que se
                // pasa el comienzo del dia siguiente para no dejar fuera los pagos de ese dia.
                hasta != null ? hasta.plusDays(1).atStartOfDay() : null,
                estado, metodo, pageable);
    }

    /**
     * Recibo en PDF del pago. Lo puede descargar el dueno de la cita o un ADMIN, y solo
     * existe para pagos cobrados o reembolsados (ver {@code PagoService.generarRecibo}).
     *
     * <p>Se sirve como {@code attachment}: el navegador lo descarga en vez de abrirlo, que
     * es lo que se espera de un justificante.
     */
    @GetMapping(path = "/{id}/recibo", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarRecibo(@PathVariable Integer id,
                                                  Authentication authentication) {
        PagoService.Recibo recibo = pagoService.generarRecibo(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // Con ContentDisposition en vez de la cabecera a mano: escapa el nombre el.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(recibo.nombreFichero())
                                .build()
                                .toString())
                .body(recibo.contenido());
    }

    @GetMapping("/cita/{citaId}")
    public PagoResponseDTO obtenerPagoPorCita(@PathVariable Integer citaId, Authentication authentication) {
        return pagoService.obtenerPagoPorCita(citaId, authentication.getName());
    }

    @PostMapping("/{citaId}/reembolsar")
    public ResponseEntity<Void> reembolsar(@PathVariable Integer citaId, Authentication authentication) {
        pagoService.reembolsar(citaId, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
