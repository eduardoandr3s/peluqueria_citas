package com.segovia.peluqueria.modulo;

import com.segovia.peluqueria.modulo.dto.ActualizarModulosDTO;
import com.segovia.peluqueria.modulo.dto.ModuloDTO;
import com.segovia.peluqueria.modulo.dto.ModulosActivosDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
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
}
