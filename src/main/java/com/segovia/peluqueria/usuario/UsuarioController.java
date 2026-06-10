package com.segovia.peluqueria.usuario;

import com.segovia.peluqueria.usuario.dto.CambiarRolRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioService.listarUsuarios();
    }

    @PostMapping
    public UsuarioResponseDTO crearUsuario(@Valid @RequestBody UsuarioRequestDTO requestDTO) {
        return usuarioService.crearUsuario(requestDTO);
    }

    @GetMapping("/{id}")
    public UsuarioResponseDTO obtenerUsuarioPorId(@PathVariable Integer id, Authentication authentication){
        return usuarioService.obtenerUsuarioPorId(id, authentication.getName());
    }

    @PutMapping("/{id}")
    public UsuarioResponseDTO actualizarUsuario(@PathVariable Integer id, @Valid @RequestBody UsuarioUpdateDTO request, Authentication authentication){
        return usuarioService.actualizarUsuario(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/rol")
    public UsuarioResponseDTO cambiarRol(@PathVariable Integer id, @Valid @RequestBody CambiarRolRequestDTO request){
        return usuarioService.cambiarRol(id, request.getRol());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id){
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
