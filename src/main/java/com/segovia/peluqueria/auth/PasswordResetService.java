package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.notificacion.evento.PasswordCambiadaEvent;
import com.segovia.peluqueria.notificacion.evento.PasswordResetSolicitadoEvent;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Flujo de recuperacion de contrasena:
 * <ol>
 *   <li>{@link #solicitarReset(String)} genera un token aleatorio, guarda su hash y publica
 *       el evento que dispara el correo. Es <b>anti-enumeracion</b>: nunca revela si el email
 *       existe (no lanza excepcion si no hay usuario).</li>
 *   <li>{@link #resetearPassword(String, String)} valida el token (vigente y de un solo uso),
 *       cambia la contrasena, invalida las sesiones (sube tokenVersion) y marca el token usado.</li>
 * </ol>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendUrl;
    private final long expiracionMinutos;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher eventPublisher,
                                @Value("${peluqueria.frontend-url}") String frontendUrl,
                                @Value("${peluqueria.reset.expiracion-minutos:30}") long expiracionMinutos) {
        this.tokenRepository = tokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.frontendUrl = frontendUrl;
        this.expiracionMinutos = expiracionMinutos;
    }

    @Transactional
    public void solicitarReset(String email) {
        Optional<Usuario> posible = usuarioRepository.findByEmail(email);
        // Anti-enumeracion: si no existe (o esta inactivo) salimos en silencio; el controller
        // responde 200 igualmente para no revelar que emails estan registrados.
        if (posible.isEmpty() || !posible.get().getActivo()) {
            log.info("Solicitud de reset para email no elegible (no se envia correo).");
            return;
        }
        Usuario usuario = posible.get();

        // Solo el ultimo enlace debe quedar vigente.
        tokenRepository.invalidarVigentesDe(usuario);

        String tokenPlano = generarTokenPlano();

        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(tokenPlano));
        token.setUsuario(usuario);
        token.setCreadoEn(LocalDateTime.now());
        token.setExpiraEn(LocalDateTime.now().plusMinutes(expiracionMinutos));
        token.setUsado(false);
        tokenRepository.save(token);

        String enlace = frontendUrl + "/reset?token=" + tokenPlano;
        eventPublisher.publishEvent(
                new PasswordResetSolicitadoEvent(usuario.getNombre(), usuario.getEmail(), enlace));
        log.info("Token de reset generado para el usuario id={}", usuario.getIdUsuario());
    }

    @Transactional
    public void resetearPassword(String tokenPlano, String nuevaPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(tokenPlano))
                .filter(t -> !t.isUsado())
                .filter(t -> !t.estaCaducado())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El enlace de recuperacion no es valido o ha caducado."));

        Usuario usuario = token.getUsuario();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        // Invalida cualquier sesion previa (el token JWT viejo deja de ser vigente).
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuarioRepository.save(usuario);

        token.setUsado(true);
        tokenRepository.save(token);

        eventPublisher.publishEvent(
                new PasswordCambiadaEvent(usuario.getNombre(), usuario.getEmail()));
        log.info("Contrasena restablecida via token para el usuario id={}", usuario.getIdUsuario());
    }

    private String generarTokenPlano() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 forma parte del JDK; este caso es practicamente imposible.
            throw new IllegalStateException("Algoritmo de hash no disponible", e);
        }
    }
}
