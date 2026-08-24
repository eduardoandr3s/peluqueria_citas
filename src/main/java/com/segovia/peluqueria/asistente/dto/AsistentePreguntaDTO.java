package com.segovia.peluqueria.asistente.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Pregunta del cliente más el historial de la conversación.
 *
 * <p>La conversación <strong>la mantiene el cliente</strong>, no el servidor: en cada turno
 * manda los mensajes anteriores. Es lo que corresponde con este despliegue, porque el
 * contenedor de Render es efímero y una memoria en RAM se perdería en cada redespliegue,
 * mientras que guardarla en base de datos añadiría una tabla y una política de retención de
 * datos personales para un asistente que no los necesita.
 *
 * <p>La consecuencia es que el historial es <strong>entrada no confiable</strong>: el cliente
 * puede inventarse turnos del asistente. Aquí no importa porque ninguna herramienta escribe y
 * ninguna decisión de autorización depende del historial, pero por eso mismo el asistente no
 * debe crecer hacia acciones con efectos sin cambiar antes este diseño.
 */
@Data
public class AsistentePreguntaDTO {

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
    private String mensaje;

    /**
     * Turnos anteriores, del más antiguo al más reciente. El tope es de coste, no de
     * usabilidad: el historial se reenvía completo en cada turno, así que sin límite una
     * conversación larga multiplica el gasto de tokens de cada mensaje siguiente.
     */
    @Valid
    @Size(max = 10, message = "El historial no puede superar los 10 mensajes")
    private List<MensajeDTO> historial = List.of();

    @Data
    public static class MensajeDTO {

        /** {@code true} si lo escribió el cliente; {@code false} si lo respondió el asistente. */
        private boolean delCliente;

        @NotBlank(message = "El texto del mensaje es obligatorio")
        @Size(max = 2000, message = "El texto del mensaje no puede superar los 2000 caracteres")
        private String texto;
    }
}
