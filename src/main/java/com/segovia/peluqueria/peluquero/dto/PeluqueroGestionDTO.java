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
    private BigDecimal comisionPorcentaje;

    private Integer usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;

    private List<ComisionServicioDTO> comisionesPorServicio;

    public static PeluqueroGestionDTO desde(Peluquero peluquero, List<ComisionServicioDTO> comisiones) {
        PeluqueroGestionDTO dto = new PeluqueroGestionDTO();
        dto.setIdPeluquero(peluquero.getIdPeluquero());
        dto.setNombre(peluquero.getNombre());
        dto.setActivo(peluquero.getActivo());
        dto.setComisionPorcentaje(peluquero.getComisionPorcentaje());
        if (peluquero.getUsuario() != null) {
            dto.setUsuarioId(peluquero.getUsuario().getIdUsuario());
            dto.setUsuarioNombre(peluquero.getUsuario().getNombre());
            dto.setUsuarioEmail(peluquero.getUsuario().getEmail());
        }
        dto.setComisionesPorServicio(comisiones);
        return dto;
    }
}
