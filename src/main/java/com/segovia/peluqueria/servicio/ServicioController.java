package com.segovia.peluqueria.servicio;

import com.segovia.peluqueria.servicio.dto.ServicioRequestDTO;
import com.segovia.peluqueria.servicio.dto.ServicioUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicios")
public class ServicioController {

    private final ServicioService servicioService;

    public ServicioController(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    @GetMapping
    public List<Servicio> listarServicios() {
        return servicioService.listarServicios();
    }

    @PostMapping
    public Servicio crearServicio(@Valid @RequestBody ServicioRequestDTO request) {
        return servicioService.crearServicio(request);
    }

    @GetMapping("/{id}")
    public Servicio obtenerServicioPorId(@PathVariable Integer id) {
        return servicioService.obtenerServicioPorId(id);
    }

    @PutMapping("/{id}")
    public Servicio actualizarServicio(@PathVariable Integer id, @Valid @RequestBody ServicioUpdateDTO request) {
        return servicioService.actualizarServicio(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarServicio(@PathVariable Integer id) {
        servicioService.eliminarServicio(id);
        return ResponseEntity.noContent().build();
    }
}
