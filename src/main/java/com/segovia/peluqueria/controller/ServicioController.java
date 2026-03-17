package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.service.ServicioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
// Marca esta clase como un controlador REST, lo que permite manejar solicitudes HTTP y enviar respuestas en formato JSON o XML.
@RequestMapping("/api/servicios")
// Especifica la ruta base para todas las solicitudes que maneje este controlador. En este caso, todas las rutas comenzarán con "/api/servicios".
public class ServicioController {

    @Autowired
    private ServicioService servicioService; // Inyecta el servicio ServicioService para manejar la lógica de negocio relacionada con los servicios.

    //Método para listar todos los servicios (GET /api/servicios)
    @GetMapping
    public List<Servicio> listarServicios() {
        return  servicioService.listarServicios();
    }

    //Método para crear un nuevo servicio (POST /api/servicios)
    @PostMapping
    public Servicio crearServicio(@RequestBody Servicio servicio) {
        // @RequestBody indica que el cuerpo de la solicitud HTTP se deserializará en un objeto Servicio. Esto permite recibir los datos del nuevo servicio en formato JSON o XML y convertirlos automáticamente en una instancia de la clase Servicio.
        return servicioService.crearServicio(servicio);

    }


}
