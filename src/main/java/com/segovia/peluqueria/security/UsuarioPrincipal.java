package com.segovia.peluqueria.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * UserDetails propio que ademas del email/password/rol expone el idUsuario y el tokenVersion.
 * El filtro JWT compara el tokenVersion del token con el de la BD para poder revocar tokens.
 */
public class UsuarioPrincipal implements UserDetails {

    private final String email;
    private final String password;
    private final boolean activo;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Integer idUsuario;
    private final Integer tokenVersion;

    public UsuarioPrincipal(String email,
                            String password,
                            boolean activo,
                            Collection<? extends GrantedAuthority> authorities,
                            Integer idUsuario,
                            Integer tokenVersion) {
        this.email = email;
        this.password = password;
        this.activo = activo;
        this.authorities = authorities;
        this.idUsuario = idUsuario;
        this.tokenVersion = tokenVersion;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }
}
