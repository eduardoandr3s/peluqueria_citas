package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.peluquero.dto.PeluqueroRequestDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/peluqueros")
public class PeluqueroController {

    private final PeluqueroService peluqueroService;

    public PeluqueroController(PeluqueroService peluqueroService) {
        this.peluqueroService = peluqueroService;
    }

    @GetMapping
    public List<PeluqueroResponseDTO> listarActivos() {
        return peluqueroService.listarActivos();
    }

    @PostMapping
    public PeluqueroResponseDTO crear(@Valid @RequestBody PeluqueroRequestDTO request) {
        return peluqueroService.crear(request);
    }

    @GetMapping("/{id}")
    public PeluqueroResponseDTO obtenerPorId(@PathVariable Integer id) {
        return peluqueroService.obtenerPorId(id);
    }

    @PutMapping("/{id}")
    public PeluqueroResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody PeluqueroUpdateDTO request) {
        return peluqueroService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        peluqueroService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
