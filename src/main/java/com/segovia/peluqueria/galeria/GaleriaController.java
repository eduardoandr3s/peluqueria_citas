package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.galeria.dto.GaleriaFotoResponseDTO;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/galeria")
public class GaleriaController {

    private final GaleriaService galeriaService;

    public GaleriaController(GaleriaService galeriaService) {
        this.galeriaService = galeriaService;
    }

    /** Publico: es el escaparate de la peluqueria, se ve sin cuenta. */
    @GetMapping
    public List<GaleriaFotoResponseDTO> listarFotos() {
        return galeriaService.listarFotos();
    }

    /**
     * Sube una foto nueva (solo ADMIN, ver {@code SecurityConfig}). La miniatura la
     * genera el cliente y viaja en el mismo multipart; es opcional.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GaleriaFotoResponseDTO subirFoto(@RequestParam("imagen") MultipartFile imagen,
                                            @RequestParam(value = "miniatura", required = false) MultipartFile miniatura,
                                            @RequestParam(value = "titulo", required = false) String titulo) {
        return galeriaService.subirFoto(imagen, miniatura, titulo);
    }

    @PutMapping("/{id}")
    public GaleriaFotoResponseDTO actualizarFoto(@PathVariable Integer id,
                                                 @Valid @RequestBody GaleriaFotoUpdateDTO request) {
        return galeriaService.actualizarFoto(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarFoto(@PathVariable Integer id) {
        galeriaService.eliminarFoto(id);
        return ResponseEntity.noContent().build();
    }
}
