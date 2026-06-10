package com.segovia.peluqueria.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdUsuarioNot(String email, Integer idUsuario);

    Optional<Usuario> findByEmail(String email);

    Page<Usuario> findByActivoTrue(Pageable pageable);

    long countByRolAndActivoTrue(Rol rol);
}
