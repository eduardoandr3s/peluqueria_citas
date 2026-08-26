package com.segovia.peluqueria.galeria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GaleriaFotoRepository extends JpaRepository<GaleriaFoto, Integer> {

    /**
     * Orden manual y, a igualdad, el id: sin el segundo criterio dos fotos con el
     * mismo orden saldrian en cualquier posicion y la rejilla bailaria entre
     * recargas.
     */
    List<GaleriaFoto> findAllByOrderByOrdenAscIdFotoAsc();

    /** La ultima de la rejilla, para colocar detras la que se acaba de subir. */
    Optional<GaleriaFoto> findFirstByOrderByOrdenDescIdFotoDesc();
}
