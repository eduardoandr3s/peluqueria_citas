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
 * El CV del peluquero es el segundo recurso del proyecto que se lee SIN cuenta, y el
 * primero que se sirve desde una tabla que tambien guarda datos de la cuenta y lo que cobra
 * cada uno. Eso es lo que se prueba aqui por HTTP y no en un test unitario: que el JSON que
 * sale por la puerta abierta no lleva nada de eso, y que la regla de {@code /publicos} esta
 * de verdad ANTES de la de {@code /api/peluqueros/**} en la cadena de filtros.
 *
 * <p>Lo otro que solo se ve por HTTP es el reparto: el rol deja llegar al servicio y dentro
 * decide de quien es la ficha, que no se sabe hasta cargarla.
 */
class PeluqueroCvIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenAdmin;
    private String tokenCliente;
    private String tokenAna;
    private String tokenLuis;

    private Integer fichaAna;
    private Integer fichaLuis;
    private Integer fichaInactiva;

    @BeforeEach
    void setUp() {
        // Las fichas de esta suite se borran por nombre: el contenedor es compartido y otras
        // pruebas dejan las suyas, que aqui no se pueden tocar.
        jdbcTemplate.update("DELETE FROM peluqueros WHERE nombre LIKE '%CV'");

        tokenAdmin = crearAdmin("cv_admin@test.com");
        tokenCliente = crearCliente("cv_cliente@test.com");
        tokenAna = crearPeluquero("cv_ana@test.com", "600000181");
        tokenLuis = crearPeluquero("cv_luis@test.com", "600000182");

        fichaAna = crearFicha("Ana CV", "cv_ana@test.com", true, 2);
        fichaLuis = crearFicha("Luis CV", "cv_luis@test.com", true, 1);
        fichaInactiva = crearFicha("Marta CV", null, false, 0);

        // El estado de los permisos lo cachea el bean, asi que se apaga por el endpoint: un
        // test no debe heredar lo que encendio el anterior.
        permiso(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void elEquipoSeLeeSinCuentaYnoFiltraNadaDeLaCuentaNiLaComision() {
        cv(fichaAna, Map.of(
                "presentacion", "Llevo la barberia desde 2015",
                "especialidades", List.of("Degradados", "Barba"),
                "aniosExperiencia", 9,
                "instagram", "https://www.instagram.com/ana.corta/?hl=es"), tokenAdmin);

        // Sin token: es el caso de uso, se mira antes de registrarse.
        ResponseEntity<List> anonimo = rest.getForEntity(url("/api/peluqueros/publicos"), List.class);
        assertEquals(HttpStatus.OK, anonimo.getStatusCode());
        List<Map<String, Object>> equipo = anonimo.getBody();

        Map<String, Object> ana = porNombre(equipo, "Ana CV");
        assertEquals("Llevo la barberia desde 2015", ana.get("presentacion"));
        assertEquals(List.of("Degradados", "Barba"), ana.get("especialidades"),
                "Las especialidades tienen que salir troceadas, no como una cadena con comas");
        assertEquals(9, ana.get("aniosExperiencia"));
        assertEquals("ana.corta", ana.get("instagram"),
                "Del Instagram se guarda el usuario, no la URL que pego quien lo relleno");

        // Lo que importa: por esta puerta abierta no puede salir nada mas.
        String json = equipo.toString();
        assertFalse(json.contains("@test.com"), "Ningun email puede salir en el listado publico");
        assertFalse(json.contains("600000181"), "Ningun telefono puede salir en el listado publico");
        assertFalse(ana.containsKey("usuarioId"), "La cuenta vinculada no puede salir en el listado publico");
        assertFalse(ana.containsKey("usuarioEmail"), "La cuenta vinculada no puede salir en el listado publico");
        assertFalse(ana.containsKey("comisionPorcentaje"), "La comision no puede salir en el listado publico");
        assertFalse(ana.containsKey("activo"), "Contar quien se ha ido no es asunto del cliente");

        // Solo los activos, y en el orden que puso el ADMIN (Luis con orden 1, Ana con 2).
        assertNull(porNombreOnulo(equipo, "Marta CV"), "Una ficha desactivada no se presenta al cliente");
        List<String> nombres = equipo.stream().map(p -> (String) p.get("nombre")).filter(n -> n.endsWith(" CV")).toList();
        assertEquals(List.of("Luis CV", "Ana CV"), nombres);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unaFichaSinCvSePresentaSoloConElNombre() {
        // Es lo que hay en produccion el dia que esto se despliegue: nadie ha rellenado nada.
        List<Map<String, Object>> equipo = rest.getForEntity(url("/api/peluqueros/publicos"), List.class).getBody();
        Map<String, Object> luis = porNombre(equipo, "Luis CV");

        assertEquals("Luis CV", luis.get("nombre"));
        assertNull(luis.get("presentacion"));
        assertNull(luis.get("fotoUrl"), "Sin foto no se puede inventar una URL: la pantalla pintaria un roto");
        assertEquals(List.of(), luis.get("especialidades"),
                "Sin especialidades debe llegar una lista vacia y no null, que romperia el *ngFor");
    }

    @Test
    @SuppressWarnings("unchecked")
    void suPropioCvLoAbreElRolYloEstrechaElPermiso() {
        // Un cliente no tiene ficha: aqui no entra ni a leer.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/peluqueros/mio"), HttpMethod.GET,
                        new HttpEntity<>(cabecera(tokenCliente)), String.class).getStatusCode());

        // Verlo si, sin permiso: mirar su propia ficha no es editarla.
        ResponseEntity<Map> suyo = rest.exchange(url("/api/peluqueros/mio"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenAna)), Map.class);
        assertEquals(HttpStatus.OK, suyo.getStatusCode());
        assertEquals(fichaAna, suyo.getBody().get("idPeluquero"));
        assertEquals("Ana CV", suyo.getBody().get("nombre"));

        // Escribirlo, no: PERFIL_CV_EDITAR nace apagado.
        assertEquals(HttpStatus.FORBIDDEN, ponerMiCv(tokenAna, Map.of("presentacion", "Yo misma")).getStatusCode(),
                "Con PERFIL_CV_EDITAR apagado un peluquero no rellena su CV");

        permiso(true);
        ResponseEntity<Map> escrito = ponerMiCv(tokenAna, Map.of(
                "presentacion", "  Yo misma  ",
                "especialidades", List.of("Degradados", "degradados", " Barba "),
                "aniosExperiencia", 9,
                "instagram", "@ana.corta"));
        assertEquals(HttpStatus.OK, escrito.getStatusCode());
        assertEquals("Yo misma", escrito.getBody().get("presentacion"));
        assertEquals(List.of("Degradados", "Barba"), escrito.getBody().get("especialidades"),
                "La misma etiqueta dos veces con otras mayusculas es la misma etiqueta");
        assertEquals("ana.corta", escrito.getBody().get("instagram"));

        // Y sigue sin poder tocar el de un companero: /mio se resuelve desde la cuenta, y el
        // de otro es de ADMIN por ruta, sin permiso que lo abra.
        assertEquals(HttpStatus.FORBIDDEN, cv(fichaLuis, Map.of("presentacion", "Mio ahora"), tokenAna).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, cv(fichaAna, Map.of("presentacion", "Ni el mio"), tokenAna).getStatusCode(),
                "PUT /{id}/cv es de ADMIN aunque el id sea el de su propia ficha: para eso esta /mio");
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaciarUnCampoEsMandarloEnBlanco() {
        cv(fichaAna, Map.of("presentacion", "Algo que ya no vale", "instagram", "ana.corta"), tokenAdmin);

        // El bloque se reemplaza entero, asi que no mandar un campo lo borra. Es la unica
        // forma de quitar una presentacion que ya no gusta.
        ResponseEntity<Map> vaciado = cv(fichaAna, Map.of("aniosExperiencia", 9), tokenAdmin);
        assertEquals(HttpStatus.OK, vaciado.getStatusCode());
        assertNull(vaciado.getBody().get("presentacion"));
        assertNull(vaciado.getBody().get("instagram"));
        assertEquals(9, vaciado.getBody().get("aniosExperiencia"));
    }

    @Test
    void lasEspecialidadesYelInstagramNoSeGuardanComoVenga() {
        // Una coma dentro de una etiqueta partiria la lista al releerla.
        assertEquals(HttpStatus.BAD_REQUEST,
                cv(fichaAna, Map.of("especialidades", List.of("Color, mechas y balayage")), tokenAdmin)
                        .getStatusCode());
        // Doce etiquetas de 40 caracteres son validas una a una y juntas no caben en la
        // columna: sin la guarda esto seria un 500 de Postgres.
        assertEquals(HttpStatus.BAD_REQUEST,
                cv(fichaAna, Map.of("especialidades", List.of(
                        "a".repeat(40), "b".repeat(40), "c".repeat(40),
                        "d".repeat(40), "e".repeat(40), "f".repeat(40), "g".repeat(40))), tokenAdmin)
                        .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                cv(fichaAna, Map.of("instagram", "https://facebook.com/otracosa"), tokenAdmin).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                cv(fichaAna, Map.of("aniosExperiencia", 200), tokenAdmin).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                cv(fichaAna, Map.of("presentacion", "x".repeat(2001)), tokenAdmin).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void laFotoLaPoneElDuenoDeLaFichaOelAdmin() {
        // Un cliente no llega ni al servicio: lo para la ruta.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/peluqueros/" + fichaAna + "/foto"), HttpMethod.POST,
                        multipart(tokenCliente, true), String.class).getStatusCode());

        // Sin el permiso, un peluquero llega al servicio y ahi se para.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/peluqueros/" + fichaAna + "/foto"), HttpMethod.POST,
                        multipart(tokenAna, true), String.class).getStatusCode());

        permiso(true);
        ResponseEntity<Map> subida = rest.exchange(url("/api/peluqueros/" + fichaAna + "/foto"),
                HttpMethod.POST, multipart(tokenAna, true), Map.class);
        assertEquals(HttpStatus.OK, subida.getStatusCode());
        assertNotNull(subida.getBody().get("fotoUrl"));

        // Y la de un companero sigue cerrada, permiso encendido incluido.
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/peluqueros/" + fichaLuis + "/foto"), HttpMethod.POST,
                        multipart(tokenAna, true), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                rest.exchange(url("/api/peluqueros/" + fichaLuis + "/foto"), HttpMethod.DELETE,
                        new HttpEntity<>(cabecera(tokenAna)), String.class).getStatusCode());

        // Un ejecutable renombrado a .jpg: se mira el contenido, no la extension.
        MultiValueMap<String, Object> falso = new LinkedMultiValueMap<>();
        falso.add("foto", recurso(new byte[] { 0x4D, 0x5A, 0x00, 0x00 }, "yo.jpg"));
        HttpHeaders headers = cabecera(tokenAna);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertEquals(HttpStatus.BAD_REQUEST,
                rest.exchange(url("/api/peluqueros/" + fichaAna + "/foto"), HttpMethod.POST,
                        new HttpEntity<>(falso, headers), String.class).getStatusCode());

        // La foto ya se ve desde fuera, sin cuenta.
        List<Map<String, Object>> equipo = rest.getForEntity(url("/api/peluqueros/publicos"), List.class).getBody();
        assertNotNull(porNombre(equipo, "Ana CV").get("fotoUrl"));

        // Y el ADMIN la quita, con todo apagado.
        permiso(false);
        assertEquals(HttpStatus.OK,
                rest.exchange(url("/api/peluqueros/" + fichaAna + "/foto"), HttpMethod.DELETE,
                        new HttpEntity<>(cabecera(tokenAdmin)), Map.class).getStatusCode());
        assertNull(porNombre(rest.getForEntity(url("/api/peluqueros/publicos"), List.class).getBody(), "Ana CV")
                .get("fotoUrl"));
        // Una ficha desactivada no tiene foto que ensenar, porque no sale en el equipo.
        assertEquals(HttpStatus.OK,
                rest.exchange(url("/api/peluqueros/" + fichaInactiva + "/foto"), HttpMethod.DELETE,
                        new HttpEntity<>(cabecera(tokenAdmin)), Map.class).getStatusCode(),
                "Borrar una foto que no hay es idempotente");
    }

    @Test
    @SuppressWarnings("unchecked")
    void elPanelLeeElCvYelOrdenEnLaFichaDeGestion() {
        cv(fichaAna, Map.of("presentacion", "Para el panel", "especialidades", List.of("Barba")), tokenAdmin);

        List<Map<String, Object>> gestion = rest.exchange(url("/api/peluqueros/gestion"), HttpMethod.GET,
                new HttpEntity<>(cabecera(tokenAdmin)), List.class).getBody();
        Map<String, Object> ana = porNombre(gestion, "Ana CV");

        // Va anidado en la ficha para que la pestana del panel no tenga que pedirlo aparte.
        Map<String, Object> cv = (Map<String, Object>) ana.get("cv");
        assertEquals("Para el panel", cv.get("presentacion"));
        assertEquals(List.of("Barba"), cv.get("especialidades"));
        assertEquals(2, ana.get("orden"));

        // El orden es de la plantilla, no del CV: lo mueve el ADMIN por la ficha.
        ResponseEntity<Map> movido = rest.exchange(url("/api/peluqueros/" + fichaAna), HttpMethod.PUT,
                new HttpEntity<>(Map.of("orden", 0), cabecera(tokenAdmin)), Map.class);
        assertEquals(HttpStatus.OK, movido.getStatusCode());
        assertEquals(0, movido.getBody().get("orden"));

        // Con Ana y Marta a 0 el desempate lo hace el nombre, no el azar de la consulta.
        List<String> nombres = ((List<Map<String, Object>>) rest.getForEntity(
                url("/api/peluqueros/publicos"), List.class).getBody()).stream()
                .map(p -> (String) p.get("nombre")).filter(n -> n.endsWith(" CV")).toList();
        assertEquals(List.of("Ana CV", "Luis CV"), nombres);
    }

    // ---- Helpers ----

    private void permiso(boolean habilitado) {
        ResponseEntity<List> resp = rest.exchange(url("/api/permisos"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("cambios",
                        List.of(Map.of("rol", "PELUQUERO", "clave", "PERFIL_CV_EDITAR", "habilitado", habilitado))),
                        cabecera(tokenAdmin)),
                List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    private ResponseEntity<Map> cv(Integer idPeluquero, Map<String, Object> cuerpo, String token) {
        return rest.exchange(url("/api/peluqueros/" + idPeluquero + "/cv"), HttpMethod.PUT,
                new HttpEntity<>(cuerpo, cabecera(token)), Map.class);
    }

    private ResponseEntity<Map> ponerMiCv(String token, Map<String, Object> cuerpo) {
        return rest.exchange(url("/api/peluqueros/mio"), HttpMethod.PUT,
                new HttpEntity<>(cuerpo, cabecera(token)), Map.class);
    }

    private Integer crearFicha(String nombre, String emailCuenta, boolean activo, int orden) {
        Integer idUsuario = emailCuenta == null ? null : idUsuario(emailCuenta);
        return jdbcTemplate.queryForObject(
                "INSERT INTO peluqueros (nombre, activo, usuario_id, comision_porcentaje, orden) "
                        + "VALUES (?, ?, ?, 20.00, ?) RETURNING id_peluquero",
                Integer.class, nombre, activo, idUsuario, orden);
    }

    private Integer idUsuario(String email) {
        return jdbcTemplate.queryForObject("SELECT id_usuario FROM usuarios WHERE email = ?", Integer.class, email);
    }

    private Map<String, Object> porNombre(List<Map<String, Object>> fichas, String nombre) {
        Map<String, Object> ficha = porNombreOnulo(fichas, nombre);
        assertNotNull(ficha, "No esta en el listado la ficha '" + nombre + "'");
        return ficha;
    }

    private Map<String, Object> porNombreOnulo(List<Map<String, Object>> fichas, String nombre) {
        return fichas.stream()
                .filter(f -> nombre.equals(f.get("nombre")))
                .findFirst()
                .orElse(null);
    }

    private HttpHeaders cabecera(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** Multipart con JPEG minimo: lo que valida el servidor son los primeros bytes. */
    private HttpEntity<MultiValueMap<String, Object>> multipart(String token, boolean conFoto) {
        byte[] datos = new byte[64];
        datos[0] = (byte) 0xFF;
        datos[1] = (byte) 0xD8;
        datos[2] = (byte) 0xFF;

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        if (conFoto) {
            cuerpo.add("foto", recurso(datos, "yo.jpg"));
        }
        HttpHeaders headers = cabecera(token);
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
                Map.of("nombre", "Admin CV", "email", email, "password", password, "telefono", "600000180"),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'ADMIN' WHERE email = ?", email);
        return login(email, password);
    }

    private String crearPeluquero(String email, String telefono) {
        String password = "Peluquero123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Trabajador CV", "email", email, "password", password, "telefono", telefono),
                Map.class);
        jdbcTemplate.update("UPDATE usuarios SET rol = 'PELUQUERO' WHERE email = ?", email);
        return login(email, password);
    }

    private String crearCliente(String email) {
        String password = "Cliente123!";
        rest.postForEntity(url("/api/auth/registro"),
                Map.of("nombre", "Cliente CV", "email", email, "password", password, "telefono", "600000183"),
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
