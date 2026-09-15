package com.segovia.peluqueria.negocio;

import com.segovia.peluqueria.negocio.dto.ActualizarNegocioDTO;
import com.segovia.peluqueria.negocio.dto.NegocioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NegocioServiceTest {

    private NegocioRepository negocioRepository;
    private NegocioService negocioService;

    @BeforeEach
    void setUp() {
        negocioRepository = mock(NegocioRepository.class);
        when(negocioRepository.findById(Negocio.ID)).thenReturn(Optional.of(filaSembrada()));
        when(negocioRepository.save(any(Negocio.class))).thenAnswer(inv -> inv.getArgument(0));
        negocioService = new NegocioService(negocioRepository);
    }

    /** La fila tal y como la deja la migracion V17. */
    private Negocio filaSembrada() {
        Negocio negocio = new Negocio();
        negocio.setId(Negocio.ID);
        negocio.setNombre("Lalo Segovia · Peluquería");
        negocio.setTelefono("+34 963 12 34 56");
        negocio.setEmail("hola@lalosegovia.es");
        negocio.setDireccion("Carrer de Colón, 42");
        negocio.setLocalidad("46004 València, España");
        negocio.setHoraApertura(LocalTime.of(9, 0));
        negocio.setHoraCierre(LocalTime.of(20, 0));
        negocio.setDiasCerrados("SUNDAY");
        return negocio;
    }

    private ActualizarNegocioDTO peticion(LocalTime apertura, LocalTime cierre, Set<DayOfWeek> cerrados) {
        ActualizarNegocioDTO request = new ActualizarNegocioDTO();
        request.setNombre("Peluqueria Nueva");
        request.setHoraApertura(apertura);
        request.setHoraCierre(cierre);
        request.setDiasCerrados(cerrados);
        return request;
    }

    @Test
    void elHorarioSaleDeLaFilaYNoDeLasProperties() {
        // Es el motivo entero de esta tabla: mientras el horario estaba en
        // application.properties no podia ser distinto para cada peluqueria sin tocar el
        // despliegue y reiniciar.
        assertEquals(LocalTime.of(9, 0), negocioService.apertura());
        assertEquals(LocalTime.of(20, 0), negocioService.cierre());
        assertEquals(EnumSet.of(DayOfWeek.SUNDAY), negocioService.diasCerrados());
    }

    @Test
    void laFichaSeLeeUnaVezYSeCachea() {
        negocioService.ficha();
        negocioService.apertura();
        negocioService.diasCerrados();

        verify(negocioRepository, times(1)).findById(Negocio.ID);
    }

    @Test
    void guardarTiraLaCache() {
        // Sin esto, cambiar el horario desde el panel no movria los huecos ofrecidos hasta
        // el siguiente reinicio.
        assertEquals(LocalTime.of(9, 0), negocioService.apertura());

        negocioService.actualizar(peticion(LocalTime.of(10, 30), LocalTime.of(21, 0),
                EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)));

        assertEquals(LocalTime.of(10, 30), negocioService.apertura());
        assertEquals(LocalTime.of(21, 0), negocioService.cierre());
        assertEquals(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), negocioService.diasCerrados());
    }

    @Test
    void aperturaPosteriorAlCierreSeRechaza() {
        // La tabla tiene su CHECK, pero dejar que salte convertiria un error del formulario
        // en un 500 sin nada que leer.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> negocioService.actualizar(peticion(LocalTime.of(20, 0), LocalTime.of(9, 0), Set.of())));
        assertTrue(ex.getMessage().contains("anterior"));
    }

    @Test
    void aperturaIgualAlCierreSeRechaza() {
        // Un horario de cero minutos no es "cerrado": para eso estan los dias cerrados.
        assertThrows(IllegalArgumentException.class,
                () -> negocioService.actualizar(peticion(LocalTime.of(9, 0), LocalTime.of(9, 0), Set.of())));
    }

    @Test
    void sinDiasCerradosSeAbreTodosLosDias() {
        NegocioDTO ficha = negocioService.actualizar(
                peticion(LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of()));

        assertTrue(ficha.getDiasCerrados().isEmpty());
        assertTrue(negocioService.diasCerrados().isEmpty());
    }

    @Test
    void diasCerradosNuloSeEntiendeComoNinguno() {
        // El formulario puede no mandar la lista; eso no es un error, es "abro todos los dias".
        ActualizarNegocioDTO request = peticion(LocalTime.of(9, 0), LocalTime.of(20, 0), null);

        assertTrue(negocioService.actualizar(request).getDiasCerrados().isEmpty());
    }

    @Test
    void losCamposOpcionalesVaciosSeGuardanComoNulos() {
        // Un string vacio y un null significan lo mismo aqui —"no lo tengo"— y guardar los
        // dos obligaria a cada pantalla a comprobar las dos cosas.
        ActualizarNegocioDTO request = peticion(LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of());
        request.setTelefono("   ");
        request.setEslogan("");

        NegocioDTO ficha = negocioService.actualizar(request);

        assertNull(ficha.getTelefono());
        assertNull(ficha.getEslogan());
    }

    @Test
    void unDiaQueNoEsUnDiaValidoSeIgnoraEnVezDeTumbarElArranque() {
        // Es texto en una columna: el precio de equivocarse es abrir un dia de mas, no que
        // la peluqueria entera deje de responder.
        Negocio fila = filaSembrada();
        fila.setDiasCerrados("SUNDAY,LUNES,MONDAY");
        when(negocioRepository.findById(Negocio.ID)).thenReturn(Optional.of(fila));

        assertEquals(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), negocioService.diasCerrados());
    }

    @Test
    void sinFilaDeNegocioSeDiceQueLaInstalacionEstaIncompleta() {
        // La siembra la migracion y el CHECK impide que haya otra: si falta, la instalacion
        // esta rota y vale mas decirlo que inventarse un nombre y un horario.
        when(negocioRepository.findById(Negocio.ID)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> negocioService.ficha());
        assertTrue(ex.getMessage().contains("V17"));
    }

    @Test
    void laFichaLlevaLoQueLasDosAppsPintanSinSesion() {
        NegocioDTO ficha = negocioService.ficha();

        assertEquals("Lalo Segovia · Peluquería", ficha.getNombre());
        assertEquals("+34 963 12 34 56", ficha.getTelefono());
        assertEquals("Carrer de Colón, 42", ficha.getDireccion());
        assertEquals(LocalTime.of(9, 0), ficha.getHoraApertura());
    }
}
