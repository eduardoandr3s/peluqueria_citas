package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    // Inyección de UsuarioService para manejar la lógica de negocio relacionada con usuarios
    @Autowired
    private UsuarioService usuarioService;

    // Endpoint para listar todos los usuarios
    @GetMapping
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioService.listarUsuarios();
    }

    // Endpoint para crear un nuevo usuario, recibe un UsuarioRequestDTO en el cuerpo de la solicitud
    @PostMapping
    public UsuarioResponseDTO crearUsuario(@Valid @RequestBody UsuarioRequestDTO requestDTO) {
        return usuarioService.crearUsuario(requestDTO);
    }

    // Endpoint para obtener un usuario por su ID, el ID se pasa como parte de la URL
    @GetMapping("/{id}")
    public UsuarioResponseDTO obtenerUsuarioPorId(@PathVariable Integer id){
        return usuarioService.obtenerUsuarioPorId(id);
    }

    // Endpoint para actualizar un usuario existente, recibe el ID del usuario a actualizar y un UsuarioRequestDTO con los nuevos datos
    @PutMapping("/{id}")
    public UsuarioResponseDTO actualizarUsuario(@PathVariable Integer id, @Valid @RequestBody UsuarioRequestDTO request){
        return usuarioService.actualizarUsuario(id, request);
    }

    // Endpoint para eliminar un usuario por su ID, el ID se pasa como parte de la URL
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id){
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
