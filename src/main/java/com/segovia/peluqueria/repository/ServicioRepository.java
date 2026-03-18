package com.segovia.peluqueria.repository;

import com.segovia.peluqueria.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Integer> {

    // Lista solo los servicios activos
    List<Servicio> findByActivoTrue();
}
