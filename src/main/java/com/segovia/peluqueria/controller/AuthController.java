package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.dto.AuthResponseDTO;
import com.segovia.peluqueria.dto.LoginRequestDTO;
import com.segovia.peluqueria.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.repository.UsuarioRepository;
import com.segovia.peluqueria.security.JwtService;
import com.segovia.peluqueria.service.UsuarioService;
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
        // 1. Buscar usuario por email
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas."));

        // 2. Verificar que el usuario este activo
        if (!usuario.getActivo()) {
            throw new IllegalArgumentException("La cuenta se encuentra desactivada.");
        }

        // 3. Verificar la contrasena
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        // 4. Generar token JWT
        String token = jwtService.generarToken(usuario.getEmail(), usuario.getRol().name(), usuario.getIdUsuario());

        // 5. Devolver respuesta con token
        return new AuthResponseDTO(token, usuario.getEmail(), usuario.getNombre(), usuario.getRol().name());
    }
}
