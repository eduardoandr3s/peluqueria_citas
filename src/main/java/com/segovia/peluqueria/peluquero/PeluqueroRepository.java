package com.segovia.peluqueria.peluquero;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeluqueroRepository extends JpaRepository<Peluquero, Integer> {

    List<Peluquero> findByActivoTrue();
}
