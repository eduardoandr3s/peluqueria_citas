package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.auth.dto.RecuperarPasswordRequestDTO;
import com.segovia.peluqueria.auth.dto.ResetPasswordRequestDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints publicos de recuperacion de contrasena. Van bajo /api/auth/** (permitAll en
 * SecurityConfig) y estan protegidos por {@code RateLimitFilter}. Se separan de
 * {@code AuthController} para no alterar su firma ni sus tests.
 */
@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/recuperar")
    public Map<String, String> recuperar(@Valid @RequestBody RecuperarPasswordRequestDTO request) {
        passwordResetService.solicitarReset(request.getEmail());
        // Respuesta fija (anti-enumeracion): no revela si el email existe.
        return Map.of("mensaje",
                "Si el email esta registrado, recibiras instrucciones para restablecer tu contrasena.");
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@Valid @RequestBody ResetPasswordRequestDTO request) {
        passwordResetService.resetearPassword(request.getToken(), request.getPassword());
        return Map.of("mensaje", "Contrasena actualizada. Ya puedes iniciar sesion.");
    }
}
