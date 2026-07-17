package com.segovia.peluqueria.estadistica;

import com.segovia.peluqueria.estadistica.dto.EstadisticasResponseDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasController {

    private final EstadisticasService estadisticasService;

    public EstadisticasController(EstadisticasService estadisticasService) {
        this.estadisticasService = estadisticasService;
    }

    @GetMapping
    public ResponseEntity<?> obtenerEstadisticas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        // Por defecto, los últimos 30 días si no se especifica el rango.
        LocalDate hastaEfectiva = hasta != null ? hasta : LocalDate.now();
        LocalDate desdeEfectiva = desde != null ? desde : hastaEfectiva.minusDays(30);
        if (desdeEfectiva.isAfter(hastaEfectiva)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La fecha 'desde' debe ser anterior o igual a 'hasta'"));
        }
        EstadisticasResponseDTO stats = estadisticasService.obtenerEstadisticas(desdeEfectiva, hastaEfectiva);
        return ResponseEntity.ok(stats);
    }
}
