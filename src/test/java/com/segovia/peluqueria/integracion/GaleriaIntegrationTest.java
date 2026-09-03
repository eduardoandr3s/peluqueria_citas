package com.segovia.peluqueria.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La galeria es el unico recurso que se escribe con multipart y se lee sin cuenta,
 * asi que lo que se comprueba aqui es justo esa asimetria: que el escaparate esta
 * abierto y que escribir pide rol y permiso.
 *
 * <p>El reparto entre companeros se prueba por HTTP porque vive en dos sitios que un test
 * unitario no junta: SecurityConfig deja llegar al servicio a quien podria tener el
 * permiso, y el servicio decide segun el dueno de la fila, que no se conoce hasta
 * cargarla.
 */
class GaleriaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenCliente;
    private String tokenAna;
    private String tokenLuis;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM galeria_fotos");
        tokenAdmin = crearAdmin("galeria_admin@test.com");
        tokenCliente = crearCliente("galeria_cliente@test.com");
        tokenAna = crearPeluquero("galeria_ana@test.com", "Ana GAL");
        tokenLuis = crearPeluquero("galeria_luis@test.com", "Luis GAL");

        // El estado de los permisos lo cachea el bean, no la tabla, asi que se apagan por
        // el endpoint: un test no debe heredar lo que encendio el anterior.
        apagarTodo();
    }

    @Test
    void galeriaSeLeeSinCuentaYUnClienteNoEscribe() {
        // El escaparate se ve sin token: es el caso de uso, no un descuido.
        ResponseEntity<List> anonimo = rest.getForEntity(url("/api/galeria"), List.class);
        assertEquals(HttpStatus.OK, anonimo.getStatusCode());

        // Subir sin token y como cliente: cerrado.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria"), HttpMethod.POST, multipart(null, true), String.class)
                        .getStatusCode(),
                "Subir a la galeria sin token debe estar cerrado");
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria"), HttpMethod.POST, multipart(tokenCliente, true), String.class)
                        .getStatusCode(),
                "Un cliente no debe poder subir a la galeria, se enciendan los permisos que sea");

        // El ADMIN sube, y la respuesta ya trae las dos URLs.
        ResponseEntity<Map> subida = rest.exchange(url("/api/galeria"), HttpMethod.POST,
                multipart(tokenAdmin, true), Map.class);
        assertEquals(HttpStatus.OK, subida.getStatusCode());
        Integer idFoto = (Integer) subida.getBody().get("idFoto");
        assertNotNull(subida.getBody().get("urlImagen"));
        assertNotNull(subida.getBody().get("urlMiniatura"));
        assertNotEquals(subida.getBody().get("urlImagen"), subida.getBody().get("urlMiniatura"),
                "Con miniatura, la rejilla no debe acabar sirviendo la imagen grande");
        assertEquals(0, subida.getBody().get("orden"));

        // Y ya se ve desde fuera.
        assertEquals(1, rest.getForEntity(url("/api/galeria"), List.class).getBody().size());

        // Ordenar: cerrado al cliente, permitido al ADMIN.
        var headersCliente = new HttpHeaders();
        headersCliente.setBearerAuth(tokenCliente);
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.PUT,
                        new HttpEntity<>(Map.of("orden", 5), headersCliente), String.class).getStatusCode());

        var headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(tokenAdmin);
        ResponseEntity<Map> editada = rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.PUT,
                new HttpEntity<>(Map.of("titulo", "Degradado con barba", "orden", 5), headersAdmin), Map.class);
        assertEquals(HttpStatus.OK, editada.getStatusCode());
        assertEquals("Degradado con barba", editada.getBody().get("titulo"));
        assertEquals(5, editada.getBody().get("orden"));

        // Borrar: igual.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.DELETE,
                        new HttpEntity<>(headersCliente), String.class).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT,
                rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.DELETE,
                        new HttpEntity<>(headersAdmin), String.class).getStatusCode());
        assertTrue(rest.getForEntity(url("/api/galeria"), List.class).getBody().isEmpty());
    }

    @Test
    void sinMiniaturaLaRejillaCaeALaImagenGrandeYUnFicheroFalsoSeRechaza() {
        ResponseEntity<Map> sinMiniatura = rest.exchange(url("/api/galeria"), HttpMethod.POST,
                multipart(tokenAdmin, false), Map.class);
        assertEquals(HttpStatus.OK, sinMiniatura.getStatusCode());
        assertEquals(sinMiniatura.getBody().get("urlImagen"), sinMiniatura.getBody().get("urlMiniatura"),
                "Sin miniatura el cliente debe recibir la grande, no un hueco");

        // Un ejecutable renombrado a .jpg: se mira el contenido, no la extension.
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("imagen", recurso(new byte[] { 0x4D, 0x5A, 0x00, 0x00 }, "trabajo.jpg"));
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        assertEquals(HttpStatus.BAD_REQUEST,
                rest.exchange(url("/api/galeria"), HttpMethod.POST,
                        new HttpEntity<>(cuerpo, headers), String.class).getStatusCode());
    }


    @Test
    @SuppressWarnings("unchecked")
    void cadaPeluqueroManejaSusFotosYNoLasDeUnCompanero() {
        // Sin el permiso no publica, aunque su rol le deje llegar al servicio.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/galeria"), HttpMethod.POST, multipart(tokenAna, true), String.class)
                        .getStatusCode(),
                "Con GALERIA_SUBIR apagado un peluquero no publica en el escaparate");

        encender("GALERIA_SUBIR");
        Integer deAna = subir(tokenAna);
        Integer deLuis = subir(tokenLuis);

        // La foto sale sellada con quien la subio, y del dueno solo viaja el nombre.
        ResponseEntity<List> conCuenta = rest.exchange(url("/api/galeria"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenAna)), List.class);
        List<Map<String, Object>> fotos = conCuenta.getBody();
        Map<String, Object> suya = porId(fotos, deAna);
        Map<String, Object> ajena = porId(fotos, deLuis);
        assertEquals("Ana GAL", suya.get("subidoPorNombre"));
        assertEquals(true, suya.get("mia"));
        assertEquals("Luis GAL", ajena.get("subidoPorNombre"));
        assertEquals(false, ajena.get("mia"));
        // El listado lo lee cualquiera sin cuenta: ahi no puede salir nada personal.
        assertFalse(suya.containsKey("subidoPor"), "El id del dueno no debe salir en una respuesta publica");
        assertFalse(suya.toString().contains("@"), "El email del dueno no debe salir en una respuesta publica");

        // Sin cuenta ninguna es de nadie, y el escaparate se sirve igual.
        List<Map<String, Object>> anonimo = rest.getForEntity(url("/api/galeria"), List.class).getBody();
        assertEquals(2, anonimo.size());
        assertEquals(false, anonimo.get(0).get("mia"));

        // Editar la suya: primero cerrado, y con el permiso abierto.
        assertEquals(HttpStatus.FORBIDDEN, editar(deAna, tokenAna, Map.of("titulo", "Mi trabajo")).getStatusCode());
        encender("GALERIA_EDITAR_PROPIA");
        assertEquals("Mi trabajo", editar(deAna, tokenAna, Map.of("titulo", "Mi trabajo")).getBody().get("titulo"));

        // Y la de un companero sigue cerrada: es exactamente lo que se pidio.
        assertEquals(HttpStatus.FORBIDDEN, editar(deLuis, tokenAna, Map.of("titulo", "Mio ahora")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, borrar(deLuis, tokenAna).getStatusCode());
        assertEquals(2, rest.getForEntity(url("/api/galeria"), List.class).getBody().size(),
                "Un 403 al borrar no debe dejarse la fila a medias");

        // Reordenar va aparte: mover una foto renumera la rejilla de todos.
        assertEquals(HttpStatus.FORBIDDEN, editar(deAna, tokenAna, Map.of("orden", 3)).getStatusCode(),
                "Editar las suyas no da derecho a reordenar la rejilla");
        encender("GALERIA_ORDENAR");
        assertEquals(3, editar(deLuis, tokenAna, Map.of("orden", 3)).getBody().get("orden"),
                "Con GALERIA_ORDENAR se mueve cualquier foto, tambien la de otro");

        // El permiso que se deja apagado es este; encendido, si toca la ajena.
        encender("GALERIA_EDITAR_AJENA");
        assertEquals(HttpStatus.NO_CONTENT, borrar(deLuis, tokenAna).getStatusCode());

        // Y un ADMIN nunca paso por la matriz: borra la que sea con todo apagado.
        apagarTodo();
        assertEquals(HttpStatus.NO_CONTENT, borrar(deAna, tokenAdmin).getStatusCode());
        assertTrue(rest.getForEntity(url("/api/galeria"), List.class).getBody().isEmpty());
    }

    @Test
    void lasFotosDelNegocioNoTienenDuenoYSoloLasTocaElAdmin() {
        // Una foto sin dueno es la que ya estaba antes de que esto se guardara.
        Integer delNegocio = subir(tokenAdmin);
        jdbcTemplate.update("UPDATE galeria_fotos SET subido_por = NULL WHERE id_foto = ?", delNegocio);

        encender("GALERIA_EDITAR_PROPIA");

        // Sin dueno no es de nadie, asi que tampoco es suya: cuenta como ajena.
        assertEquals(HttpStatus.FORBIDDEN, editar(delNegocio, tokenAna, Map.of("titulo", "Mio")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, borrar(delNegocio, tokenAna).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, borrar(delNegocio, tokenAdmin).getStatusCode());
    }

    // ---- Helpers de permisos y fotos ----

    private void encender(String clave) {
        escribirPermiso(clave, true);
    }

    private void apagarTodo() {
        for (String clave : List.of("GALERIA_SUBIR", "GALERIA_EDITAR_PROPIA", "GALERIA_EDITAR_AJENA",
                "GALERIA_ORDENAR")) {
            escribirPermiso(clave, false);
        }
    }

    private void escribirPermiso(String clave, boolean habilitado) {
        ResponseEntity<List> resp = rest.exchange(url("/api/permisos"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("cambios",
                        List.of(Map.of("rol", "PELUQUERO", "clave", clave, "habilitado", habilitado))),
                        cabecera(tokenAdmin)),
                List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    private Integer subir(String token) {
        ResponseEntity<Map> resp = rest.exchange(url("/api/galeria"), HttpMethod.POST,
                multipart(token, true), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return (Integer) resp.getBody().get("idFoto");
    }

    private ResponseEntity<Map> editar(Integer idFoto, String token, Map<String, Object> cuerpo) {
        return rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.PUT,
                new HttpEntity<>(cuerpo, cabecera(token)), Map.class);
    }

    private ResponseEntity<String> borrar(Integer idFoto, String token) {
        return rest.exchange(url("/api/galeria/" + idFoto), HttpMethod.DELETE,
                new HttpEntity<>(cabecera(token)), String.class);
    }

    private Map<String, Object> porId(List<Map<String, Object>> fotos, Integer idFoto) {
        return fotos.stream()
                .filter(foto -> idFoto.equals(foto.get("idFoto")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No esta en el listado la foto " + idFoto));
    }

    private HttpHeaders cabecera(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String crearPeluquero(String email, String nombre) {
        String password = "Peluquero123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", nombre, "email", email, "password", password, "telefono", "600000095"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'PELUQUERO' WHERE email = ?", email);
        return login(email, password);
    }

    /** Multipart con JPEG minimo: lo que valida el servidor son los primeros bytes. */
    private HttpEntity<MultiValueMap<String, Object>> multipart(String token, boolean conMiniatura) {
        byte[] datos = new byte[64];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("imagen", recurso(datos, "trabajo.jpg"));
        if (conMiniatura) {
            cuerpo.add("miniatura", recurso(datos, "trabajo-mini.jpg"));
        }
        cuerpo.add("titulo", "Degradado");

        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(cuerpo, headers);
    }

    private ByteArrayResource recurso(byte[] datos, String nombre) {
        return new ByteArrayResource(datos) {
            @Override
            public String getFilename() {
                return nombre;
            }
        };
    }

    private String crearAdmin(String email) {
        String password = "Admin123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Admin GAL", "email", email, "password", password, "telefono", "600000097"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", email);
        return login(email, password);
    }

    private String crearCliente(String email) {
        String password = "Cliente123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Cliente GAL", "email", email, "password", password, "telefono", "600000096"),
                Map.class);
        return login(email, password);
    }

    private String login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(url("/api/auth/login"),
                Map.of("email", email, "password", password), Map.class);
        return (String) resp.getBody().get("token");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
