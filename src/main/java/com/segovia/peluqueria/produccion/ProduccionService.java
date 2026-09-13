package com.segovia.peluqueria.produccion;

import com.segovia.peluqueria.exception.ResourceNotFoundException;
import com.segovia.peluqueria.modulo.Modulo;
import com.segovia.peluqueria.modulo.ModuloService;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.peluquero.PeluqueroRepository;
import com.segovia.peluqueria.produccion.dto.LineaProduccionDTO;
import com.segovia.peluqueria.produccion.dto.ProduccionPeluqueroDTO;
import com.segovia.peluqueria.produccion.dto.ProduccionResponseDTO;
import com.segovia.peluqueria.usuario.Usuario;
import com.segovia.peluqueria.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class ProduccionService {

    /** Tope del rango consultable, para que nadie pida veinte anos de golpe. */
    private static final int MAX_MESES_RANGO = 24;

    private final ProduccionRepository produccionRepository;
    private final PeluqueroRepository peluqueroRepository;
    private final UsuarioRepository usuarioRepository;
    private final ModuloService moduloService;

    public ProduccionService(ProduccionRepository produccionRepository,
                             PeluqueroRepository peluqueroRepository,
                             UsuarioRepository usuarioRepository,
                             ModuloService moduloService) {
        this.produccionRepository = produccionRepository;
        this.peluqueroRepository = peluqueroRepository;
        this.usuarioRepository = usuarioRepository;
        this.moduloService = moduloService;
    }

    /**
     * Produccion del peluquero que corresponde a la cuenta autenticada.
     *
     * <p>El id no se recibe por parametro a proposito: se resuelve desde la cuenta. Asi no
     * existe la version del endpoint en la que un peluquero pide "su" produccion pasando el
     * id de otro.
     */
    public ProduccionResponseDTO produccionPropia(String emailAutenticado, LocalDate desde, LocalDate hasta) {
        moduloService.exigir(Modulo.PRODUCCION);
        Usuario actual = usuarioRepository.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + emailAutenticado));
        Peluquero ficha = peluqueroRepository.findByUsuarioIdUsuario(actual.getIdUsuario())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tu cuenta no esta vinculada a ninguna ficha de peluquero. Pideselo a un administrador."));
        return produccionDe(ficha, desde, hasta);
    }

    public ProduccionResponseDTO produccionDePeluquero(Integer idPeluquero, LocalDate desde, LocalDate hasta) {
        moduloService.exigir(Modulo.PRODUCCION);
        Peluquero ficha = peluqueroRepository.findById(idPeluquero)
                .orElseThrow(() -> new ResourceNotFoundException("Peluquero no encontrado con id: " + idPeluquero));
        return produccionDe(ficha, desde, hasta);
    }

    public List<ProduccionPeluqueroDTO> comparativa(LocalDate desde, LocalDate hasta) {
        moduloService.exigir(Modulo.PRODUCCION);
        validarRango(desde, hasta);
        boolean conComision = comisionEncendida();
        return produccionRepository
                .comparativa(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(), exigeCobro()).stream()
                .map(f -> new ProduccionPeluqueroDTO(
                        entero(f[0]), (String) f[1], numero(f[2]), importe(f[3]),
                        conComision ? importe(f[4]) : null))
                .toList();
    }

    /**
     * Si la produccion exige que la cita este cobrada, que es lo que dice el modulo PAGOS.
     *
     * <p>Apagado, produccion cuenta las citas COMPLETADA a secas. No es un detalle: sin
     * pagos nada llegaria nunca a PAGADO y todos los totales del negocio serian cero, que
     * es exactamente lo que parece un bug y no lo es. Ver {@code ProduccionRepository}.
     */
    private boolean exigeCobro() {
        return moduloService.estaActivo(Modulo.PAGOS);
    }

    /** Con el modulo apagado la comision no viaja: null es "aqui no se comisiona". */
    private boolean comisionEncendida() {
        return moduloService.estaActivo(Modulo.COMISIONES);
    }

    private ProduccionResponseDTO produccionDe(Peluquero ficha, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        LocalDateTime desdeHora = desde.atStartOfDay();
        LocalDateTime hastaHora = hasta.plusDays(1).atStartOfDay();
        Integer id = ficha.getIdPeluquero();

        boolean exigeCobro = exigeCobro();
        boolean conComision = comisionEncendida();

        Object[] resumen = primeraFila(produccionRepository.resumen(id, desdeHora, hastaHora, exigeCobro));
        // Sin pagos no hay "realizado sin cobrar": esas citas ya estan contadas arriba, y
        // repetirlas abajo seria contar dos veces el mismo trabajo.
        Object[] pendiente = exigeCobro
                ? primeraFila(produccionRepository.sinCobrar(id, desdeHora, hastaHora))
                : new Object[]{0L, BigDecimal.ZERO};

        ProduccionResponseDTO dto = new ProduccionResponseDTO();
        dto.setIdPeluquero(id);
        dto.setNombre(ficha.getNombre());
        dto.setDesde(desde);
        dto.setHasta(hasta);
        dto.setServiciosRealizados(numero(resumen[0]));
        dto.setImporteVendido(importe(resumen[1]));
        dto.setComision(conComision ? importe(resumen[2]) : null);
        dto.setExigeCobro(exigeCobro);
        dto.setServiciosSinCobrar(numero(pendiente[0]));
        dto.setImporteSinCobrar(importe(pendiente[1]));
        dto.setPorServicio(lineas(produccionRepository.porServicio(id, desdeHora, hastaHora, exigeCobro), conComision));
        dto.setPorMes(lineas(produccionRepository.porMes(id, desdeHora, hastaHora, exigeCobro), conComision));
        return dto;
    }

    private List<LineaProduccionDTO> lineas(List<Object[]> filas, boolean conComision) {
        return filas.stream()
                .map(f -> new LineaProduccionDTO((String) f[0], numero(f[1]), importe(f[2]),
                        conComision ? importe(f[3]) : null))
                .toList();
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' debe ser anterior o igual a 'hasta'");
        }
        if (desde.plusMonths(MAX_MESES_RANGO).isBefore(hasta)) {
            throw new IllegalArgumentException("El rango no puede pasar de " + MAX_MESES_RANGO + " meses.");
        }
    }

    /**
     * Una consulta de agregacion siempre devuelve una fila, pero si alguna vez no lo hace
     * es mejor un cero que un IndexOutOfBounds en la pantalla de nomina.
     */
    private Object[] primeraFila(List<Object[]> filas) {
        return filas.isEmpty() ? new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO} : filas.get(0);
    }

    private long numero(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }

    private Integer entero(Object valor) {
        return valor == null ? null : ((Number) valor).intValue();
    }

    /**
     * A dos decimales. La comision se calcula en SQL como importe * porcentaje / 100 y sale
     * con la escala que le da Postgres; redondear aqui evita que la pantalla muestre
     * 12,749999 y que la suma de las lineas no cuadre con el total.
     */
    private BigDecimal importe(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal decimal = valor instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) valor).doubleValue());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }
}
