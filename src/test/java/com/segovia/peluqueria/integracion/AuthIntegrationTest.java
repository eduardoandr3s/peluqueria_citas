package com.segovia.peluqueria.integracion;

import com.segovia.peluqueria.auth.dto.AuthResponseDTO;
import com.segovia.peluqueria.auth.dto.LoginRequestDTO;
import com.segovia.peluqueria.auth.dto.RefreshRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/auth";

    @Test
    void cicloCompletoAuth() {
        String email = "auth_test@test.com";
        String password = "Password123!";

        // Registrar usuario
        Map<String, Object> registroBody = Map.of(
                "nombre", "Auth Test",
                "email", email,
                "password", password,
                "telefono", "600000001"
        );
        ResponseEntity<Map> registroResp = rest.postForEntity(
                url(BASE + "/registro"), registroBody, Map.class);
        assertEquals(HttpStatus.OK, registroResp.getStatusCode());
        assertEquals(email, registroResp.getBody().get("email"));
        assertFalse(registroResp.getBody().containsKey("password"));

        // Login
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);
        ResponseEntity<AuthResponseDTO> loginResp = rest.postForEntity(
                url(BASE + "/login"), loginRequest, AuthResponseDTO.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        String accessToken = loginResp.getBody().getToken();
        String refreshToken = loginResp.getBody().getRefreshToken();
        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // Llamada autenticada
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(accessToken);
        var citasResp = rest.exchange(
                url("/api/citas"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertEquals(HttpStatus.OK, citasResp.getStatusCode());

        // Refresh: rotar token
        RefreshRequestDTO refreshRequest = new RefreshRequestDTO();
        refreshRequest.setRefreshToken(refreshToken);
        ResponseEntity<AuthResponseDTO> refreshResp = rest.postForEntity(
                url(BASE + "/refresh"), refreshRequest, AuthResponseDTO.class);
        assertEquals(HttpStatus.OK, refreshResp.getStatusCode());
        String nuevoAccess = refreshResp.getBody().getToken();
        String nuevoRefresh = refreshResp.getBody().getRefreshToken();
        assertNotNull(nuevoAccess);
        assertNotNull(nuevoRefresh);
        assertNotEquals(refreshToken, nuevoRefresh);

        // El access token anterior sigue siendo válido (no ha expirado)
        var headersViejo = new org.springframework.http.HttpHeaders();
        headersViejo.setBearerAuth(accessToken);
        var citasConAccessViejo = rest.exchange(
                url("/api/citas"),
                HttpMethod.GET,
                new HttpEntity<>(headersViejo),
                String.class);
        assertEquals(HttpStatus.OK, citasConAccessViejo.getStatusCode());

        // El refresh token anterior NO debe funcionar (ya fue rotado)
        RefreshRequestDTO refreshReuso = new RefreshRequestDTO();
        refreshReuso.setRefreshToken(refreshToken);
        ResponseEntity<Map> reusoResp = rest.postForEntity(
                url(BASE + "/refresh"), refreshReuso, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, reusoResp.getStatusCode());

        // El nuevo refresh token funciona
        RefreshRequestDTO refreshConNuevo = new RefreshRequestDTO();
        refreshConNuevo.setRefreshToken(nuevoRefresh);
        ResponseEntity<AuthResponseDTO> refreshNuevoResp = rest.postForEntity(
                url(BASE + "/refresh"), refreshConNuevo, AuthResponseDTO.class);
        assertEquals(HttpStatus.OK, refreshNuevoResp.getStatusCode());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
