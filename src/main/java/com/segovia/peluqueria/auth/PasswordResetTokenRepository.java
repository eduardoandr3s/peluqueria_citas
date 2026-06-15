package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Invalida los tokens vigentes (no usados) de un usuario al solicitar uno nuevo,
    // de modo que solo el ultimo enlace enviado quede activo.
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usado = true WHERE t.usuario = :usuario AND t.usado = false")
    void invalidarVigentesDe(@Param("usuario") Usuario usuario);
}
