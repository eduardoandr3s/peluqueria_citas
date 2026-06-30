package com.segovia.peluqueria.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoca toda la familia (cadena de rotaciones) al detectar el reuso de un token ya rotado.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revocado = true WHERE t.familia = :familia AND t.revocado = false")
    void revocarFamilia(@Param("familia") String familia);
}
