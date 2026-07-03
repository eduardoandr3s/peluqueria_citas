package com.segovia.peluqueria.pago;

import com.segovia.peluqueria.pago.dto.CrearPaymentIntentDTO;
import com.segovia.peluqueria.pago.dto.PagoManualRequestDTO;
import com.segovia.peluqueria.pago.dto.PagoResponseDTO;
import com.segovia.peluqueria.pago.dto.PaymentIntentResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
