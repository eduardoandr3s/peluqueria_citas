package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.model.Servicio;
import com.segovia.peluqueria.repository.ServicioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // Marca esta clase como un controlador REST, lo que permite manejar solicitudes HTTP y enviar respuestas en formato JSON o XML.
@RequestMapping("/api/servicios") // Especifica la ruta base para todas las solicitudes que maneje este controlador. En este caso, todas las rutas comenzarán con "/api/servicios".
public class ServicioController {

    @Autowired // Inyecta automáticamente una instancia de ServicioRepository en esta clase, lo que permite acceder a los métodos de la capa de persistencia sin necesidad de crear manualmente una instancia.
    private ServicioRepository servicioRepository;

    //Método para listar todos los servicios (GET /api/servicios)
    @GetMapping
    public List<Servicio> listarServicios() {
        return servicioRepository.findAll(); // Llama al método findAll() del repositorio para obtener una lista de todos los servicios almacenados en la base de datos y la devuelve como respuesta.
    }

    //Método para crear un nuevo servicio (POST /api/servicios)
    @PostMapping
    public Servicio crearServicio(@RequestBody Servicio servicio){
        // @RequestBody indica que el cuerpo de la solicitud HTTP se deserializará en un objeto Servicio. Esto permite recibir los datos del nuevo servicio en formato JSON o XML y convertirlos automáticamente en una instancia de la clase Servicio.
        return servicioRepository.save(servicio); // Llama al método save() del repositorio para guardar el nuevo servicio en la base de datos y devuelve el servicio guardado como respuesta.

    }





}
