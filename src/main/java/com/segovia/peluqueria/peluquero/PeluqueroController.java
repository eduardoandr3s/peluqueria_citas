package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.peluquero.dto.ComisionServicioDTO;
import com.segovia.peluqueria.peluquero.dto.ComisionesUpdateDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroCvUpdateDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroGestionDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroPublicoDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroRequestDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroResponseDTO;
import com.segovia.peluqueria.peluquero.dto.PeluqueroUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/peluqueros")
public class PeluqueroController {

    private final PeluqueroService peluqueroService;
    private final PeluqueroCvService cvService;

    public PeluqueroController(PeluqueroService peluqueroService, PeluqueroCvService cvService) {
        this.peluqueroService = peluqueroService;
        this.cvService = cvService;
    }

    @GetMapping
    public List<PeluqueroResponseDTO> listarActivos() {
        return peluqueroService.listarActivos();
    }

    @PostMapping
    public PeluqueroResponseDTO crear(@Valid @RequestBody PeluqueroRequestDTO request) {
        return peluqueroService.crear(request);
    }

    /**
     * Fichas completas (activas e inactivas) con comision y cuenta vinculada. Va antes de
     * {@code /{id}} para que "gestion" no se lea como un id.
     */
    @GetMapping("/gestion")
    public List<PeluqueroGestionDTO> listarParaGestion() {
        return peluqueroService.listarParaGestion();
    }

    /**
     * El equipo con su carta de presentacion, PUBLICO: se lee sin cuenta porque es lo que
     * mira alguien que todavia no se ha registrado para decidir con quien agendar. Va antes
     * de {@code /{id}} por lo mismo que {@code /gestion}, y en {@code SecurityConfig} su
     * regla tiene que ir antes de la de {@code /api/peluqueros/**}, que pide token.
     */
    @GetMapping("/publicos")
    public List<PeluqueroPublicoDTO> listarPublicos() {
        return cvService.listarPublicos();
    }

    /**
     * El CV de la ficha de quien pregunta. No lleva id a proposito: se resuelve desde la
     * cuenta, como {@code /api/produccion/mia}.
     */
    @GetMapping("/mio")
    public PeluqueroCvDTO miCv(Authentication authentication) {
        return cvService.cvPropio(authentication.getName());
    }

    /** Reemplaza el CV propio. Lo abre el rol y lo estrecha {@code PERFIL_CV_EDITAR}. */
    @PutMapping("/mio")
    public PeluqueroCvDTO actualizarMiCv(@Valid @RequestBody PeluqueroCvUpdateDTO request,
                                         Authentication authentication) {
        return cvService.actualizarCvPropio(authentication.getName(), request);
    }

    /** El CV de cualquier ficha, para la pestana del panel. Solo ADMIN, por ruta. */
    @PutMapping("/{id}/cv")
    public PeluqueroCvDTO actualizarCv(@PathVariable Integer id,
                                       @Valid @RequestBody PeluqueroCvUpdateDTO request) {
        return cvService.actualizarCvDe(id, request);
    }

    /**
     * Foto del CV. Entran ADMIN y PELUQUERO por rol; dentro, el servicio comprueba que la
     * ficha sea la suya y que tenga el permiso, porque de quien es esa ficha no se sabe
     * hasta cargarla.
     */
    @PostMapping(path = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PeluqueroCvDTO subirFoto(@PathVariable Integer id,
                                    @RequestParam("foto") MultipartFile foto,
                                    Authentication authentication) {
        return cvService.subirFoto(id, foto, authentication.getName());
    }

    @DeleteMapping("/{id}/foto")
    public PeluqueroCvDTO borrarFoto(@PathVariable Integer id, Authentication authentication) {
        return cvService.borrarFoto(id, authentication.getName());
    }

    @GetMapping("/{id}")
    public PeluqueroResponseDTO obtenerPorId(@PathVariable Integer id) {
        return peluqueroService.obtenerPorId(id);
    }

    @PutMapping("/{id}")
    public PeluqueroGestionDTO actualizar(@PathVariable Integer id, @Valid @RequestBody PeluqueroUpdateDTO request) {
        return peluqueroService.actualizar(id, request);
    }

    @GetMapping("/{id}/comisiones")
    public List<ComisionServicioDTO> comisiones(@PathVariable Integer id) {
        return peluqueroService.comisionesDe(id);
    }

    @PutMapping("/{id}/comisiones")
    public List<ComisionServicioDTO> reemplazarComisiones(@PathVariable Integer id,
                                                          @Valid @RequestBody ComisionesUpdateDTO request) {
        return peluqueroService.reemplazarComisiones(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        peluqueroService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
