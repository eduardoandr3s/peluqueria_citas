package com.segovia.peluqueria.calendario;

import com.segovia.peluqueria.calendario.dto.DiaBloqueadoRequestDTO;
import com.segovia.peluqueria.calendario.dto.DiaBloqueadoResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dias-bloqueados")
public class DiaBloqueadoController {

    private final CalendarioService calendarioService;

    public DiaBloqueadoController(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping
    public List<DiaBloqueadoResponseDTO> listarProximos() {
        return calendarioService.listarProximos();
    }

    @PostMapping
    public DiaBloqueadoResponseDTO bloquear(@Valid @RequestBody DiaBloqueadoRequestDTO request) {
        return calendarioService.bloquear(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desbloquear(@PathVariable Integer id) {
        calendarioService.desbloquear(id);
        return ResponseEntity.noContent().build();
    }
}
