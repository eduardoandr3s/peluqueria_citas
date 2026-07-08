package com.segovia.peluqueria.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdUsuarioNot(String email, Integer idUsuario);

    Optional<Usuario> findByEmail(String email);

    Page<Usuario> findByActivoTrue(Pageable pageable);

    long countByRolAndActivoTrue(Rol rol);

    long countByFechaRegistroBetween(LocalDate desde, LocalDate hasta);

    // Busqueda por nombre o email (contains, case-insensitive), combinable con incluirInactivos.
    // Si incluirInactivos es false solo devuelve usuarios activos. Filtra sobre toda la tabla, no solo la pagina.
    @Query("SELECT u FROM Usuario u WHERE "
            + "(:incluirInactivos = true OR u.activo = true) AND ("
            + "LOWER(u.nombre) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Usuario> buscar(@Param("search") String search,
                         @Param("incluirInactivos") boolean incluirInactivos,
                         Pageable pageable);
}
