package com.segovia.peluqueria.cita.dto;

import com.segovia.peluqueria.cita.EstadoCita;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cierre de una cita: se realizo, el cliente no vino, o se anula.
 *
 * <p>Es un endpoint aparte del PUT de la cita y no un estado mas porque cerrar tiene
 * efectos que modificar no tiene: congela el importe y la comision, sella quien lo hizo y
 * dispara el aviso al cliente.
 */
@Data
public class CitaCierreDTO {

    @NotNull(message = "El estado de cierre es obligatorio")
    private EstadoCita estado;

    @Size(max = 2000, message = "Las observaciones no pueden pasar de 2000 caracteres")
    private String observaciones;

    /** Si se avisó al cliente por telefono o en persona. El email automatico se manda igual. */
    private Boolean clienteContactado;
}
