package com.segovia.peluqueria.asistente;

import com.segovia.peluqueria.asistente.dto.AsistentePreguntaDTO;
import com.segovia.peluqueria.asistente.dto.AsistenteRespuestaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AsistenteServiceTest {

    private ChatClient.ChatClientRequestSpec peticion;
    private ChatClient.CallResponseSpec llamada;
    private AsistenteService asistenteService;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        peticion = mock(ChatClient.ChatClientRequestSpec.class);
        llamada = mock(ChatClient.CallResponseSpec.class);

        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(peticion);
        when(peticion.messages(anyList())).thenReturn(peticion);
        when(peticion.user(anyString())).thenReturn(peticion);
        when(peticion.call()).thenReturn(llamada);

        asistenteService = new AsistenteService(builder, mock(AsistenteHerramientas.class));
    }

    private void responderCon(String texto, Integer entrada, Integer salida) {
        Generation generacion = new Generation(new AssistantMessage(texto));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(entrada, salida))
                .build();
        when(llamada.chatResponse()).thenReturn(new ChatResponse(List.of(generacion), metadata));
    }

    private AsistentePreguntaDTO pregunta(String mensaje) {
        AsistentePreguntaDTO dto = new AsistentePreguntaDTO();
        dto.setMensaje(mensaje);
        return dto;
    }

    private AsistentePreguntaDTO.MensajeDTO mensaje(boolean delCliente, String texto) {
        AsistentePreguntaDTO.MensajeDTO m = new AsistentePreguntaDTO.MensajeDTO();
        m.setDelCliente(delCliente);
        m.setTexto(texto);
        return m;
    }

    @Test
    void responder_devuelveElTextoYElConsumoDeTokens() {
        responderCon("El corte de caballero cuesta 15 euros.", 1200, 40);

        AsistenteRespuestaDTO respuesta = asistenteService.responder(pregunta("cuanto vale un corte?"));

        assertEquals("El corte de caballero cuesta 15 euros.", respuesta.getRespuesta());
        assertEquals(1200, respuesta.getTokensEntrada());
        assertEquals(40, respuesta.getTokensSalida());
    }

    /**
     * El historial va al modelo con el rol correcto: si los turnos del asistente se
     * enviaran como turnos del cliente, el modelo leeria sus propias respuestas como
     * peticiones y la conversacion se descarrila.
     */
    @Test
    void responder_traduceElHistorialAlRolCorrecto() {
        responderCon("Ese dia abrimos a las nueve.", 100, 10);
        AsistentePreguntaDTO dto = pregunta("y el jueves?");
        dto.setHistorial(List.of(
                mensaje(true, "que horario teneis?"),
                mensaje(false, "De nueve a ocho, de lunes a sabado.")));

        asistenteService.responder(dto);

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.captor();
        verify(peticion).messages(captor.capture());
        List<Message> enviados = captor.getValue();

        assertEquals(2, enviados.size(), "el turno actual no va en el historial, va en user()");
        assertEquals(MessageType.USER, enviados.get(0).getMessageType());
        assertEquals(MessageType.ASSISTANT, enviados.get(1).getMessageType());
        verify(peticion).user("y el jueves?");
    }

    @Test
    void responder_sinHistorialNoEnviaMensajesPrevios() {
        responderCon("Hola, en que puedo ayudarte?", 90, 8);

        asistenteService.responder(pregunta("hola"));

        verify(peticion).messages(List.of());
    }

    /**
     * En un tier gratuito el fallo esperable no es una caida, es haber agotado la cuota del
     * dia. Se distingue porque el mensaje al cliente es distinto: reintentar no sirve.
     */
    @Test
    void responder_cuotaAgotadaSeMarcaComoTal() {
        when(peticion.call()).thenThrow(new RuntimeException(
                "429 RESOURCE_EXHAUSTED: Quota exceeded for quota metric 'Generate requests'"));

        AsistenteException ex = assertThrows(AsistenteException.class,
                () -> asistenteService.responder(pregunta("hola")));

        assertTrue(ex.isCuotaAgotada());
    }

    @Test
    void responder_cuotaAgotadaSeDetectaEnUnaCausaAnidada() {
        RuntimeException raiz = new RuntimeException("HTTP 429 Too Many Requests");
        when(peticion.call()).thenThrow(new IllegalStateException("fallo al llamar al modelo", raiz));

        AsistenteException ex = assertThrows(AsistenteException.class,
                () -> asistenteService.responder(pregunta("hola")));

        assertTrue(ex.isCuotaAgotada());
    }

    @Test
    void responder_otroFalloNoSeConfundeConCuotaAgotada() {
        when(peticion.call()).thenThrow(new RuntimeException("Connection reset by peer"));

        AsistenteException ex = assertThrows(AsistenteException.class,
                () -> asistenteService.responder(pregunta("hola")));

        assertFalse(ex.isCuotaAgotada());
    }

    /**
     * Una causa que se apunta a si misma colgaria el recorrido de causas. No es teorico:
     * algunos wrappers de cliente HTTP lo hacen.
     */
    @Test
    void responder_causaCiclicaNoCuelga() {
        RuntimeException ciclica = new RuntimeException("fallo raro") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        when(peticion.call()).thenThrow(ciclica);

        AsistenteException ex = assertThrows(AsistenteException.class,
                () -> asistenteService.responder(pregunta("hola")));

        assertFalse(ex.isCuotaAgotada());
    }

    @Test
    void responder_respuestaVaciaDelModeloEsUnFallo() {
        when(llamada.chatResponse()).thenReturn(null);

        AsistenteException ex = assertThrows(AsistenteException.class,
                () -> asistenteService.responder(pregunta("hola")));

        assertFalse(ex.isCuotaAgotada());
    }

    @Test
    void responder_sinMetadatosDeUsoNoRevienta() {
        Generation generacion = new Generation(new AssistantMessage("Hecho."));
        when(llamada.chatResponse()).thenReturn(new ChatResponse(List.of(generacion)));

        AsistenteRespuestaDTO respuesta = asistenteService.responder(pregunta("hola"));

        assertEquals("Hecho.", respuesta.getRespuesta());
    }
}
