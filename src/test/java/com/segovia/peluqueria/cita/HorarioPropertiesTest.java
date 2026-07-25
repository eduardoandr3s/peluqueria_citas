package com.segovia.peluqueria.cita;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprueba que las properties del horario se enlazan bien, en especial
 * {@code dias-cerrados} (lista de enums). El contexto completo solo se levanta en los
 * tests de integracion, asi que aqui se usa el Binder directamente.
 */
class HorarioPropertiesTest {

    private HorarioProperties enlazar(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("peluqueria.horario", HorarioProperties.class)
                .orElseGet(HorarioProperties::new);
    }

    @Test
    void valoresPorDefecto_lunesASabadoDe9A20() {
        HorarioProperties horario = new HorarioProperties();

        assertEquals(LocalTime.of(9, 0), horario.getApertura());
        assertEquals(LocalTime.of(20, 0), horario.getCierre());
        assertEquals(Set.of(DayOfWeek.SUNDAY), horario.getDiasCerrados());
    }

    @Test
    void enlazaHorarioYDiaCerradoUnico() {
        HorarioProperties horario = enlazar(Map.of(
                "peluqueria.horario.apertura", "10:00",
                "peluqueria.horario.cierre", "21:30",
                "peluqueria.horario.dias-cerrados", "SUNDAY"));

        assertEquals(LocalTime.of(10, 0), horario.getApertura());
        assertEquals(LocalTime.of(21, 30), horario.getCierre());
        assertEquals(Set.of(DayOfWeek.SUNDAY), horario.getDiasCerrados());
    }

    @Test
    void enlazaVariosDiasCerradosSeparadosPorComas() {
        HorarioProperties horario = enlazar(Map.of(
                "peluqueria.horario.dias-cerrados", "SUNDAY,MONDAY"));

        assertEquals(Set.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), horario.getDiasCerrados());
    }

    @Test
    void diaCerradoInvalido_falla() {
        assertThrows(Exception.class,
                () -> enlazar(Map.of("peluqueria.horario.dias-cerrados", "LUNES")));
    }
}
