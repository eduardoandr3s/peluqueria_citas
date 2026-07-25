package com.segovia.peluqueria.cita;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Horario laboral de la peluquería, externalizado a properties
 * ({@code peluqueria.horario.apertura} / {@code peluqueria.horario.cierre} /
 * {@code peluqueria.horario.dias-cerrados}).
 * Valores por defecto: L-S de 09:00 a 20:00.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "peluqueria.horario")
public class HorarioProperties {

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime apertura = LocalTime.of(9, 0);

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime cierre = LocalTime.of(20, 0);

    /**
     * Días de la semana en los que no se abre nunca. Los cierres de un día concreto
     * (festivos) no van aquí, sino en la tabla {@code dias_bloqueados}.
     */
    private Set<DayOfWeek> diasCerrados = EnumSet.of(DayOfWeek.SUNDAY);
}
