package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.auth.dto.AuthResponseDTO;
import com.segovia.peluqueria.auth.dto.LoginRequestDTO;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioService usuarioService,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

        String token = jwtService.generarToken(usuario.getEmail(), usuario.getRol().name(), usuario.getIdUsuario());

        return new AuthResponseDTO(token, usuario.getEmail(), usuario.getNombre(), usuario.getRol().name());
    }
}
