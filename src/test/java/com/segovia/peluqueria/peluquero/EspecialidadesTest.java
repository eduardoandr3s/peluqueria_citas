package com.segovia.peluqueria.peluquero;

import com.segovia.peluqueria.peluquero.dto.Especialidades;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las especialidades se guardan como una cadena con comas, y esta clase es lo unico que
 * sujeta esa decision. Lo que se prueba aqui es que ninguna de las dos formas se pueda
 * corromper: ni la columna con una etiqueta que lleve una coma dentro, ni la lista con
 * huecos que el frontend acabaria pintando como una etiqueta vacia.
 */
class EspecialidadesTest {

    @Test
    void aListaTroceaYLimpiaLoQueHayaGuardado() {
        assertEquals(List.of("Degradados", "Barba", "Color"),
                Especialidades.aLista("Degradados, Barba,Color"));
        // Comas de sobra o espacios sueltos no deben salir como etiquetas vacias.
        assertEquals(List.of("Degradados", "Barba"), Especialidades.aLista("Degradados,, , Barba,"));
    }

    @Test
    void aListaDevuelveVaciaYNuncaNull() {
        assertTrue(Especialidades.aLista(null).isEmpty());
        assertTrue(Especialidades.aLista("").isEmpty());
        assertTrue(Especialidades.aLista("   ").isEmpty());
        assertTrue(Especialidades.aLista(", ,").isEmpty());
    }

    @Test
    void aColumnaNormalizaYQuitaRepetidas() {
        assertEquals("Degradados, Barba", Especialidades.aColumna(List.of("  Degradados ", "Barba")));
        // Dos veces lo mismo con otras mayusculas es lo mismo, y gana la primera forma.
        assertEquals("Degradados", Especialidades.aColumna(List.of("Degradados", "degradados", "DEGRADADOS")));
    }

    @Test
    void aColumnaDevuelveNullCuandoNoQuedaNada() {
        assertNull(Especialidades.aColumna(null));
        assertNull(Especialidades.aColumna(List.of()));
        // Una lista de blancos es no tener especialidades, no tener una cadena de espacios.
        assertNull(Especialidades.aColumna(Arrays.asList("  ", null, "")));
    }

    @Test
    void aColumnaRechazaUnaEtiquetaConComa() {
        // La coma es el separador: dentro de una etiqueta partiria la lista al releerla.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Especialidades.aColumna(List.of("Color, mechas y balayage")));
        assertTrue(e.getMessage().contains("comas"));
    }

    @Test
    void aColumnaRechazaDemasiadasOdemasiadoLargas() {
        List<String> muchas = new ArrayList<>();
        for (int i = 0; i < Especialidades.MAXIMO + 1; i++) {
            muchas.add("Especialidad " + i);
        }
        assertThrows(IllegalArgumentException.class, () -> Especialidades.aColumna(muchas));

        String larga = String.join("", Collections.nCopies(Especialidades.MAXIMO_LONGITUD + 1, "a"));
        assertThrows(IllegalArgumentException.class, () -> Especialidades.aColumna(List.of(larga)));
    }

    @Test
    void aColumnaRechazaLoQueNoCabeEnLaColumnaAunqueCadaEtiquetaSeaValida() {
        // El tope de la columna son 255: doce etiquetas de 40 son validas una por una y
        // juntas se pasan. Sin esta comprobacion el error saldria de Postgres como un 500.
        String etiqueta = String.join("", Collections.nCopies(Especialidades.MAXIMO_LONGITUD, "a"));
        List<String> alLimite = new ArrayList<>();
        for (int i = 0; i < Especialidades.MAXIMO; i++) {
            // Distintas entre si, que si no se quedaria una sola al deduplicar.
            alEnesima(alLimite, etiqueta, i);
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Especialidades.aColumna(alLimite));
        assertTrue(e.getMessage().contains("caracteres"), e.getMessage());
    }

    private void alEnesima(List<String> destino, String etiqueta, int i) {
        destino.add(etiqueta.substring(0, etiqueta.length() - 2) + String.format("%02d", i));
    }

    @Test
    void loQueSeGuardaSeVuelveALeerIgual() {
        List<String> original = List.of("Degradados", "Barba", "Color y mechas");
        assertEquals(original, Especialidades.aLista(Especialidades.aColumna(original)));
    }
}
