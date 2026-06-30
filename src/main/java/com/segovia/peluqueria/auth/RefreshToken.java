package com.segovia.peluqueria.auth;

import com.segovia.peluqueria.usuario.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Refresh token de larga duracion (rotacion + deteccion de reuso). Como en
 * {@link PasswordResetToken} solo se persiste el hash (SHA-256); el valor en claro se
 * entrega al cliente y nunca se almacena.
 *
 * <p>Cada token pertenece a una <b>familia</b> (la cadena de rotaciones nacida de un login):
 * al rotar se conserva la familia y al detectar el reuso de un token ya rotado se revoca
 * la familia entera, sin afectar a otras sesiones/dispositivos del mismo usuario.
 *
 * <p>Guarda ademas el {@code tokenVersion} del usuario en el momento de emitirse: si la
 * version cambia (cambio de password o de rol), el refresh deja de ser valido al rotar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 36)
    private String familia;

    // tokenVersion del usuario al emitir este refresh; se compara con el actual al rotar.
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado = false;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public boolean estaCaducado() {
        return LocalDateTime.now().isAfter(expiraEn);
    }
}
