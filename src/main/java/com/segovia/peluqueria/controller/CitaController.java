package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.dto.CitaRequestDTO;
import com.segovia.peluqueria.dto.CitaUpdateDTO;
import com.segovia.peluqueria.model.Cita;
import com.segovia.peluqueria.service.CitaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    @Autowired
    private CitaService citaService;

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
