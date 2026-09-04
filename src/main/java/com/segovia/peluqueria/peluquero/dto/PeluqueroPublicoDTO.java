package com.segovia.peluqueria.peluquero.dto;

import com.segovia.peluqueria.peluquero.Peluquero;
import lombok.Data;

import java.util.List;

/**
 * Carta de presentacion de un profesional, tal y como la ve un cliente ANTES de tener
 * cuenta: es la respuesta de {@code GET /api/peluqueros/publicos}, que se sirve sin token.
 *
 * <p>Es un DTO nuevo y no una ampliacion de {@link PeluqueroResponseDTO} a proposito. Aqui
 * no entra <b>nada</b> de la cuenta —ni email, ni telefono, ni el {@code usuarioId}— ni la
 * comision, y tampoco {@code activo}: el listado ya devuelve solo los activos, asi que
 * publicar el campo solo serviria para contar cuanta gente se ha ido de la peluqueria.
 */
@Data
public class PeluqueroPublicoDTO {

    private Integer idPeluquero;
    private String nombre;
    private String presentacion;

    /**
     * Ya troceadas. En la base de datos viven como una cadena con comas, pero partirla es
     * trabajo del servidor: si viajara entera, el panel y la app tendrian cada uno su
     * propia idea de que hacer con los espacios y las comas de sobra.
     */
    private List<String> especialidades;

    private Integer aniosExperiencia;

    /** Ya montada desde la clave guardada. Null si esa ficha no tiene foto. */
    private String fotoUrl;

    /** Solo el usuario, sin arroba ni URL: el enlace lo monta quien pinta la pantalla. */
    private String instagram;

    public static PeluqueroPublicoDTO desde(Peluquero peluquero, String fotoUrl) {
        PeluqueroPublicoDTO dto = new PeluqueroPublicoDTO();
        dto.setIdPeluquero(peluquero.getIdPeluquero());
        dto.setNombre(peluquero.getNombre());
        dto.setPresentacion(peluquero.getPresentacion());
        dto.setEspecialidades(Especialidades.aLista(peluquero.getEspecialidades()));
        dto.setAniosExperiencia(peluquero.getAniosExperiencia());
        dto.setFotoUrl(fotoUrl);
        dto.setInstagram(peluquero.getInstagram());
        return dto;
    }
}
