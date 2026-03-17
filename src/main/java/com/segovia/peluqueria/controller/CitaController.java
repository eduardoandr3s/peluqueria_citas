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

        return citaService.agendarCita(cita);

    }
}
