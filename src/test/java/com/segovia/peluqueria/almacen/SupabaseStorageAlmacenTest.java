package com.segovia.peluqueria.almacen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Tests del adaptador de Supabase contra un servidor simulado.
 *
 * <p>Existen porque este es el unico punto del almacen que no se puede ejercitar en
 * desarrollo: sin credenciales se usa {@link AlmacenLocal}, asi que la forma de las
 * peticiones y, sobre todo, el armado de la URL firmada solo se comprobarian en
 * produccion.
 */
class SupabaseStorageAlmacenTest {

    private static final String BASE_URL = "https://proyecto.supabase.co";
    private static final String CLAVE = "invalid";

    private MockRestServiceServer servidor;
    private SupabaseStorageAlmacen almacen;

    @BeforeEach
    void setUp() {
        AlmacenProperties propiedades = new AlmacenProperties();
        // Con barra final a proposito: se normaliza para no acabar con dobles barras.
        propiedades.setSupabaseUrl(BASE_URL + "/");
        propiedades.setServiceKey("clave-de-servicio");

        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        almacen = new SupabaseStorageAlmacen(propiedades, constructor);
    }

    @Test
    void guardar_subeElContenidoConLaClaveYPermiteSustituir() {
        servidor.expect(requestTo(BASE_URL + "/storage/v1/object/avatares/7/foto.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer clave-de-servicio"))
                // La pasarela de Supabase enruta por apikey: sin ella hay montajes que dan 401.
                .andExpect(header("apikey", "clave-de-servicio"))
                // Sin upsert, sustituir la foto fallaria por clave existente.
                .andExpect(header("x-upsert", "true"))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andRespond(withSuccess());

        String clave = almacen.guardar("avatares", "7/foto.jpg", new byte[] { 1, 2 }, "image/jpeg");

        assertEquals("7/foto.jpg", clave);
        servidor.verify();
    }

    @Test
    void urlFirmada_prefijaLaRutaRelativaQueDevuelveSupabase() {
        servidor.expect(requestTo(BASE_URL + "/storage/v1/object/sign/avatares/7/foto.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                // signedURL viene RELATIVA a /storage/v1: es el error clasico de esta API.
                .andRespond(withSuccess(
                        "{\"signedURL\":\"/object/sign/avatares/7/foto.jpg?token=abc\"}",
                        MediaType.APPLICATION_JSON));

        String url = almacen.urlFirmada("avatares", "7/foto.jpg", Duration.ofHours(1));

        assertEquals(BASE_URL + "/storage/v1/object/sign/avatares/7/foto.jpg?token=abc", url);
        servidor.verify();
    }

    @Test
    void urlFirmada_sinUrlEnLaRespuesta_lanzaAlmacenException() {
        servidor.expect(requestTo(BASE_URL + "/storage/v1/object/sign/avatares/" + CLAVE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // Devolver null seria peor: el cliente recibiria un usuario "sin foto" cuando si la tiene.
        assertThrows(AlmacenException.class,
                () -> almacen.urlFirmada("avatares", CLAVE, Duration.ofHours(1)));
    }

    @Test
    void urlFirmada_siElAlmacenFalla_lanzaAlmacenException() {
        servidor.expect(requestTo(BASE_URL + "/storage/v1/object/sign/avatares/" + CLAVE))
                .andRespond(withServerError());

        // AlmacenException se traduce a 502: el fallo es del servicio de arriba.
        assertThrows(AlmacenException.class,
                () -> almacen.urlFirmada("avatares", CLAVE, Duration.ofHours(1)));
    }

    @Test
    void guardar_siElAlmacenFalla_lanzaAlmacenException() {
        servidor.expect(requestTo(BASE_URL + "/storage/v1/object/avatares/" + CLAVE))
                .andRespond(withServerError());

        assertThrows(AlmacenException.class,
                () -> almacen.guardar("avatares", CLAVE, new byte[] { 1 }, "image/jpeg"));
    }

    @Test
    void borrar_esBestEffort_noPropagaElFallo() {
        servidor.expect(requestTo(BASE_URL + "/storage/v1/object/avatares/" + CLAVE))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        // Un huerfano ocupando cuota no es motivo para tumbar la operacion del usuario.
        assertDoesNotThrow(() -> almacen.borrar("avatares", CLAVE));
        servidor.verify();
    }

    @Test
    void urlDeLectura_apuntaAlaRutaPublicaDelBucket() {
        assertEquals(BASE_URL + "/storage/v1/object/public/servicios/3/foto.jpg",
                almacen.urlDeLectura("servicios", "3/foto.jpg"));
    }
}
