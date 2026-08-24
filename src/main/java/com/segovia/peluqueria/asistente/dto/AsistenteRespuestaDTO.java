package com.segovia.peluqueria.asistente.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta del asistente. Lleva el consumo de tokens del turno porque en un tier gratuito
 * el límite es la cuota, no el dinero: sin esto no hay forma de saber cuánto queda ni de
 * detectar que una conversación se ha ido de madre hasta que la API empieza a devolver 429.
 */
@Data
@AllArgsConstructor
public class AsistenteRespuestaDTO {

    private String respuesta;

    /** Tokens de entrada del turno (incluye el historial reenviado y el prompt de sistema). */
    private Integer tokensEntrada;

    /** Tokens generados por el modelo en este turno. */
    private Integer tokensSalida;
}
