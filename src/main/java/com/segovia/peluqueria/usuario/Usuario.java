package com.segovia.peluqueria.usuario;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
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

    @JsonIgnore
    @Column(length = 255, nullable = false)
    private String password;

    @Column(name = "fecha_registro")
    private LocalDate fechaRegistro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10) default 'USER'")
    private Rol rol = Rol.USER;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean activo = true;

    // Se incrementa para invalidar tokens emitidos antes (ej. cambio de password o de rol).
    // El JWT lleva esta version como claim; el filtro la compara con la de BD en cada request.
    @Column(name = "token_version", nullable = false, columnDefinition = "integer default 1")
    private Integer tokenVersion = 1;
}
