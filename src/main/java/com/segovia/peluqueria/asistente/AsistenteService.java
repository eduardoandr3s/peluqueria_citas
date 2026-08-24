package com.segovia.peluqueria.asistente;

import com.segovia.peluqueria.asistente.dto.AsistentePreguntaDTO;
import com.segovia.peluqueria.asistente.dto.AsistenteRespuestaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Asistente conversacional de la peluquería. Responde preguntas de clientes sobre servicios,
 * precios, horario y huecos libres, consultando los datos reales a través de
 * {@link AsistenteHerramientas} (tool calling) en vez de improvisarlos.
 *
 * <p>El bucle de herramientas lo lleva Spring AI: cuando el modelo pide una herramienta,
 * Spring la ejecuta, le devuelve el resultado y vuelve a llamar al modelo hasta que hay una
 * respuesta en texto. Aquí solo se arma la petición y se traducen los fallos.
 *
 * <p>Solo existe si hay un modelo configurado ({@code spring.ai.model.chat} distinto de
 * {@code none}). Sin él no habría bean {@code ChatClient.Builder} y el contexto no
 * arrancaría, y el asistente tiene que poder estar apagado sin tumbar la aplicación. La
 * condición se expresa contra el interruptor de Spring AI y no contra la API key de un
 * proveedor concreto, para que cambiar de proveedor no obligue a tocar esta clase.
 */
@Service
@ConditionalOnExpression("!'${spring.ai.model.chat:none}'.equals('none')")
public class AsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteService.class);

    /**
     * El prompt de sistema se reenvía en <strong>cada</strong> turno, así que cada línea de
     * aquí se paga tantas veces como mensajes tenga la conversación: está escrito corto a
     * propósito. Las dos últimas reglas no son de estilo, son de coste y de seguridad: el
     * endpoint es público, y sin ellas el asistente es un ChatGPT gratis para cualquiera que
     * encuentre la URL, que es la vía real de quemar la cuota.
     */
    private static final String PROMPT = """
            Eres el asistente de la peluqueria Lalo Segovia. Ayudas a clientes por escrito,
            en el idioma en el que te escriban, con frases cortas y sin markdown.

            Reglas:
            - Precios, duraciones, horarios y huecos libres SIEMPRE salen de las herramientas.
              Nunca los supongas ni los recuerdes de un mensaje anterior: vuelve a consultarlos.
            - Para resolver "manana", "el jueves" o "la semana que viene", usa la fecha de hoy
              que devuelve consultarHorario. No supongas en que dia estamos.
            - No puedes reservar, cambiar ni anular citas, ni consultar las de nadie. Si te lo
              piden, di que la cita se pide desde la web o la app, o llamando a la peluqueria.
            - Si una herramienta falla o no hay huecos, dilo con naturalidad y ofrece otro dia.
            - Si te preguntan algo que no es de la peluqueria, di en una frase que solo puedes
              ayudar con eso y no sigas la conversacion.
            """;

    private final ChatClient chatClient;

    public AsistenteService(ChatClient.Builder builder, AsistenteHerramientas herramientas) {
        this.chatClient = builder
                .defaultSystem(PROMPT)
                .defaultTools(herramientas)
                .build();
    }

    public AsistenteRespuestaDTO responder(AsistentePreguntaDTO pregunta) {
        ChatResponse respuesta;
        try {
            respuesta = chatClient.prompt()
                    .messages(aMensajes(pregunta.getHistorial()))
                    .user(pregunta.getMensaje())
                    .call()
                    .chatResponse();
        } catch (Exception e) {
            throw new AsistenteException("Fallo al consultar el modelo", e, esCuotaAgotada(e));
        }

        if (respuesta == null || respuesta.getResult() == null) {
            throw new AsistenteException("El modelo no devolvio ninguna respuesta", null, false);
        }

        String texto = respuesta.getResult().getOutput().getText();
        Usage uso = respuesta.getMetadata() != null ? respuesta.getMetadata().getUsage() : null;
        Integer entrada = uso != null ? uso.getPromptTokens() : null;
        Integer salida = uso != null ? uso.getCompletionTokens() : null;

        log.info("Asistente respondio con {} tokens de entrada y {} de salida", entrada, salida);
        return new AsistenteRespuestaDTO(texto, entrada, salida);
    }

    /**
     * El historial llega del cliente (ver {@link AsistentePreguntaDTO}) y se traduce a los
     * mensajes que entiende Spring AI. El turno actual no se añade aquí: lo pone
     * {@code .user(...)}, que es el que el modelo debe responder.
     */
    private List<Message> aMensajes(List<AsistentePreguntaDTO.MensajeDTO> historial) {
        if (historial == null || historial.isEmpty()) {
            return List.of();
        }
        List<Message> mensajes = new ArrayList<>(historial.size());
        for (AsistentePreguntaDTO.MensajeDTO m : historial) {
            mensajes.add(m.isDelCliente() ? new UserMessage(m.getTexto()) : new AssistantMessage(m.getTexto()));
        }
        return mensajes;
    }

    /**
     * Distingue "he agotado la cuota gratuita" de cualquier otro fallo. No hay un tipo de
     * excepcion propio para esto: el proveedor devuelve 429 y Spring AI lo envuelve, asi que
     * se busca en el texto de la cadena de causas. Es fragil por naturaleza, y por eso el
     * fallback es tratarlo como un fallo normal, no como cuota agotada.
     */
    private boolean esCuotaAgotada(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            String mensaje = causa.getMessage();
            if (mensaje != null && (mensaje.contains("429")
                    || mensaje.contains("RESOURCE_EXHAUSTED")
                    || mensaje.toLowerCase().contains("quota"))) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }
}
