package com.segovia.peluqueria.permiso;

import com.segovia.peluqueria.permiso.dto.ActualizarPermisosDTO;
import com.segovia.peluqueria.permiso.dto.MisPermisosDTO;
import com.segovia.peluqueria.permiso.dto.PermisoDTO;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import com.segovia.peluqueria.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permisos")
public class PermisoController {

    private final PermisoService permisoService;
    private final UsuarioRepository usuarioRepository;

    public PermisoController(PermisoService permisoService, UsuarioRepository usuarioRepository) {
        this.permisoService = permisoService;
        this.usuarioRepository = usuarioRepository;
    }

    /** Matriz rol x permiso para la pantalla de configuracion. Solo ADMIN. */
    @GetMapping
    public List<PermisoDTO> listar() {
        return permisoService.listarMatriz();
    }

    @PutMapping
    public List<PermisoDTO> actualizar(@Valid @RequestBody ActualizarPermisosDTO request) {
        return permisoService.actualizar(request);
    }

    /**
     * Lo que tiene concedido quien pregunta. Lo puede llamar cualquier autenticado: son
     * sus propios permisos, y el frontend los necesita al entrar para no pintar botones
     * que terminarian en un 403.
     */
    @GetMapping("/mios")
    public MisPermisosDTO mios(Authentication authentication) {
        Usuario actual = usuarioRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        return permisoService.misPermisos(actual.getRol());
    }
}
