package com.segovia.peluqueria.peluquero.dto;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Traduce las especialidades entre las dos formas en que viven: una cadena con comas en la
 * columna y una lista en el API.
 *
 * <p>Esta aqui, y en un solo sitio, porque la conversion es lo unico que hace fragil la
 * decision de guardarlas como texto: si cada frontend partiera la cadena por su cuenta,
 * uno ensenaria etiquetas vacias y el otro con espacios delante. El servidor la parte al
 * leer y la normaliza al escribir, y ninguno de los dos se enteran de que hay una coma.
 */
public final class Especialidades {

    /** Tope de etiquetas. No es una restriccion tecnica: es que no caben mas en la ficha. */
    public static final int MAXIMO = 12;

    /** Tope por etiqueta. "Coloracion y mechas" cabe de sobra; un parrafo no es una etiqueta. */
    public static final int MAXIMO_LONGITUD = 40;

    /**
     * Lo que aguanta la columna. Se comprueba aqui y no se deja para la base de datos: 12
     * etiquetas de 40 caracteres suman mas de 255, asi que sin esta guarda el tope de dos
     * campos que por separado son validos acabaria en un 500 de Postgres en vez de en un
     * 400 que explique que sobra texto.
     */
    private static final int MAXIMO_COLUMNA = 255;

    private Especialidades() {
    }

    /** La columna a lista. Una columna vacia o null es una lista vacia, nunca null. */
    public static List<String> aLista(String guardado) {
        if (guardado == null || guardado.isBlank()) {
            return List.of();
        }
        return Arrays.stream(guardado.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .toList();
    }

    /**
     * La lista a columna, normalizada: sin espacios de sobra, sin vacias y sin repetidas
     * (ignorando mayusculas, que es como las teclearia una persona). Devuelve null cuando
     * no queda nada, para que la columna quede vacia en vez de con una cadena de blancos.
     *
     * @throws IllegalArgumentException si se pasa de {@link #MAXIMO} etiquetas o alguna es
     *                                  mas larga que {@link #MAXIMO_LONGITUD}
     */
    public static String aColumna(List<String> lista) {
        if (lista == null || lista.isEmpty()) {
            return null;
        }

        Set<String> vistas = new LinkedHashSet<>();
        List<String> limpias = lista.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .filter(e -> vistas.add(e.toLowerCase()))
                .toList();

        if (limpias.isEmpty()) {
            return null;
        }
        if (limpias.size() > MAXIMO) {
            throw new IllegalArgumentException("No se pueden poner mas de " + MAXIMO + " especialidades.");
        }
        for (String especialidad : limpias) {
            if (especialidad.length() > MAXIMO_LONGITUD) {
                throw new IllegalArgumentException(
                        "La especialidad '" + especialidad + "' pasa de " + MAXIMO_LONGITUD + " caracteres.");
            }
            // La coma es el separador: dentro de una etiqueta partiria la lista al leerla.
            if (especialidad.indexOf(',') >= 0) {
                throw new IllegalArgumentException(
                        "Una especialidad no puede llevar comas; manda cada una como un elemento de la lista.");
            }
        }
        String columna = String.join(", ", limpias);
        if (columna.length() > MAXIMO_COLUMNA) {
            throw new IllegalArgumentException(
                    "Las especialidades suman " + columna.length() + " caracteres y no caben en "
                            + MAXIMO_COLUMNA + "; deja menos o mas cortas.");
        }
        return columna;
    }
}
