package com.segovia.peluqueria.pago;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import com.segovia.peluqueria.cita.Cita;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Convierte un {@link Pago} en el PDF de su recibo.
 *
 * <p>Se renderiza una plantilla Thymeleaf a PDF en vez de maquetar el documento en
 * codigo: el motor ya esta en el proyecto para los correos, asi que el recibo se
 * escribe y se ajusta en HTML, no recolocando coordenadas.
 *
 * <p>El PDF se genera al vuelo y no se guarda en ningun sitio. Un recibo de una pagina
 * son unas decenas de KB y se puede reconstruir siempre desde la base de datos, asi que
 * almacenarlo solo anadiria cuota y ciclo de vida que gestionar. Si algun dia hace falta,
 * se cachea.
 */
@Component
public class ReciboPdfGenerador {

    static {
        // openhtmltopdf loguea por java.util.logging y a nivel INFO suelta tres lineas por
        // documento ("Prepare to fly", tiempos, selectores). Serian tres lineas por cada
        // recibo descargado, asi que se sube a WARNING: los avisos de verdad (una fuente
        // que falta, CSS que no entiende) siguen viendose.
        for (String logger : XRLog.listRegisteredLoggers()) {
            XRLog.setLevel(logger, Level.WARNING);
        }
    }

    /** Plantilla del recibo, bajo {@code src/main/resources/templates/}. */
    private static final String PLANTILLA = "recibo/pago";

    private static final DateTimeFormatter FECHA_Y_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final DateTimeFormatter SOLO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ITemplateEngine motorPlantillas;

    public ReciboPdfGenerador(ITemplateEngine motorPlantillas) {
        this.motorPlantillas = motorPlantillas;
    }

    /**
     * @return el PDF del recibo, listo para escribir en la respuesta
     */
    public byte[] generar(Pago pago) {
        String html = motorPlantillas.process(PLANTILLA, contexto(pago));

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder constructor = new PdfRendererBuilder();
            constructor.useFastMode();
            // Sin baseUri: la plantilla es autocontenida (nada de imagenes ni CSS externo),
            // asi que generar un recibo no depende de la red ni puede quedarse colgado.
            constructor.withHtmlContent(html, null);
            constructor.toStream(salida);
            constructor.run();
            return salida.toByteArray();
        } catch (IOException e) {
            // Un fallo aqui no es del cliente ni algo que pueda arreglar: se deja subir
            // como 500 en vez de disfrazarlo de respuesta valida.
            throw new UncheckedIOException("No se ha podido generar el PDF del recibo.", e);
        }
    }

    /** Nombre con el que se descarga: identifica el recibo sin exponer datos del cliente. */
    public String nombreFichero(Pago pago) {
        return "recibo-" + pago.getIdPago() + ".pdf";
    }

    private Context contexto(Pago pago) {
        Cita cita = pago.getCita();
        Context contexto = new Context(Locale.forLanguageTag("es-ES"));

        contexto.setVariable("numero", pago.getIdPago());
        // Fecha del documento: la del cobro si ya se cobro; si no, la de creacion del pago.
        contexto.setVariable("fechaEmision",
                SOLO_FECHA.format(pago.getFechaPago() != null
                        ? pago.getFechaPago()
                        : pago.getFechaCreacion()));

        contexto.setVariable("clienteNombre", cita.getUsuario().getNombre());
        contexto.setVariable("clienteEmail", cita.getUsuario().getEmail());

        contexto.setVariable("servicio", cita.getServicio().getNombre());
        contexto.setVariable("citaFecha", FECHA_Y_HORA.format(cita.getFechaHora()));
        contexto.setVariable("peluquero",
                cita.getPeluquero() != null ? cita.getPeluquero().getNombre() : null);

        contexto.setVariable("importe", importeConDosDecimales(pago.getMonto()));
        contexto.setVariable("metodoPago", textoMetodo(pago.getMetodoPago()));
        contexto.setVariable("reembolsado", pago.getEstadoPago() == EstadoPago.REEMBOLSADO);
        contexto.setVariable("referencia", pago.getReferenciaExterna());

        return contexto;
    }

    /**
     * Formatea el importe a mano en vez de con {@code NumberFormat}: asi el PDF sale igual
     * en cualquier maquina, sin depender del locale por defecto de la JVM que lo genere.
     */
    private String importeConDosDecimales(BigDecimal monto) {
        return String.format(Locale.forLanguageTag("es-ES"), "%,.2f", monto);
    }

    private String textoMetodo(MetodoPago metodo) {
        return switch (metodo) {
            case TARJETA -> "Tarjeta";
            case EFECTIVO -> "Efectivo";
            case TRANSFERENCIA -> "Transferencia";
        };
    }
}
