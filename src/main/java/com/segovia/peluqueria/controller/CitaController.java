package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.model.Cita;
import com.segovia.peluqueria.service.CitaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {
    @Autowired
    private CitaService citaService; // Inyectamos el servicio de citas para manejar la lógica de negocio

    // Método para listar todas las citas, devolviendo una lista de objetos Cita
    @GetMapping
    public List<Cita> listarCitas() {
        return citaService.listarCitas();
    }

    // Método para agendar una nueva cita, recibiendo los datos de la cita en el cuerpo de la solicitud y devolviendo la cita creada
    @PostMapping
    public Cita agendarCita(@RequestBody Cita cita) {

        return citaService.agendarCita(cita);
    }

    // Método para obtener una cita por su ID, recibiendo el ID como parte de la URL y devolviendo la cita correspondiente
    @GetMapping("/{id}")
    public Cita obtenerCitaPorId(@PathVariable Integer id) {
        return citaService.obtenerCitaPorId(id);
    }

    // Método para actualizar una cita existente por su ID, recibiendo los nuevos datos de la cita en el cuerpo de la solicitud
    @PutMapping("/{id}")
    public Cita actualizarCita(@PathVariable Integer id, @RequestBody Cita cita){
        return citaService.actualizarCita(id, cita);
    }

    // Método para eliminar una cita por su ID, devolviendo una respuesta sin contenido si la eliminación es exitosa
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCita(@PathVariable Integer id){
        citaService.eliminarCita(id);
        return ResponseEntity.noContent().build();
    }
}
