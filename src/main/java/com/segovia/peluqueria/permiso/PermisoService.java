package com.segovia.peluqueria.permiso;

import com.segovia.peluqueria.permiso.dto.ActualizarPermisosDTO;
import com.segovia.peluqueria.permiso.dto.CambioPermisoDTO;
import com.segovia.peluqueria.permiso.dto.MisPermisosDTO;
import com.segovia.peluqueria.permiso.dto.PermisoDTO;
import com.segovia.peluqueria.usuario.Rol;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Estado de los permisos configurables.
 *
 * <p>Se consulta desde los servicios y NO desde SecurityConfig: las reglas de las rutas
 * son estaticas y estos flags no. La consecuencia querida es que el orden siempre sea el
 * mismo: primero pasa la regla de rol, despues el permiso. Un permiso encendido no
 * concede nada que el rol no permitiera ya.
 *
 * <p>Lleva cache en memoria porque {@code tienePermiso} se llama en el camino de cada
 * peticion que lo use y la tabla cambia una vez cada varios meses. La cache se rellena
 * perezosamente y se tira entera al escribir, que con dos roles y un punado de claves
 * sale mas barato y mas dificil de equivocar que invalidar una entrada.
 *
 * <p><b>Con varias instancias del backend habria que invalidarla tambien en las otras.</b>
 * Hoy corre una sola en Render, asi que no se monta nada para eso; si algun dia se
 * escala, este es el sitio.
 */
@Service
public class PermisoService {

    private final PermisoRolRepository permisoRolRepository;

    /** null = todavia no cargada. Se reemplaza entera, nunca se muta. */
    private final AtomicReference<Map<Rol, Set<Permiso>>> cache = new AtomicReference<>();

    public PermisoService(PermisoRolRepository permisoRolRepository) {
        this.permisoRolRepository = permisoRolRepository;
    }

    /**
     * Unico punto de verdad. Devuelve si ese rol tiene concedido el permiso.
     *
     * <p>Un ADMIN los tiene todos por rol y no se configura. Un rol al que el permiso no
     * le aplica no lo tiene nunca, aunque alguien colara una fila a mano en la tabla.
     */
    @Transactional(readOnly = true)
    public boolean tienePermiso(Rol rol, Permiso permiso) {
        if (rol == Rol.ADMIN) {
            return true;
        }
        if (!permiso.aplicaA(rol)) {
            return false;
        }
        return concedidos().getOrDefault(rol, Set.of()).contains(permiso);
    }

    /** Matriz completa para el panel: un permiso por fila, sus roles configurables por columna. */
    @Transactional(readOnly = true)
    public List<PermisoDTO> listarMatriz() {
        Map<Rol, Set<Permiso>> estado = concedidos();
        return Arrays.stream(Permiso.values())
                .map(permiso -> {
                    Map<Rol, Boolean> roles = new LinkedHashMap<>();
                    for (Rol rol : Rol.values()) {
                        if (permiso.aplicaA(rol)) {
                            roles.put(rol, estado.getOrDefault(rol, Set.of()).contains(permiso));
                        }
                    }
                    return new PermisoDTO(permiso, roles);
                })
                .toList();
    }

    /** Lo concedido a la cuenta que pregunta, para que el frontend pinte u oculte. */
    @Transactional(readOnly = true)
    public MisPermisosDTO misPermisos(Rol rol) {
        Set<String> claves = Arrays.stream(Permiso.values())
                .filter(permiso -> tienePermiso(rol, permiso))
                .map(Permiso::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new MisPermisosDTO(rol, claves);
    }

    /**
     * Aplica los cambios de la matriz. Rechaza una clave que no exista en el catalogo y
     * un rol al que ese permiso no se le pueda configurar: si se aceptara, la tabla
     * guardaria un permiso que nunca se concede y la pantalla mentiria.
     */
    @Transactional
    public List<PermisoDTO> actualizar(ActualizarPermisosDTO request) {
        for (CambioPermisoDTO cambio : request.getCambios()) {
            Permiso permiso = resolver(cambio.getClave());
            if (!permiso.aplicaA(cambio.getRol())) {
                throw new IllegalArgumentException(
                        "El permiso " + permiso.name() + " no se configura para el rol " + cambio.getRol()
                                + "; los administradores lo tienen siempre y los clientes nunca.");
            }
            PermisoRol fila = permisoRolRepository.findByRolAndClave(cambio.getRol(), permiso.name())
                    .orElseGet(() -> new PermisoRol(cambio.getRol(), permiso, false));
            fila.setHabilitado(Boolean.TRUE.equals(cambio.getHabilitado()));
            permisoRolRepository.save(fila);
        }
        cache.set(null);
        return listarMatriz();
    }

    private Permiso resolver(String clave) {
        try {
            return Permiso.valueOf(clave);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el permiso " + clave + ".");
        }
    }

    /**
     * Estado efectivo por rol, con la cache delante. Una fila cuya clave ya no exista en
     * el enum se ignora en vez de reventar: un permiso puede retirarse del codigo y su
     * fila sobrevivirle hasta que alguien la limpie.
     */
    private Map<Rol, Set<Permiso>> concedidos() {
        Map<Rol, Set<Permiso>> actual = cache.get();
        if (actual != null) {
            return actual;
        }

        Map<String, Map<Rol, Boolean>> guardado = new LinkedHashMap<>();
        for (PermisoRol fila : permisoRolRepository.findAll()) {
            guardado.computeIfAbsent(fila.getClave(), k -> new EnumMap<>(Rol.class))
                    .put(fila.getRol(), fila.isHabilitado());
        }

        Map<Rol, Set<Permiso>> calculado = new EnumMap<>(Rol.class);
        for (Rol rol : Rol.values()) {
            Set<Permiso> delRol = EnumSet.noneOf(Permiso.class);
            for (Permiso permiso : Permiso.values()) {
                if (!permiso.aplicaA(rol)) {
                    continue;
                }
                // Sin fila vale el defecto del enum: desplegar un permiso nuevo no cambia
                // lo que puede hacer nadie hasta que un administrador lo encienda.
                boolean habilitado = guardado.getOrDefault(permiso.name(), Map.of())
                        .getOrDefault(rol, permiso.isPorDefecto());
                if (habilitado) {
                    delRol.add(permiso);
                }
            }
            calculado.put(rol, delRol);
        }

        cache.set(calculado);
        return calculado;
    }
}
