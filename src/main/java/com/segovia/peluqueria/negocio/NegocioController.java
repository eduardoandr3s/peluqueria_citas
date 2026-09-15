package com.segovia.peluqueria.negocio;

import com.segovia.peluqueria.negocio.dto.ActualizarNegocioDTO;
import com.segovia.peluqueria.negocio.dto.NegocioDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/negocio")
public class NegocioController {

    private final NegocioService negocioService;

    public NegocioController(NegocioService negocioService) {
        this.negocioService = negocioService;
    }

    /**
     * Quien es esta peluqueria. <b>Publico y sin token</b>, por lo mismo que
     * {@code /api/modulos/activos}: las dos apps pintan el nombre, el logo y el contacto
     * antes de que nadie inicie sesion, y son datos de escaparate.
     */
    @GetMapping
    public NegocioDTO ficha() {
        return negocioService.ficha();
    }

    /** Guarda la ficha. Solo ADMIN: el horario decide los huecos de todo el mundo. */
    @PutMapping
    public NegocioDTO actualizar(@Valid @RequestBody ActualizarNegocioDTO request) {
        return negocioService.actualizar(request);
    }
}
