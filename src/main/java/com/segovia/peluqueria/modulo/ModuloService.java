package com.segovia.peluqueria.modulo;

import com.segovia.peluqueria.modulo.dto.ActualizarModulosDTO;
import com.segovia.peluqueria.modulo.dto.CambioModuloDTO;
import com.segovia.peluqueria.modulo.dto.ModuloDTO;
import com.segovia.peluqueria.modulo.dto.PerfilArranqueDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Estado de los modulos del negocio: que partes del producto estan encendidas.
 *
 * <p>Se consulta desde los servicios, igual que {@code PermisoService} y por el mismo
 * motivo: las reglas de ruta de SecurityConfig son estaticas y esto no. La consecuencia
 * querida es el orden de las tres preguntas: <b>modulo, rol, permiso</b>. Un modulo
 * apagado corta antes de mirar el rol, y por eso tambien deja fuera a un ADMIN.
 *
 * <p>Lleva cache en memoria por lo mismo que la de permisos: se pregunta en el camino de
 * cada peticion que lo use y la tabla cambia una vez cada varios meses. Se rellena
 * perezosamente y se tira entera al escribir.
 *
 * <p><b>Con varias instancias del backend habria que invalidarla tambien en las otras.</b>
 * Hoy corre una sola en Render; si algun dia se escala, este es el sitio.
 */
@Service
public class ModuloService {

    private final ModuloRepository moduloRepository;

    /** null = todavia no cargado. Se reemplaza entero, nunca se muta. */
    private final AtomicReference<Estado> cache = new AtomicReference<>();

    public ModuloService(ModuloRepository moduloRepository) {
        this.moduloRepository = moduloRepository;
    }

    /**
     * Si el modulo aplica de verdad, ya contadas las reglas de padre e hijos.
     *
     * <p>Es lo que hay que preguntar siempre: el valor guardado a secas mentiria en los
     * dos sentidos (un hijo encendido bajo un padre apagado, o un padre encendido sin
     * ningun hijo con el que hacer nada).
     */
    @Transactional(readOnly = true)
    public boolean estaActivo(Modulo modulo) {
        return estado().efectivos().contains(modulo);
    }

    /**
     * Corta la operacion si el modulo esta apagado. Es la primera linea de los servicios
     * que hacen algo que un modulo puede quitar, antes de mirar rol o permiso.
     */
    @Transactional(readOnly = true)
    public void exigir(Modulo modulo) {
        if (!estaActivo(modulo)) {
            throw new ModuloDesactivadoException(culpable(modulo));
        }
    }

    /**
     * Quien esta apagado de verdad. Si a un hijo lo tumba su padre, el mensaje nombra al
     * padre: decirle a alguien que no hay "pago en efectivo" cuando lo que no hay es cobrar
     * en absoluto manda a buscar el check equivocado.
     */
    private Modulo culpable(Modulo modulo) {
        return modulo.esHijo() && !estaActivo(modulo.getPadre()) ? modulo.getPadre() : modulo;
    }

    /**
     * Claves de lo que esta encendido, para que el frontend sepa que pintar. Es publico y
     * sin token: dice que hace el negocio, que es justo lo que el escaparate ya cuenta.
     */
    @Transactional(readOnly = true)
    public Set<String> clavesActivas() {
        return estado().efectivos().stream()
                .map(Modulo::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Catalogo completo con su estado, para la pantalla de configuracion. */
    @Transactional(readOnly = true)
    public List<ModuloDTO> listar() {
        Estado actual = estado();
        return Arrays.stream(Modulo.values())
                .map(modulo -> new ModuloDTO(
                        modulo,
                        actual.guardado(modulo),
                        actual.efectivos().contains(modulo)))
                .toList();
    }

    /**
     * Aplica los cambios de la pantalla. Rechaza una clave que no exista en el catalogo:
     * si se aceptara, la tabla guardaria el estado de un modulo que nadie consulta nunca y
     * la pantalla mentiria.
     *
     * <p>Encender un hijo con el padre apagado si se acepta: no hace nada todavia, pero
     * dejarlo preparado es legitimo y el {@code efectivo} del DTO lo cuenta tal cual.
     */
    @Transactional
    public List<ModuloDTO> actualizar(ActualizarModulosDTO request) {
        for (CambioModuloDTO cambio : request.getCambios()) {
            Modulo modulo = resolver(cambio.getClave());
            ModuloEstado fila = moduloRepository.findById(modulo.name())
                    .orElseGet(() -> new ModuloEstado(modulo, true));
            fila.setActivo(Boolean.TRUE.equals(cambio.getActivo()));
            moduloRepository.save(fila);
        }
        cache.set(null);
        return listar();
    }

    /** Los perfiles de arranque que se le pueden ofrecer a una peluqueria nueva. */
    public List<PerfilArranqueDTO> perfiles() {
        return Arrays.stream(PerfilArranque.values())
                .map(PerfilArranqueDTO::new)
                .toList();
    }

    /**
     * Aplica un perfil de arranque: escribe el estado de <b>todos</b> los modulos de una
     * vez, encendiendo los del perfil y apagando el resto.
     *
     * <p>Se escriben todos y no solo los que cambian, a proposito: asi la tabla queda con
     * una fila explicita por modulo y el resultado no depende de lo que hubiera antes.
     * Aplicar el mismo perfil dos veces deja lo mismo.
     *
     * <p><b>No borra nada.</b> Vale aqui igual que al apagar un modulo a mano: los datos de
     * lo que se apaga siguen en la base y vuelven al encenderlo. Lo unico que este atajo
     * hace distinto es apagar varias cosas de golpe, y por eso la pantalla enseña antes la
     * lista de lo que se va a apagar.
     */
    @Transactional
    public List<ModuloDTO> aplicarPerfil(PerfilArranque perfil) {
        for (Modulo modulo : Modulo.values()) {
            boolean encendido = perfil.getEncendidos().contains(modulo);
            ModuloEstado fila = moduloRepository.findById(modulo.name())
                    .orElseGet(() -> new ModuloEstado(modulo, encendido));
            fila.setActivo(encendido);
            moduloRepository.save(fila);
        }
        cache.set(null);
        return listar();
    }

    /** Traduce la clave que llega por URL. Una que no exista es un 400, no un 500. */
    public PerfilArranque resolverPerfil(String clave) {
        try {
            return PerfilArranque.valueOf(clave);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el perfil de arranque " + clave + ".");
        }
    }

    private Modulo resolver(String clave) {
        try {
            return Modulo.valueOf(clave);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el modulo " + clave + ".");
        }
    }

    /**
     * Lo guardado y lo que aplica de verdad, calculado de una vez.
     *
     * <p>Las dos reglas de la jerarquia, que se decidieron de entrada para que la pantalla
     * no mienta:
     * <ul>
     *   <li><b>Apagar el padre apaga los hijos de hecho.</b> No se les toca la fila: si el
     *       padre vuelve, vuelven ellos como estaban.
     *   <li><b>Apagar todos los hijos es lo mismo que apagar el padre.</b> Si no, quedaria
     *       un modulo "encendido" sin ninguna forma de hacer nada dentro: PAGOS encendido y
     *       ningun medio de pago es exactamente eso.
     * </ul>
     * No hay circularidad entre las dos: un hijo solo mira su fila y la de su padre.
     */
    private Estado estado() {
        Estado actual = cache.get();
        if (actual != null) {
            return actual;
        }

        Map<String, Boolean> filas = moduloRepository.findAll().stream()
                .collect(Collectors.toMap(ModuloEstado::getClave, ModuloEstado::isActivo, (a, b) -> b));

        // Sin fila vale el defecto: todos nacen encendidos, asi que desplegar un modulo
        // nuevo no le quita nada a nadie hasta que un administrador lo apague. Una fila
        // cuya clave ya no exista en el enum se ignora sola, porque aqui se recorre el enum.
        Map<Modulo, Boolean> guardado = new EnumMap<>(Modulo.class);
        for (Modulo modulo : Modulo.values()) {
            guardado.put(modulo, filas.getOrDefault(modulo.name(), true));
        }

        Set<Modulo> efectivos = EnumSet.noneOf(Modulo.class);
        for (Modulo modulo : Modulo.values()) {
            if (!guardado.get(modulo)) {
                continue;
            }
            if (modulo.esHijo()) {
                if (guardado.get(modulo.getPadre())) {
                    efectivos.add(modulo);
                }
            } else if (sinHijos(modulo) || algunHijoEncendido(guardado, modulo)) {
                efectivos.add(modulo);
            }
        }

        Estado calculado = new Estado(guardado, efectivos);
        cache.set(calculado);
        return calculado;
    }

    private boolean sinHijos(Modulo padre) {
        return Arrays.stream(Modulo.values()).noneMatch(m -> m.getPadre() == padre);
    }

    private boolean algunHijoEncendido(Map<Modulo, Boolean> guardado, Modulo padre) {
        return Arrays.stream(Modulo.values())
                .anyMatch(m -> m.getPadre() == padre && guardado.get(m));
    }

    /**
     * Lo que decidio el administrador ({@code guardado}) y lo que aplica de verdad
     * ({@code efectivos}). El panel necesita los dos: la casilla se pinta con lo guardado,
     * y la diferencia entre ambos es lo que hay que explicarle al que la mira.
     */
    private record Estado(Map<Modulo, Boolean> guardado, Set<Modulo> efectivos) {

        boolean guardado(Modulo modulo) {
            return Boolean.TRUE.equals(guardado.get(modulo));
        }
    }
}
