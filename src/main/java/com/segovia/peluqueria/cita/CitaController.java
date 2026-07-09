package com.segovia.peluqueria.cita;

import com.segovia.peluqueria.cita.dto.CitaRequestDTO;
import com.segovia.peluqueria.cita.dto.CitaResponseDTO;
import com.segovia.peluqueria.cita.dto.CitaUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping
    public Page<CitaResponseDTO> listarCitas(
            @PageableDefault(size = 20, sort = "fechaHora", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return citaService.listarCitas(authentication.getName(), pageable);
    }

    @GetMapping("/disponibilidad")
    public List<String> disponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam Integer idServicio,
            @RequestParam(required = false) Integer peluqueroId) {
        return citaService.obtenerDisponibilidad(fecha, idServicio, peluqueroId);
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
