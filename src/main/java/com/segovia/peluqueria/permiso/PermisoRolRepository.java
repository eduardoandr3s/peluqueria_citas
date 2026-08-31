package com.segovia.peluqueria.permiso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermisoRolRepository extends JpaRepository<PermisoRol, PermisoRolId> {

    Optional<PermisoRol> findByRolAndClave(com.segovia.peluqueria.usuario.Rol rol, String clave);
}
