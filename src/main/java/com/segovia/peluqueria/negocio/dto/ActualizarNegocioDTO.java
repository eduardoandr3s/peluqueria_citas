package com.segovia.peluqueria.negocio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * Lo que manda el formulario del panel. Se envia entera: es una ficha corta que se edita
 * de una vez, y un PUT parcial obligaria a distinguir "no lo mando" de "lo dejo vacio"
 * en ocho campos que casi todos son opcionales.
 *
 * <p>El nombre y el horario son obligatorios porque sin ellos no hay producto: el nombre
 * encabeza las dos apps y el horario decide que horas se ofrecen al agendar.
 */
@Data
public class ActualizarNegocioDTO {

    @NotBlank(message = "El nombre del negocio es obligatorio.")
    @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres.")
    private String nombre;

    @Size(max = 200, message = "El eslogan no puede pasar de 200 caracteres.")
    private String eslogan;

    @Size(max = 30, message = "El telefono no puede pasar de 30 caracteres.")
    private String telefono;

    @Email(message = "El correo de contacto no es valido.")
    @Size(max = 160, message = "El correo no puede pasar de 160 caracteres.")
    private String email;

    @Size(max = 200, message = "La direccion no puede pasar de 200 caracteres.")
    private String direccion;

    @Size(max = 120, message = "La localidad no puede pasar de 120 caracteres.")
    private String localidad;

    @Size(max = 500, message = "La URL del logo no puede pasar de 500 caracteres.")
    private String logoUrl;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "El color debe ir en formato #RRGGBB.")
    private String colorPrimario;

    @NotNull(message = "La hora de apertura es obligatoria.")
    private LocalTime horaApertura;

    @NotNull(message = "La hora de cierre es obligatoria.")
    private LocalTime horaCierre;

    /**
     * Dias de la semana en los que no se abre nunca. Puede venir vacia (se abre todos los
     * dias); si viene null se entiende lo mismo, para que el formulario pueda omitirla.
     */
    private Set<DayOfWeek> diasCerrados;
}
