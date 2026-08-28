package com.segovia.peluqueria.usuario;

/**
 * Roles del sistema, de menos a mas privilegios.
 *
 * <p>PELUQUERO es el intermedio: ve y cierra las citas que tiene asignadas y consulta su
 * propia produccion, pero no toca usuarios, servicios ni la caja. No hay jerarquia
 * automatica de Spring Security detras: cada regla de SecurityConfig dice explicitamente
 * quien pasa, porque una jerarquia implicita en un fichero y las reglas en otro es
 * justo la clase de cosa que abre un endpoint sin que nadie lo note.
 *
 * <p>El nombre cabe en usuarios.rol, que es VARCHAR(10).
 */
public enum Rol {
    USER,
    PELUQUERO,
    ADMIN
}
