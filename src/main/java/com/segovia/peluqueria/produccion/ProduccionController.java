package com.segovia.peluqueria.produccion;

import com.segovia.peluqueria.produccion.dto.ProduccionPeluqueroDTO;
import com.segovia.peluqueria.produccion.dto.ProduccionResponseDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/produccion")
public class ProduccionController {

    private final ProduccionService produccionService;

    public ProduccionController(ProduccionService produccionService) {
        this.produccionService = produccionService;
    }

    @GetMapping("/mia")
    public ProduccionResponseDTO mia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        return produccionService.produccionPropia(authentication.getName(), desdeEfectiva(desde), hastaEfectiva(hasta));
    }

    @GetMapping("/peluquero/{id}")
    public ProduccionResponseDTO dePeluquero(
            @PathVariable Integer id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return produccionService.produccionDePeluquero(id, desdeEfectiva(desde), hastaEfectiva(hasta));
    }

    @GetMapping
    public List<ProduccionPeluqueroDTO> comparativa(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return produccionService.comparativa(desdeEfectiva(desde), hastaEfectiva(hasta));
    }

    // Sin rango explicito se responde el mes en curso, que es la unidad en la que se paga.
    private LocalDate desdeEfectiva(LocalDate desde) {
        return desde != null ? desde : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate hastaEfectiva(LocalDate hasta) {
        return hasta != null ? hasta : LocalDate.now();
    }
}
