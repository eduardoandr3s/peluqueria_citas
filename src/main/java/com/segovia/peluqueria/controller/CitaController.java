package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.model.Cita;
import com.segovia.peluqueria.service.CitaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {
    @Autowired
    private CitaService citaService; // Inyectamos el servicio de citas para manejar la lógica de negocio

    @GetMapping
    public List<Cita> listarCitas() {
        return citaService.listarCitas();
    }

    @PostMapping
    public Cita agendarCita(@RequestBody Cita cita) {

        // Si el estado pendiente no viene en el JSON se lo asignamos por defecto

        if (cita.getEstado() == null) {
            cita.setEstado("PENDIENTE");
        }
        return citaService.agendarCita(cita);

    }
}
