package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping
    public List<Cita> listarCitas() {
        return citaService.listarCitas();
    }

    @PostMapping
    public Cita agendarCita(@Valid @RequestBody CitaRequestDTO request) {
        return citaService.agendarCita(request);
    }

    @GetMapping("/{id}")
    public Cita obtenerCitaPorId(@PathVariable Integer id) {
        return citaService.obtenerCitaPorId(id);
    }

    @PutMapping("/{id}")
    public Cita actualizarCita(@PathVariable Integer id, @Valid @RequestBody CitaUpdateDTO request) {
        return citaService.actualizarCita(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCita(@PathVariable Integer id) {
        citaService.eliminarCita(id);
        return ResponseEntity.noContent().build();
    }
}
