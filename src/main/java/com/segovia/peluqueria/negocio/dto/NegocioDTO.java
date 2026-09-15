package com.segovia.peluqueria.negocio.dto;

import com.segovia.peluqueria.negocio.Negocio;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * La ficha del negocio tal y como la leen el panel y las dos apps.
 *
 * <p><b>Es la misma para todos y se sirve sin token.</b> No hay una version publica y otra
 * privada porque aqui no hay nada privado: el nombre, el telefono, la direccion y el
 * horario son justo lo que un negocio quiere que se sepa. Partirla en dos DTOs habria sido
 * inventar un secreto que no existe.
 */
@Data
public class NegocioDTO {

    private String nombre;
    private String eslogan;
    private String telefono;
    private String email;
    private String direccion;
    private String localidad;
    private String logoUrl;
    private String colorPrimario;
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    /** Dias de la semana en los que no se abre nunca. Vacio = se abre todos los dias. */
    private Set<DayOfWeek> diasCerrados;

    public NegocioDTO(Negocio negocio, Set<DayOfWeek> diasCerrados) {
        this.nombre = negocio.getNombre();
        this.eslogan = negocio.getEslogan();
        this.telefono = negocio.getTelefono();
        this.email = negocio.getEmail();
        this.direccion = negocio.getDireccion();
        this.localidad = negocio.getLocalidad();
        this.logoUrl = negocio.getLogoUrl();
        this.colorPrimario = negocio.getColorPrimario();
        this.horaApertura = negocio.getHoraApertura();
        this.horaCierre = negocio.getHoraCierre();
        this.diasCerrados = diasCerrados;
    }
}
