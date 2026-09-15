package com.segovia.peluqueria.modulo;

import com.segovia.peluqueria.modulo.dto.ActualizarModulosDTO;
import com.segovia.peluqueria.modulo.dto.CambioModuloDTO;
import com.segovia.peluqueria.modulo.dto.ModuloDTO;
import com.segovia.peluqueria.modulo.dto.PerfilArranqueDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModuloServiceTest {

    private ModuloRepository moduloRepository;
    private ModuloService moduloService;

    @BeforeEach
    void setUp() {
        moduloRepository = mock(ModuloRepository.class);
        when(moduloRepository.findAll()).thenReturn(List.of());
        when(moduloRepository.findById(anyString())).thenReturn(Optional.empty());
        moduloService = new ModuloService(moduloRepository);
    }

    /** Guarda un estado como si estuviera en la tabla. */
    private void guardado(Object... claveYValor) {
        ModuloEstado[] filas = new ModuloEstado[claveYValor.length / 2];
        for (int i = 0; i < filas.length; i++) {
            filas[i] = new ModuloEstado((Modulo) claveYValor[i * 2], (boolean) claveYValor[i * 2 + 1]);
        }
        when(moduloRepository.findAll()).thenReturn(List.of(filas));
    }

    @Test
    void sinFilaGuardadaTodoNaceEncendido() {
        // Es la diferencia con los permisos, que nacen apagados, y por el mismo motivo: el
        // valor por defecto tiene que ser el comportamiento de antes. Desplegar el motor de
        // modulos no le puede quitar nada a nadie.
        for (Modulo modulo : Modulo.values()) {
            assertTrue(moduloService.estaActivo(modulo), modulo + " deberia nacer encendido.");
        }
    }

    @Test
    void unaFilaApagadaQuitaElModulo() {
        guardado(Modulo.COMISIONES, false);

        assertFalse(moduloService.estaActivo(Modulo.COMISIONES));
        assertTrue(moduloService.estaActivo(Modulo.PAGOS));
    }

    @Test
    void apagarElPadreApagaLosHijos() {
        guardado(Modulo.PAGOS, false);

        assertFalse(moduloService.estaActivo(Modulo.PAGO_TARJETA));
        assertFalse(moduloService.estaActivo(Modulo.PAGO_EFECTIVO));
        assertFalse(moduloService.estaActivo(Modulo.PAGO_TRANSFERENCIA));
    }

    @Test
    void apagarTodosLosHijosEsApagarElPadre() {
        // Si no fuera asi quedaria un modulo "encendido" sin ninguna forma de cobrar, y la
        // pantalla de configuracion mentiria.
        guardado(Modulo.PAGO_TARJETA, false,
                Modulo.PAGO_EFECTIVO, false,
                Modulo.PAGO_TRANSFERENCIA, false);

        assertFalse(moduloService.estaActivo(Modulo.PAGOS));
    }

    @Test
    void conUnSoloMedioDePagoElPadreSigueEncendido() {
        // El caso realista: la peluqueria que cobra en el local y no quiere pasarela.
        guardado(Modulo.PAGO_TARJETA, false, Modulo.PAGO_TRANSFERENCIA, false);

        assertTrue(moduloService.estaActivo(Modulo.PAGOS));
        assertTrue(moduloService.estaActivo(Modulo.PAGO_EFECTIVO));
        assertFalse(moduloService.estaActivo(Modulo.PAGO_TARJETA));
    }

    @Test
    void unHijoEncendidoBajoUnPadreApagadoNoSirveDeNada() {
        // Se puede dejar preparado, pero no aplica. El DTO lo cuenta con 'efectivo'.
        guardado(Modulo.PAGOS, false, Modulo.PAGO_EFECTIVO, true);

        assertFalse(moduloService.estaActivo(Modulo.PAGO_EFECTIVO));
        ModuloDTO fila = filaDe(Modulo.PAGO_EFECTIVO);
        assertTrue(fila.isActivo(), "La casilla sigue marcada: es lo que eligio el administrador.");
        assertFalse(fila.isEfectivo(), "Pero no aplica, y la pantalla tiene que poder decirlo.");
    }

    @Test
    void exigirNoHaceNadaSiElModuloEstaEncendido() {
        moduloService.exigir(Modulo.PAGOS);
    }

    @Test
    void exigirUnModuloApagadoCorta() {
        guardado(Modulo.COMISIONES, false);

        ModuloDesactivadoException ex = assertThrows(ModuloDesactivadoException.class,
                () -> moduloService.exigir(Modulo.COMISIONES));
        assertEquals(Modulo.COMISIONES, ex.getModulo());
        assertTrue(ex.getMessage().contains("Comisiones"));
    }

    @Test
    void siAUnHijoLoTumbaSuPadreElErrorNombraAlPadre() {
        // Decir "no hay pago en efectivo" cuando lo que no hay es cobrar en absoluto manda a
        // buscar el check equivocado.
        guardado(Modulo.PAGOS, false);

        ModuloDesactivadoException ex = assertThrows(ModuloDesactivadoException.class,
                () -> moduloService.exigir(Modulo.PAGO_EFECTIVO));
        assertEquals(Modulo.PAGOS, ex.getModulo());
    }

    @Test
    void siElHijoEsElApagadoElErrorLoNombraAEl() {
        guardado(Modulo.PAGO_TARJETA, false);

        ModuloDesactivadoException ex = assertThrows(ModuloDesactivadoException.class,
                () -> moduloService.exigir(Modulo.PAGO_TARJETA));
        assertEquals(Modulo.PAGO_TARJETA, ex.getModulo());
    }

    @Test
    void lasClavesActivasSonLasEfectivas() {
        guardado(Modulo.PAGOS, false);

        assertTrue(moduloService.clavesActivas().contains("COMISIONES"));
        assertFalse(moduloService.clavesActivas().contains("PAGOS"));
        assertFalse(moduloService.clavesActivas().contains("PAGO_EFECTIVO"),
                "Un hijo de un padre apagado no puede salir en la lista publica.");
    }

    @Test
    void unaFilaDeUnModuloQueYaNoExisteSeIgnora() {
        // Un modulo puede retirarse del codigo y su fila sobrevivirle hasta que alguien la
        // limpie. Eso no puede tumbar el arranque.
        ModuloEstado huerfana = new ModuloEstado(Modulo.PAGOS, false);
        when(moduloRepository.findAll()).thenReturn(List.of(huerfana));

        assertFalse(moduloService.estaActivo(Modulo.PAGOS));
        assertTrue(moduloService.estaActivo(Modulo.COMISIONES));
    }

    @Test
    void actualizarGuardaElCambioYTiraLaCache() {
        moduloService.estaActivo(Modulo.COMISIONES);
        guardado(Modulo.COMISIONES, false);

        List<ModuloDTO> despues = moduloService.actualizar(cambio("COMISIONES", false));

        verify(moduloRepository).save(any(ModuloEstado.class));
        // Sin tirar la cache, la pantalla se quedaria pintando el estado viejo hasta el
        // siguiente reinicio.
        assertFalse(despues.stream().filter(m -> m.getClave().equals("COMISIONES")).findFirst().orElseThrow().isActivo());
        verify(moduloRepository, times(2)).findAll();
    }

    @Test
    void actualizarConUnaClaveQueNoExisteSeRechaza() {
        // Si se aceptara, la tabla guardaria el estado de un modulo que nadie consulta.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> moduloService.actualizar(cambio("MODULO_INVENTADO", false)));
        assertTrue(ex.getMessage().contains("MODULO_INVENTADO"));
    }

    @Test
    void elCatalogoLlevaLaJerarquiaParaQueLaPantallaLaPuedaAnidar() {
        ModuloDTO tarjeta = filaDe(Modulo.PAGO_TARJETA);
        assertEquals("PAGOS", tarjeta.getPadre());
        assertEquals(null, filaDe(Modulo.PAGOS).getPadre());
        assertEquals(Modulo.values().length, moduloService.listar().size());
    }


    // ---------- perfiles de arranque ----------

    @Test
    void aplicarUnPerfilEscribeTodosLosModulos() {
        // Todos y no solo los que cambian: asi el resultado no depende de lo que hubiera
        // antes y aplicar el mismo perfil dos veces deja lo mismo.
        moduloService.aplicarPerfil(PerfilArranque.SOLO_AGENDA);

        verify(moduloRepository, times(Modulo.values().length)).save(any(ModuloEstado.class));
    }

    @Test
    void soloAgendaDejaLosRecordatoriosYApagaElResto() {
        capturarLoGuardado();

        moduloService.aplicarPerfil(PerfilArranque.SOLO_AGENDA);

        assertTrue(moduloService.estaActivo(Modulo.RECORDATORIOS_EMAIL));
        assertFalse(moduloService.estaActivo(Modulo.PAGOS));
        assertFalse(moduloService.estaActivo(Modulo.GALERIA));
        assertFalse(moduloService.estaActivo(Modulo.PRODUCCION));
        assertFalse(moduloService.estaActivo(Modulo.COMISIONES));
    }

    @Test
    void agendaYCajaNoEnciendeLaPasarelaDeTarjeta() {
        // No es un olvido: Stripe necesita una cuenta y unas claves propias de cada negocio,
        // asi que el primer dia de un cliente nuevo no hay con que cobrar online. Encenderlo
        // seria ofrecerle al cliente final una pasarela que devuelve error.
        capturarLoGuardado();

        moduloService.aplicarPerfil(PerfilArranque.AGENDA_Y_CAJA);

        assertTrue(moduloService.estaActivo(Modulo.PAGOS));
        assertTrue(moduloService.estaActivo(Modulo.PAGO_EFECTIVO));
        assertTrue(moduloService.estaActivo(Modulo.PAGO_TRANSFERENCIA));
        assertFalse(moduloService.estaActivo(Modulo.PAGO_TARJETA));
    }

    @Test
    void elPerfilTodoDejaElProductoEntero() {
        capturarLoGuardado();

        moduloService.aplicarPerfil(PerfilArranque.TODO);

        for (Modulo modulo : Modulo.values()) {
            assertTrue(moduloService.estaActivo(modulo), modulo + " deberia quedar encendido.");
        }
    }

    @Test
    void aplicarUnPerfilTiraLaCache() {
        // Sin esto, la pantalla seguiria pintando el estado de antes hasta el siguiente
        // reinicio, que es justo el bug que se arreglo cuando se apago COMISIONES a mano.
        capturarLoGuardado();
        moduloService.estaActivo(Modulo.GALERIA);

        moduloService.aplicarPerfil(PerfilArranque.SOLO_AGENDA);

        assertFalse(moduloService.estaActivo(Modulo.GALERIA));
    }

    @Test
    void aplicarDosVecesElMismoPerfilDejaLoMismo() {
        capturarLoGuardado();

        moduloService.aplicarPerfil(PerfilArranque.AGENDA_Y_CAJA);
        List<ModuloDTO> primera = moduloService.listar();
        moduloService.aplicarPerfil(PerfilArranque.AGENDA_Y_CAJA);

        assertEquals(primera, moduloService.listar());
    }

    @Test
    void unPerfilQueNoExisteSeRechaza() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> moduloService.resolverPerfil("PERFIL_INVENTADO"));
        assertTrue(ex.getMessage().contains("PERFIL_INVENTADO"));
    }

    @Test
    void cadaPerfilDiceQueEnciendeYQueApaga() {
        // Las dos listas van resueltas desde aqui para que el panel pueda avisar de lo que
        // se pierde sin recalcularlo por su cuenta y arriesgarse a discrepar.
        List<PerfilArranqueDTO> perfiles = moduloService.perfiles();

        assertEquals(PerfilArranque.values().length, perfiles.size());
        for (PerfilArranqueDTO perfil : perfiles) {
            assertEquals(Modulo.values().length, perfil.getEnciende().size() + perfil.getApaga().size());
        }
        PerfilArranqueDTO soloAgenda = perfiles.stream()
                .filter(p -> p.getClave().equals("SOLO_AGENDA"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("RECORDATORIOS_EMAIL"), soloAgenda.getEnciende());
        assertTrue(soloAgenda.getApaga().contains("PAGOS"));
    }

    /**
     * Hace que el repositorio mockeado se comporte como una tabla: lo que se guarda es lo
     * que se lee despues. Sin esto, {@code findAll} seguiria devolviendo la lista vacia y
     * todos los modulos pareceran encendidos por el valor por defecto.
     */
    private void capturarLoGuardado() {
        Map<String, ModuloEstado> tabla = new LinkedHashMap<>();
        when(moduloRepository.save(any(ModuloEstado.class))).thenAnswer(inv -> {
            ModuloEstado fila = inv.getArgument(0);
            tabla.put(fila.getClave(), fila);
            return fila;
        });
        when(moduloRepository.findAll()).thenAnswer(inv -> List.copyOf(tabla.values()));
        when(moduloRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(tabla.get(inv.getArgument(0))));
    }

    private ModuloDTO filaDe(Modulo modulo) {
        return moduloService.listar().stream()
                .filter(m -> m.getClave().equals(modulo.name()))
                .findFirst()
                .orElseThrow();
    }

    private ActualizarModulosDTO cambio(String clave, boolean activo) {
        CambioModuloDTO uno = new CambioModuloDTO();
        uno.setClave(clave);
        uno.setActivo(activo);
        ActualizarModulosDTO request = new ActualizarModulosDTO();
        request.setCambios(List.of(uno));
        return request;
    }
}
