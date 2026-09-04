package com.segovia.peluqueria.peluquero;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeluqueroRepository extends JpaRepository<Peluquero, Integer> {

    List<Peluquero> findByActivoTrue();

    /**
     * El equipo tal y como se presenta al cliente. El desempate por nombre importa: con
     * todos los ordenes a 0 (que es como nacen) el listado tiene que salir siempre igual y
     * no en el orden que le apetezca devolver a la base de datos.
     */
    List<Peluquero> findByActivoTrueOrderByOrdenAscNombreAsc();

    /** Ficha de la cuenta autenticada, vacia si esa cuenta no es peluquero. */
    Optional<Peluquero> findByUsuarioIdUsuario(Integer idUsuario);
}
