package com.segovia.peluqueria.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(length = 50, nullable = false)
    private String nombre;

    @Column(length = 100, nullable = false, unique = true)
    private String email;

    @Column(length = 15)
    private String telefono;

    @Column(length = 255, nullable = false)
    private String password;

    @Column(name = "fecha_registro")
    private LocalDate fechaRegistro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10) default 'USER'")
    private Rol rol = Rol.USER;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean activo = true;
}
