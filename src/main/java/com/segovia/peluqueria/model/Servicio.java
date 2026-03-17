package com.segovia.peluqueria.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data // Genera getters, setters, toString, equals y hashCode automáticamente
@NoArgsConstructor // Genera un constructor sin argumentos
@AllArgsConstructor // Genera un constructor con todos los argumentos
@Entity // Marca esta clase como una entidad de JPA
@Table(name = "servicios") // Especifica el nombre de la tabla en la base de datos
public class Servicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Genera automáticamente el ID
    private Integer id_servicio;

    @Column(nullable = false) // Especifica que este campo no puede ser nulo
    private String nombre;

    @Column(columnDefinition = "TEXT") // Especifica que este campo es de tipo TEXT en la base de datos
    private String descripcion;

    @Column(precision = 10, scale = 2) // Especifica la precisión y escala para el campo de precio
    private BigDecimal precio;

    // si no se especifica el tipo de dato, se asume que es un entero con el mismo nombre que el campo en la base de datos
    private Integer duracion; // Duración en minutos

    public Integer getId_servicio() {
        return id_servicio;
    }

    public void setId_servicio(Integer id_servicio) {
        this.id_servicio = id_servicio;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public Integer getDuracion() {
        return duracion;
    }

    public void setDuracion(Integer duracion) {
        this.duracion = duracion;
    }
}

