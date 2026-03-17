package com.segovia.peluqueria.repository;

import com.segovia.peluqueria.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    // JpaRepository ya tiene programado por detrás el guardar, borrar, buscar por ID, etc.
    // "Usuario" es la tabla que maneja, e "Integer" es el tipo de dato de su Clave Primaria (ID).
}
