package com.segovia.peluqueria.peluquero.dto;

import com.segovia.peluqueria.peluquero.Peluquero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha completa de un peluquero, con la comision y la cuenta vinculada. Es de ADMIN y
 * NO se reutiliza en {@code PeluqueroResponseDTO}, que va anidado en cada cita y lo leen
 * los clientes: lo que cobra un profesional no se filtra por ahi.
 */
@Data
public class PeluqueroGestionDTO {

    private Integer idPeluquero;
    private String nombre;
    private Boolean activo;

    /**
     * Porcentaje por defecto, o <b>null si el modulo de comisiones esta apagado</b>: ahi la
     * ficha no habla de dinero. El valor sigue guardado en la base de datos y vuelve tal
     * cual si el modulo se enciende otra vez.
     */
    private BigDecimal comisionPorcentaje;

    /** Sitio en la pantalla "Equipo". Se cambia por {@code PUT /api/peluqueros/{id}}. */
    private Integer orden;

    private Integer usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;

    private List<ComisionServicioDTO> comisionesPorServicio;

    /**
     * El CV, para que la pestana del panel lo pinte sin una segunda peticion. Se lee por
     * aqui y se escribe por {@code PUT /api/peluqueros/{id}/cv}, que reemplaza el bloque
     * entero: en este DTO un null significa "no lo toques" y con eso no se puede vaciar un
     * campo de texto.
     */
    private PeluqueroCvDTO cv;

    /**
     * @param conComision si el modulo de comisiones esta encendido. Apagado, la ficha sale
     *                    sin porcentaje y sin excepciones por servicio: no es que sean cero,
     *                    es que aqui no se comisiona.
     */
    public static PeluqueroGestionDTO desde(Peluquero peluquero, List<ComisionServicioDTO> comisiones,
                                            String fotoUrl, boolean conComision) {
        PeluqueroGestionDTO dto = new PeluqueroGestionDTO();
        dto.setIdPeluquero(peluquero.getIdPeluquero());
        dto.setNombre(peluquero.getNombre());
        dto.setActivo(peluquero.getActivo());
        dto.setComisionPorcentaje(conComision ? peluquero.getComisionPorcentaje() : null);
        if (peluquero.getUsuario() != null) {
            dto.setUsuarioId(peluquero.getUsuario().getIdUsuario());
            dto.setUsuarioNombre(peluquero.getUsuario().getNombre());
            dto.setUsuarioEmail(peluquero.getUsuario().getEmail());
        }
        dto.setComisionesPorServicio(conComision ? comisiones : List.of());
        dto.setOrden(peluquero.getOrden());
        dto.setCv(PeluqueroCvDTO.desde(peluquero, fotoUrl));
        return dto;
    }
}
