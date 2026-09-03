package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.galeria.dto.GaleriaFotoResponseDTO;
import com.segovia.peluqueria.galeria.dto.GaleriaFotoUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
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

    /**
     * Publico: es el escaparate de la peluqueria, se ve sin cuenta. La autenticacion
     * llega null cuando no hay ninguna, y solo sirve para marcar las fotos propias.
     */
    @GetMapping
    public List<GaleriaFotoResponseDTO> listarFotos(Authentication authentication) {
        return galeriaService.listarFotos(authentication != null ? authentication.getName() : null);
    }

    /**
     * Sube una foto nueva. Entran ADMIN y PELUQUERO por rol (ver {@code SecurityConfig}) y
     * el permiso {@code GALERIA_SUBIR} decide dentro. La miniatura la genera el cliente y
     * viaja en el mismo multipart; es opcional.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GaleriaFotoResponseDTO subirFoto(@RequestParam("imagen") MultipartFile imagen,
                                            @RequestParam(value = "miniatura", required = false) MultipartFile miniatura,
                                            @RequestParam(value = "titulo", required = false) String titulo,
                                            Authentication authentication) {
        return galeriaService.subirFoto(imagen, miniatura, titulo, authentication.getName());
    }

    /** El dueno de la foto y el permiso deciden; el servicio lo comprueba campo a campo. */
    @PutMapping("/{id}")
    public GaleriaFotoResponseDTO actualizarFoto(@PathVariable Integer id,
                                                 @Valid @RequestBody GaleriaFotoUpdateDTO request,
                                                 Authentication authentication) {
        return galeriaService.actualizarFoto(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarFoto(@PathVariable Integer id, Authentication authentication) {
        galeriaService.eliminarFoto(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
