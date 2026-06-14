package com.segovia.peluqueria.notificacion;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

/**
 * Render de plantillas Thymeleaf + envio de correo HTML. El envio es asincrono ({@link Async})
 * para no bloquear la peticion HTTP, y los errores se registran sin propagarse: un fallo de
 * correo nunca debe tumbar una operacion de negocio ya confirmada.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Locale ES = Locale.of("es");

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String from;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
                        SpringTemplateEngine templateEngine,
                        @Value("${peluqueria.mail.from}") String from,
                        @Value("${peluqueria.mail.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.from = from;
        this.enabled = enabled;
    }

    @Async
    public void enviarHtml(String destinatario, String asunto, String plantilla, Map<String, Object> modelo) {
        if (!enabled) {
            log.info("[mail deshabilitado] Se omite el envio de '{}' a {}", asunto, destinatario);
            return;
        }
        try {
            Context contexto = new Context(ES);
            contexto.setVariables(modelo);
            String html = templateEngine.process("email/" + plantilla, contexto);

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);

            mailSender.send(mensaje);
            log.info("Correo '{}' enviado a {}", asunto, destinatario);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, e.getMessage());
        }
    }
}
