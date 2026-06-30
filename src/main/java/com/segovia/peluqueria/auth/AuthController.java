package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.auth.dto.AuthResponseDTO;
import com.segovia.peluqueria.auth.dto.LoginRequestDTO;
import com.segovia.peluqueria.auth.dto.RefreshRequestDTO;
import com.segovia.peluqueria.exception.InvalidRefreshTokenException;
import com.segovia.peluqueria.security.JwtService;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.segovia.peluqueria.usuario.UsuarioService;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UsuarioService usuarioService,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/registro")
    public UsuarioResponseDTO registro(@Valid @RequestBody UsuarioRequestDTO request) {
        return usuarioService.crearUsuario(request);
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas."));

        if (!usuario.getActivo()) {
            throw new IllegalArgumentException("La cuenta se encuentra desactivada.");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        String token = jwtService.generarToken(usuario.getEmail(), usuario.getRol().name(),
                usuario.getIdUsuario(), usuario.getTokenVersion());
        String refreshToken = refreshTokenService.emitirNuevaFamilia(usuario);

        return new AuthResponseDTO(token, refreshToken, usuario.getEmail(),
                usuario.getNombre(), usuario.getRol().name());
    }

    /**
     * Rota el refresh token: invalida el presentado y devuelve un nuevo access JWT + refresh.
     * Si el token no es valido (caducado, ya rotado, o credenciales cambiadas) responde 401.
     */
    @PostMapping("/refresh")
    public AuthResponseDTO refresh(@Valid @RequestBody RefreshRequestDTO request) {
        RefreshTokenService.RotacionResult resultado = refreshTokenService.rotar(request.getRefreshToken());
        Usuario usuario = resultado.usuario();

        if (!usuario.getActivo()) {
            throw new InvalidRefreshTokenException("La cuenta se encuentra desactivada.");
        }

        String token = jwtService.generarToken(usuario.getEmail(), usuario.getRol().name(),
                usuario.getIdUsuario(), usuario.getTokenVersion());

        return new AuthResponseDTO(token, resultado.refreshTokenPlano(), usuario.getEmail(),
                usuario.getNombre(), usuario.getRol().name());
    }

    /** Cierra la sesion del dispositivo: revoca el refresh token presentado (idempotente). */
    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody RefreshRequestDTO request) {
        refreshTokenService.revocar(request.getRefreshToken());
        return Map.of("mensaje", "Sesion cerrada.");
    }
}
