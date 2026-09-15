package com.segovia.peluqueria.modulo;

import com.segovia.peluqueria.modulo.dto.ActualizarModulosDTO;
import com.segovia.peluqueria.modulo.dto.ModuloDTO;
import com.segovia.peluqueria.modulo.dto.ModulosActivosDTO;
import com.segovia.peluqueria.modulo.dto.PerfilArranqueDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/modulos")
public class ModuloController {

    private final ModuloService moduloService;

    public ModuloController(ModuloService moduloService) {
        this.moduloService = moduloService;
    }

    /**
     * Que tiene encendido el negocio. <b>Publico y sin token</b>, porque la app tiene
     * pantallas que se ven sin cuenta (el catalogo, la galeria, el equipo) y necesita
     * saber que pintar antes del login. No expone nada personal: dice que hace la
     * peluqueria, que es lo que el escaparate ya cuenta.
     */
    @GetMapping("/activos")
    public ModulosActivosDTO activos() {
        return new ModulosActivosDTO(moduloService.clavesActivas());
    }

    /** Catalogo completo con su estado, para la pantalla de configuracion. Solo ADMIN. */
    @GetMapping
    public List<ModuloDTO> listar() {
        return moduloService.listar();
    }

    /**
     * Enciende y apaga modulos. Solo ADMIN, y de nadie mas: apagar los pagos es mas grave
     * que anular una cita.
     */
    @PutMapping
    public List<ModuloDTO> actualizar(@Valid @RequestBody ActualizarModulosDTO request) {
        return moduloService.actualizar(request);
    }

    /**
     * Los perfiles de arranque, con lo que enciende y lo que apaga cada uno. Solo ADMIN:
     * es la pantalla de configuracion, y lo que hace el publico con esto es nada.
     */
    @GetMapping("/perfiles")
    public List<PerfilArranqueDTO> perfiles() {
        return moduloService.perfiles();
    }

    /**
     * Aplica un perfil de arranque. Es POST y no PUT porque no se esta guardando "el
     * perfil" en ningun sitio: se dispara una accion que reescribe el estado de todos los
     * modulos y despues no queda perfil que consultar.
     */
    @PostMapping("/perfiles/{clave}")
    public List<ModuloDTO> aplicarPerfil(@PathVariable String clave) {
        return moduloService.aplicarPerfil(moduloService.resolverPerfil(clave));
    }
}
