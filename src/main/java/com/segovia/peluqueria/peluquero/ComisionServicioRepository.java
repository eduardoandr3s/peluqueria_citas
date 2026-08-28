package com.segovia.peluqueria.peluquero;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComisionServicioRepository extends JpaRepository<ComisionServicio, Integer> {

    List<ComisionServicio> findByPeluqueroIdPeluquero(Integer idPeluquero);

    Optional<ComisionServicio> findByPeluqueroIdPeluqueroAndServicioIdServicio(Integer idPeluquero, Integer idServicio);

    void deleteByPeluqueroIdPeluqueroAndServicioIdServicio(Integer idPeluquero, Integer idServicio);
}
