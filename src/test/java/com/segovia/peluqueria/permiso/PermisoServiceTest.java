package com.segovia.peluqueria.permiso;

import com.segovia.peluqueria.permiso.dto.ActualizarPermisosDTO;
import com.segovia.peluqueria.permiso.dto.CambioPermisoDTO;
import com.segovia.peluqueria.permiso.dto.MisPermisosDTO;
import com.segovia.peluqueria.permiso.dto.PermisoDTO;
import com.segovia.peluqueria.usuario.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermisoServiceTest {

    private PermisoRolRepository permisoRolRepository;
    private PermisoService permisoService;

    @BeforeEach
    void setUp() {
        permisoRolRepository = mock(PermisoRolRepository.class);
        when(permisoRolRepository.findAll()).thenReturn(List.of());
        permisoService = new PermisoService(permisoRolRepository);
    }

    @Test
    void unAdminTieneTodosLosPermisosSinMirarLaTabla() {
        for (Permiso permiso : Permiso.values()) {
            assertTrue(permisoService.tienePermiso(Rol.ADMIN, permiso),
                    "Un ADMIN no se configura: tiene " + permiso + " por rol.");
        }
        // Ni siquiera hace falta consultar el estado guardado para responder.
        verify(permisoRolRepository, never()).findAll();
    }

    @Test
    void sinFilaGuardadaValeElValorPorDefectoDelEnum() {
        // Los dos permisos de hoy nacen apagados: desplegarlos no cambia lo que puede
        // hacer nadie hasta que un administrador los encienda.
        assertFalse(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR));
        assertFalse(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.CITA_REPROGRAMAR));
    }

    @Test
    void unaFilaHabilitadaConcedeElPermiso() {
        when(permisoRolRepository.findAll()).thenReturn(
                List.of(new PermisoRol(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR, true)));

        assertTrue(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR));
        // Y no arrastra a los demas permisos.
        assertFalse(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.CITA_REPROGRAMAR));
    }

    @Test
    void unRolAlQueNoAplicaNoLoTieneNiConLaFilaEncendida() {
        // Es la regla de oro: el flag ESTRECHA, nunca abre. Una fila colada a mano para
        // USER no puede concederle nada, porque el permiso no se le configura.
        when(permisoRolRepository.findAll()).thenReturn(
                List.of(new PermisoRol(Rol.USER, Permiso.PAGO_MANUAL_REGISTRAR, true)));

        assertFalse(permisoService.tienePermiso(Rol.USER, Permiso.PAGO_MANUAL_REGISTRAR));
    }

    @Test
    void unaFilaDeUnPermisoRetiradoDelCodigoSeIgnora() {
        PermisoRol fantasma = mock(PermisoRol.class);
        when(fantasma.getRol()).thenReturn(Rol.PELUQUERO);
        when(fantasma.getClave()).thenReturn("PERMISO_QUE_YA_NO_EXISTE");
        when(fantasma.isHabilitado()).thenReturn(true);
        when(permisoRolRepository.findAll()).thenReturn(List.of(fantasma));

        // No revienta al cargar: un permiso puede retirarse del enum y su fila
        // sobrevivirle hasta que alguien la limpie.
        assertFalse(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR));
    }

    @Test
    void laTablaSeLeeUnaSolaVezGraciasALaCache() {
        permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR);
        permisoService.tienePermiso(Rol.PELUQUERO, Permiso.CITA_REPROGRAMAR);
        permisoService.listarMatriz();

        verify(permisoRolRepository, times(1)).findAll();
    }

    @Test
    void escribirTiraLaCacheYLaSiguienteLecturaVeElCambio() {
        assertFalse(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR));

        when(permisoRolRepository.findByRolAndClave(any(), anyString())).thenReturn(Optional.empty());
        when(permisoRolRepository.findAll()).thenReturn(
                List.of(new PermisoRol(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR, true)));
        permisoService.actualizar(cambio(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR, true));

        assertTrue(permisoService.tienePermiso(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR));
    }

    @Test
    void actualizarUnaFilaQueYaExisteLaReutilizaEnVezDeDuplicarla() {
        PermisoRol existente = new PermisoRol(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR, false);
        when(permisoRolRepository.findByRolAndClave(Rol.PELUQUERO, "PAGO_MANUAL_REGISTRAR"))
                .thenReturn(Optional.of(existente));

        permisoService.actualizar(cambio(Rol.PELUQUERO, Permiso.PAGO_MANUAL_REGISTRAR, true));

        assertTrue(existente.isHabilitado());
        verify(permisoRolRepository).save(existente);
    }

    @Test
    void actualizarConUnaClaveQueNoExisteLanza() {
        ActualizarPermisosDTO request = new ActualizarPermisosDTO();
        CambioPermisoDTO cambio = new CambioPermisoDTO();
        cambio.setRol(Rol.PELUQUERO);
        cambio.setClave("INVENTADO");
        cambio.setHabilitado(true);
        request.setCambios(List.of(cambio));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> permisoService.actualizar(request));
        assertTrue(ex.getMessage().contains("INVENTADO"));
        verify(permisoRolRepository, never()).save(any());
    }

    @Test
    void actualizarUnPermisoParaUnRolQueNoSeConfiguraLanza() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> permisoService.actualizar(cambio(Rol.ADMIN, Permiso.PAGO_MANUAL_REGISTRAR, false)));

        assertTrue(ex.getMessage().contains("ADMIN"));
        // Lo importante: no queda escrita una fila que la aplicacion nunca leeria.
        verify(permisoRolRepository, never()).save(any());
    }

    @Test
    void laMatrizSoloTraeLosRolesQueSeConfiguran() {
        List<PermisoDTO> matriz = permisoService.listarMatriz();

        assertEquals(Permiso.values().length, matriz.size());
        for (PermisoDTO fila : matriz) {
            assertEquals(java.util.Set.of(Rol.PELUQUERO), fila.getRoles().keySet(),
                    "La matriz no debe ofrecer casillas para ADMIN ni para USER.");
            assertFalse(fila.getRoles().get(Rol.PELUQUERO));
        }
    }

    @Test
    void misPermisosDeUnAdminLosTraeTodosYLosDeUnClienteNinguno() {
        MisPermisosDTO admin = permisoService.misPermisos(Rol.ADMIN);
        assertEquals(Permiso.values().length, admin.getPermisos().size());
        assertEquals(Rol.ADMIN, admin.getRol());

        assertTrue(permisoService.misPermisos(Rol.USER).getPermisos().isEmpty());
        assertTrue(permisoService.misPermisos(Rol.PELUQUERO).getPermisos().isEmpty());
    }

    private ActualizarPermisosDTO cambio(Rol rol, Permiso permiso, boolean habilitado) {
        CambioPermisoDTO cambio = new CambioPermisoDTO();
        cambio.setRol(rol);
        cambio.setClave(permiso.name());
        cambio.setHabilitado(habilitado);
        ActualizarPermisosDTO request = new ActualizarPermisosDTO();
        request.setCambios(List.of(cambio));
        return request;
    }
}
