package com.segovia.peluqueria.repository;

import com.segovia.peluqueria.model.Cita;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitaRepository extends JpaRepository<Cita, Integer> {
}
