package com.segovia.peluqueria.calendario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaBloqueadoRepository extends JpaRepository<DiaBloqueado, Integer> {

    boolean existsByFecha(LocalDate fecha);

    Optional<DiaBloqueado> findByFecha(LocalDate fecha);

    List<DiaBloqueado> findByFechaBetweenOrderByFecha(LocalDate desde, LocalDate hasta);

    List<DiaBloqueado> findByFechaGreaterThanEqualOrderByFecha(LocalDate desde);
}
