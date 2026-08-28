package com.segovia.peluqueria.peluquero;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeluqueroRepository extends JpaRepository<Peluquero, Integer> {

    List<Peluquero> findByActivoTrue();

    /** Ficha de la cuenta autenticada, vacia si esa cuenta no es peluquero. */
    Optional<Peluquero> findByUsuarioIdUsuario(Integer idUsuario);
}
