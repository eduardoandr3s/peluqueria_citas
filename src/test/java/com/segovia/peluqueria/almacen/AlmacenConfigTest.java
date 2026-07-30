package com.segovia.peluqueria.almacen;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La eleccion de almacen es la unica logica de {@link AlmacenConfig}, y la parte
 * que importa es que el apano de desarrollo (disco local) no se cuele en
 * produccion sin que nadie se de cuenta.
 */
class AlmacenConfigTest {

    private static AlmacenProperties conCredenciales() {
        AlmacenProperties propiedades = new AlmacenProperties();
        propiedades.setSupabaseUrl("https://proyecto.supabase.co");
        propiedades.setServiceKey("clave-de-servicio");
        return propiedades;
    }

    private static MockEnvironment perfil(String... perfiles) {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setProperty("spring.profiles.active", String.join(",", perfiles));
        return entorno;
    }

    @Test
    void conCredencialesUsaSupabase() {
        var config = new AlmacenConfig(conCredenciales(), perfil("prod"));

        assertInstanceOf(SupabaseStorageAlmacen.class, config.almacenFicheros());
    }

    @Test
    void sinCredencialesEnDesarrolloCaeAlDiscoLocal() {
        var config = new AlmacenConfig(new AlmacenProperties(), perfil("dev"));

        assertInstanceOf(AlmacenLocal.class, config.almacenFicheros());
    }

    @Test
    void sinCredencialesEnProduccionNoArranca() {
        var config = new AlmacenConfig(new AlmacenProperties(), perfil("prod"));

        var error = assertThrows(IllegalStateException.class, config::almacenFicheros);
        // El mensaje es lo unico que vera quien despliegue: tiene que nombrar las
        // variables que faltan, no solo decir que algo va mal.
        assertTrue(error.getMessage().contains("SUPABASE_URL"));
        assertTrue(error.getMessage().contains("SUPABASE_SERVICE_KEY"));
    }

    @Test
    void bastaConQueProdEste_entreLosPerfilesActivos() {
        var config = new AlmacenConfig(new AlmacenProperties(), perfil("prod", "metrics"));

        assertThrows(IllegalStateException.class, config::almacenFicheros);
    }

    @Test
    void soloLaServiceKeyNoBasta() {
        // Media configuracion es peor que ninguna: apunta a que alguien puso una
        // variable y se olvido de la otra, asi que en prod tambien debe fallar.
        AlmacenProperties propiedades = new AlmacenProperties();
        propiedades.setServiceKey("clave-de-servicio");

        assertThrows(IllegalStateException.class,
                () -> new AlmacenConfig(propiedades, perfil("prod")).almacenFicheros());
    }
}
