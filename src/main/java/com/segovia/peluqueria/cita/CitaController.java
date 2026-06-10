package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public List<CitaResponseDTO> listarCitas(Authentication authentication) {
        return citaService.listarCitas(authentication.getName());
    }

    @PostMapping
    public CitaResponseDTO agendarCita(@Valid @RequestBody CitaRequestDTO request, Authentication authentication) {
        return citaService.agendarCita(request, authentication.getName());
    }

    @GetMapping("/{id}")
    public CitaResponseDTO obtenerCitaPorId(@PathVariable Integer id, Authentication authentication) {
        return citaService.obtenerCitaPorId(id, authentication.getName());
    }

    @PutMapping("/{id}")
    public CitaResponseDTO actualizarCita(@PathVariable Integer id, @Valid @RequestBody CitaUpdateDTO request, Authentication authentication) {
        return citaService.actualizarCita(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCita(@PathVariable Integer id, Authentication authentication) {
        citaService.eliminarCita(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
