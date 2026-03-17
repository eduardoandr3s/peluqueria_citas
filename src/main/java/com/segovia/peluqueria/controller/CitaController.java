package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.model.Cita;
import com.segovia.peluqueria.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {
    @Autowired
    private CitaRepository citaRepository;

    @GetMapping
    public List<Cita> listarCitas() {
        return citaRepository.findAll();
    }

    @PostMapping
    public Cita agendarCita(@RequestBody Cita cita){

        // Si el estado pendiente no viene en el JSON se lo asignamos por defecto

        if (cita.getEstado() == null) {
            cita.setEstado("PENDIENTE");
        }
        return citaRepository.save(cita);

    }
}
