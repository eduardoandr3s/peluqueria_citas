package com.segovia.peluqueria.servicio;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "servicios")
public class Servicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Integer idServicio;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal precio;

    private Integer duracion;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean activo = true;

    /**
     * Clave del objeto en el almacen de ficheros, no la URL. La URL se construye
     * al leer (ver {@code ServicioService}) para no atar las filas a un bucket.
     */
    @Column(name = "imagen_clave", length = 255)
    private String imagenClave;
}
