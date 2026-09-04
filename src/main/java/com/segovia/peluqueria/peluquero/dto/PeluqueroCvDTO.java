package com.segovia.peluqueria.peluquero.dto;

import com.segovia.peluqueria.peluquero.Peluquero;
import lombok.Data;

import java.util.List;

/**
 * El CV como lo ve quien lo edita: el propio peluquero en {@code /api/peluqueros/mio} o el
 * administrador desde la ficha.
 *
 * <p>Es el DTO publico mas lo que hace falta para editar y no se ensena al cliente:
 * {@code activo} (una ficha desactivada no sale en el equipo, y quien la rellena tiene que
 * saberlo) y {@code orden}, que es de la plantilla entera y solo lo cambia un ADMIN por
 * {@code PUT /api/peluqueros/{id}}. Sigue sin traer nada de la cuenta ni la comision.
 */
@Data
public class PeluqueroCvDTO {

    private Integer idPeluquero;
    private String nombre;
    private Boolean activo;

    private String presentacion;
    private List<String> especialidades;
    private Integer aniosExperiencia;
    private String fotoUrl;
    private String instagram;

    /** Solo de lectura por aqui: ordenar el equipo es cosa del ADMIN. */
    private Integer orden;

    public static PeluqueroCvDTO desde(Peluquero peluquero, String fotoUrl) {
        PeluqueroCvDTO dto = new PeluqueroCvDTO();
        dto.setIdPeluquero(peluquero.getIdPeluquero());
        dto.setNombre(peluquero.getNombre());
        dto.setActivo(peluquero.getActivo());
        dto.setPresentacion(peluquero.getPresentacion());
        dto.setEspecialidades(Especialidades.aLista(peluquero.getEspecialidades()));
        dto.setAniosExperiencia(peluquero.getAniosExperiencia());
        dto.setFotoUrl(fotoUrl);
        dto.setInstagram(peluquero.getInstagram());
        dto.setOrden(peluquero.getOrden());
        return dto;
    }
}
