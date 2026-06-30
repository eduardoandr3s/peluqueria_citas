package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.exception.InvalidRefreshTokenException;
import com.segovia.peluqueria.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Ciclo de vida de los refresh tokens (rotacion con deteccion de reuso):
 * <ul>
 *   <li>{@link #emitirNuevaFamilia(Usuario)}: al hacer login, crea un refresh en una familia nueva.</li>
 *   <li>{@link #rotar(String)}: valida el refresh presentado, lo invalida y emite uno nuevo en la
 *       misma familia. Si el token ya estaba revocado (reuso de un token rotado), revoca la familia
 *       entera. Si el {@code tokenVersion} del usuario cambio (password/rol), el refresh es invalido.</li>
 *   <li>{@link #revocar(String)}: logout; invalida el refresh presentado (idempotente).</li>
 * </ul>
 * Solo se almacena el hash SHA-256 del token; el valor en claro nunca se persiste.
 *
 * <p>Nota: la rotacion es estricta. Si el cliente reintenta un refresh con un token ya rotado
 * (p.ej. tras perder la respuesta por red), se interpreta como reuso y se revoca la familia; el
 * cliente debera volver a iniciar sesion.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokenRepository;
    private final long expiracionDias;

    public RefreshTokenService(RefreshTokenRepository tokenRepository,
                               @Value("${peluqueria.refresh.expiracion-dias:30}") long expiracionDias) {
        this.tokenRepository = tokenRepository;
        this.expiracionDias = expiracionDias;
    }

    @Transactional
    public String emitirNuevaFamilia(Usuario usuario) {
        return crearToken(usuario, UUID.randomUUID().toString(), usuario.getTokenVersion());
    }

    @Transactional
    public RotacionResult rotar(String tokenPlano) {
        RefreshToken token = tokenRepository.findByTokenHash(hash(tokenPlano))
                .orElseThrow(() -> new InvalidRefreshTokenException("El refresh token no es valido."));

        if (token.isRevocado()) {
            // Reuso de un token ya rotado: posible robo. Se invalida toda la familia.
            tokenRepository.revocarFamilia(token.getFamilia());
            log.warn("Reuso de refresh token detectado; familia revocada (usuario id={}).",
                    token.getUsuario().getIdUsuario());
            throw new InvalidRefreshTokenException("El refresh token fue reutilizado; sesiones revocadas.");
        }
        if (token.estaCaducado()) {
            throw new InvalidRefreshTokenException("El refresh token ha caducado.");
        }

        Usuario usuario = token.getUsuario();
        if (!token.getTokenVersion().equals(usuario.getTokenVersion())) {
            // La password o el rol cambiaron tras emitir este refresh: deja de ser valido.
            throw new InvalidRefreshTokenException("La sesion ha expirado por un cambio de credenciales.");
        }

        token.setRevocado(true);
        tokenRepository.save(token);

        String nuevoPlano = crearToken(usuario, token.getFamilia(), usuario.getTokenVersion());
        return new RotacionResult(usuario, nuevoPlano);
    }

    @Transactional
    public void revocar(String tokenPlano) {
        tokenRepository.findByTokenHash(hash(tokenPlano)).ifPresent(token -> {
            token.setRevocado(true);
            tokenRepository.save(token);
        });
    }

    private String crearToken(Usuario usuario, String familia, Integer tokenVersion) {
        String plano = generarTokenPlano();
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(plano));
        token.setUsuario(usuario);
        token.setFamilia(familia);
        token.setTokenVersion(tokenVersion);
        token.setCreadoEn(LocalDateTime.now());
        token.setExpiraEn(LocalDateTime.now().plusDays(expiracionDias));
        token.setRevocado(false);
        tokenRepository.save(token);
        return plano;
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

    /** Resultado de una rotacion: el usuario dueño y el nuevo refresh token en claro. */
    public record RotacionResult(Usuario usuario, String refreshTokenPlano) {
    }
}
