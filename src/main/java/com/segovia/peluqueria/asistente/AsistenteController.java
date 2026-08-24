package com.segovia.peluqueria.asistente;

import com.segovia.peluqueria.asistente.dto.AsistentePreguntaDTO;
import com.segovia.peluqueria.asistente.dto.AsistenteRespuestaDTO;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistente conversacional. Endpoint <strong>publico</strong>: un cliente pregunta precios y
 * horarios antes de registrarse, asi que exigir login lo dejaria sin uso.
 *
 * <p>Al ser publico y costar tokens, esta limitado por IP en
 * {@code com.segovia.peluqueria.security.RateLimitFilter}, que responde 429 al pasarse.
 *
 * <p>Solo se registra si el asistente esta encendido (ver {@link AsistenteService}). Con el
 * asistente apagado la ruta no existe y responde 404, que es la respuesta honesta: no es que
 * el asistente haya fallado, es que no esta desplegado.
 */
@RestController
@ConditionalOnExpression("!'${spring.ai.model.chat:none}'.equals('none')")
@RequestMapping("/api/asistente")
public class AsistenteController {

    private final AsistenteService asistenteService;

    public AsistenteController(AsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    @PostMapping
    public AsistenteRespuestaDTO preguntar(@Valid @RequestBody AsistentePreguntaDTO pregunta) {
        return asistenteService.responder(pregunta);
    }
}
