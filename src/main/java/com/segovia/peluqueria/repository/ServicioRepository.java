package com.segovia.peluqueria.repository;

import com.segovia.peluqueria.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // Con esto le digo a Spring que esta interfaz es un repositorio, lo que permite la inyección de dependencias y otras funcionalidades relacionadas con la persistencia de datos
public interface ServicioRepository extends JpaRepository<Servicio, Integer> {
// JpaRepository ya tiene programado por detrás el guardar, borrar, buscar por ID, etc.
    // "Servicio" es la tabla que maneja, e "Integer" es el tipo de dato de su Clave Primaria (ID).

}
