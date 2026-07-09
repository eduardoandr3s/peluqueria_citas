package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.peluquero.dto.PeluqueroRequestDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PeluqueroService {

    private final PeluqueroRepository peluqueroRepository;

    public PeluqueroService(PeluqueroRepository peluqueroRepository) {
        this.peluqueroRepository = peluqueroRepository;
    }

    @Transactional(readOnly = true)
    public List<PeluqueroResponseDTO> listarActivos() {
        return peluqueroRepository.findByActivoTrue().stream()
                .map(PeluqueroResponseDTO::desde)
                .toList();
    }

    @Transactional
    public PeluqueroResponseDTO crear(PeluqueroRequestDTO request) {
        Peluquero peluquero = new Peluquero();
        peluquero.setNombre(request.getNombre().trim());
        return PeluqueroResponseDTO.desde(peluqueroRepository.save(peluquero));
    }

    @Transactional(readOnly = true)
    public PeluqueroResponseDTO obtenerPorId(Integer id) {
        return PeluqueroResponseDTO.desde(obtenerEntidadPorId(id));
    }

    @Transactional
    public PeluqueroResponseDTO actualizar(Integer id, PeluqueroUpdateDTO request) {
        Peluquero peluquero = obtenerEntidadPorId(id);
        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            peluquero.setNombre(request.getNombre().trim());
        }
        return PeluqueroResponseDTO.desde(peluqueroRepository.save(peluquero));
    }

    @Transactional
    public void eliminar(Integer id) {
        Peluquero peluquero = obtenerEntidadPorId(id);
        peluquero.setActivo(false);
        peluqueroRepository.save(peluquero);
    }

    Peluquero obtenerEntidadPorId(Integer id) {
        return peluqueroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Peluquero no encontrado con id: " + id));
    }
}
