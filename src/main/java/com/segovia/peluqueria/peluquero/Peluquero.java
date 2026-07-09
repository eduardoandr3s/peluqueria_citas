package com.segovia.peluqueria.peluquero;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "peluqueros")
public class Peluquero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_peluquero")
    private Integer idPeluquero;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean activo = true;
}
