package com.segovia.peluqueria.usuario;

import com.segovia.peluqueria.usuario.dto.CambiarRolRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.usuario.dto.UsuarioUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public Page<UsuarioResponseDTO> listarUsuarios(
            @RequestParam(name = "incluirInactivos", defaultValue = "false") boolean incluirInactivos,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return usuarioService.listarUsuarios(incluirInactivos, search, pageable);
    }

    @PostMapping
    public UsuarioResponseDTO crearUsuario(@Valid @RequestBody UsuarioRequestDTO requestDTO) {
        return usuarioService.crearUsuario(requestDTO);
    }

    @GetMapping("/me")
    public UsuarioResponseDTO obtenerUsuarioActual(Authentication authentication){
        return usuarioService.obtenerUsuarioActual(authentication.getName());
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

    @PatchMapping("/{id}/activar")
    public UsuarioResponseDTO activarUsuario(@PathVariable Integer id){
        return usuarioService.activarUsuario(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id){
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sube o sustituye el avatar. No basta con estar autenticado: cada usuario solo
     * puede tocar el suyo (un ADMIN, el de cualquiera), y eso lo comprueba el
     * servicio, que es quien sabe de quien es el id.
     */
    @PostMapping(path = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UsuarioResponseDTO subirAvatar(@PathVariable Integer id,
                                          @RequestParam("imagen") MultipartFile imagen,
                                          Authentication authentication){
        return usuarioService.subirAvatar(id, imagen, authentication.getName());
    }

    @DeleteMapping("/{id}/avatar")
    public UsuarioResponseDTO borrarAvatar(@PathVariable Integer id, Authentication authentication){
        return usuarioService.borrarAvatar(id, authentication.getName());
    }
}
