package com.segovia.peluqueria.controller;

import com.segovia.peluqueria.dto.UsuarioRequestDTO;
import com.segovia.peluqueria.dto.UsuarioResponseDTO;
import com.segovia.peluqueria.model.Usuario;
import com.segovia.peluqueria.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioService.listarUsuarios();
    }

    @PostMapping
    public UsuarioResponseDTO crearUsuario(@RequestBody UsuarioRequestDTO requestDTO) {
        return usuarioService.crearUsuario(requestDTO);
    }
}
