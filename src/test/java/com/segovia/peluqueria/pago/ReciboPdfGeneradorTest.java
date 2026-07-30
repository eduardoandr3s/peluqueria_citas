package com.segovia.peluqueria.pago;

import com.segovia.peluqueria.cita.Cita;
import com.segovia.peluqueria.cita.EstadoCita;
import com.segovia.peluqueria.peluquero.Peluquero;
import com.segovia.peluqueria.servicio.Servicio;
import com.segovia.peluqueria.usuario.Usuario;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aqui SI se renderiza el PDF de verdad, con el mismo motor Thymeleaf y la misma
 * plantilla que en produccion: es la unica forma de que una plantilla mal cerrada o un
 * caracter que la fuente no soporta salte en un test y no en la cara del usuario.
 *
 * <p>Se comprueba el contenido extrayendo el texto con PDFBox, no solo que el fichero
 * empiece por {@code %PDF-}: un PDF valido y vacio pasaria esa comprobacion.
 */
class ReciboPdfGeneradorTest {

    private ReciboPdfGenerador generador;

    @BeforeEach
    void setUp() {
        // Mismo resolver que usa Spring Boot para las plantillas del classpath, montado a
        // mano para no arrancar el contexto: el test tarda milisegundos.
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        // SpringTemplateEngine y no el TemplateEngine base: el de Spring evalua las
        // expresiones con SpEL y el base con OGNL, que no esta en el classpath (falla con
        // NoClassDefFoundError: ognl/PropertyAccessor). Ademas asi el test usa el mismo
        // motor que se inyecta en produccion, que es de lo que se trata. Desde Thymeleaf
        // 3.1 no hace falta darle un ApplicationContext.
        SpringTemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolver);

        generador = new ReciboPdfGenerador(motor);
    }

    private Pago pagoCobrado() {
        Usuario cliente = new Usuario();
        cliente.setIdUsuario(1);
        cliente.setNombre("Carlos Munoz");
        cliente.setEmail("carlos@test.com");

        Servicio servicio = new Servicio();
        servicio.setNombre("Corte y barba");
        servicio.setPrecio(new BigDecimal("25.50"));

        Cita cita = new Cita();
        cita.setIdCita(1);
        cita.setUsuario(cliente);
        cita.setServicio(servicio);
        cita.setFechaHora(LocalDateTime.of(2026, 8, 3, 10, 30));
        cita.setEstado(EstadoCita.CONFIRMADA);

        Pago pago = new Pago();
        pago.setIdPago(42);
        pago.setCita(cita);
        pago.setMonto(new BigDecimal("25.50"));
        pago.setMetodoPago(MetodoPago.TARJETA);
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setReferenciaExterna("pi_123");
        pago.setFechaCreacion(LocalDateTime.of(2026, 7, 30, 9, 0));
        pago.setFechaPago(LocalDateTime.of(2026, 7, 30, 9, 5));
        return pago;
    }

    private static String textoDe(byte[] pdf) throws IOException {
        try (var documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    @Test
    void generaUnPdfValido() {
        byte[] pdf = generador.generar(pagoCobrado());

        assertTrue(pdf.length > 0);
        // Firma de fichero PDF: los 5 primeros bytes son "%PDF-".
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }

    @Test
    void elPdfLlevaLosDatosDelPagoYDeLaCita() throws IOException {
        String texto = textoDe(generador.generar(pagoCobrado()));

        assertTrue(texto.contains("Lalo Segovia"), "falta el nombre del negocio");
        assertTrue(texto.contains("42"), "falta el numero de recibo");
        assertTrue(texto.contains("Carlos Munoz"), "falta el cliente");
        assertTrue(texto.contains("carlos@test.com"), "falta el correo del cliente");
        assertTrue(texto.contains("Corte y barba"), "falta el servicio");
        assertTrue(texto.contains("03/08/2026"), "falta la fecha de la cita");
        assertTrue(texto.contains("Tarjeta"), "falta el metodo de pago");
        assertTrue(texto.contains("25,50"), "falta el importe");
    }

    @Test
    void dejaClaroQueNoEsUnaFactura() throws IOException {
        // El PDF no debe prometer valor fiscal que no tiene.
        String texto = textoDe(generador.generar(pagoCobrado()));

        assertTrue(texto.contains("no una factura"));
    }

    @Test
    void unPagoReembolsadoLoIndicaEnElDocumento() throws IOException {
        Pago pago = pagoCobrado();
        pago.setEstadoPago(EstadoPago.REEMBOLSADO);

        String texto = textoDe(generador.generar(pago));

        assertTrue(texto.contains("reembolsado"), "el recibo debe decir que se reembolso");
    }

    @Test
    void unPagoCobradoNoDiceNadaDeReembolso() throws IOException {
        String texto = textoDe(generador.generar(pagoCobrado()));

        assertFalse(texto.contains("reembolsado"));
    }

    @Test
    void elPeluqueroSaleSoloSiLaCitaLoTiene() throws IOException {
        assertFalse(textoDe(generador.generar(pagoCobrado())).contains("Peluquero"));

        Pago conPeluquero = pagoCobrado();
        Peluquero peluquero = new Peluquero();
        peluquero.setIdPeluquero(1);
        peluquero.setNombre("Lalo");
        conPeluquero.getCita().setPeluquero(peluquero);

        assertTrue(textoDe(generador.generar(conPeluquero)).contains("Lalo"));
    }

    @Test
    void elImporteSaleConDosDecimalesYComaDecimal() throws IOException {
        Pago pago = pagoCobrado();
        pago.setMonto(new BigDecimal("8.00"));

        // Formato espanol y fijo, no el del locale de la maquina que genere el PDF.
        assertTrue(textoDe(generador.generar(pago)).contains("8,00"));
    }

    @Test
    void enEfectivoNoHayReferenciaExterna() throws IOException {
        Pago pago = pagoCobrado();
        pago.setMetodoPago(MetodoPago.EFECTIVO);
        pago.setReferenciaExterna(null);

        String texto = textoDe(generador.generar(pago));

        assertTrue(texto.contains("Efectivo"));
        assertFalse(texto.contains("Referencia"));
    }

    @Test
    void sinFechaDePagoUsaLaDeCreacion() throws IOException {
        // Un pago manual puede no tener fechaPago; el recibo no debe salir sin fecha.
        Pago pago = pagoCobrado();
        pago.setFechaPago(null);

        assertTrue(textoDe(generador.generar(pago)).contains("30/07/2026"));
    }

    @Test
    void elNombreDelFicheroIdentificaElRecibo() {
        assertEquals("recibo-42.pdf", generador.nombreFichero(pagoCobrado()));
    }
}
