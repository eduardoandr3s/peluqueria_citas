package com.segovia.peluqueria.exception;

import com.segovia.peluqueria.almacen.AlmacenException;
import com.segovia.peluqueria.asistente.AsistenteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> manejarErroresDeValidacion(MethodArgumentNotValidException ex){
        Map<String, String> errores = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) ->{
            String nombreCampo = ((FieldError) error).getField();
            String mensajeError = error.getDefaultMessage();
            errores.put(nombreCampo, mensajeError);
        });
        return errores;
    }

    // Captura específicamente cuando no encontramos un recurso en la BD
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ResourceNotFoundException.class)
    public Map<String, String> manejarNoEncontrado(ResourceNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    // Captura JSON mal formado o valores no validos para un tipo (ej. un rol inexistente en el enum)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Map<String, String> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El cuerpo de la peticion es invalido o contiene un valor no permitido.");
        return error;
    }

    // Captura cuando un usuario intenta acceder a un recurso que no le pertenece
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public Map<String, String> manejarAccesoDenegado(AccessDeniedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    // Refresh token invalido (caducado, ya rotado/reusado, o credenciales cambiadas): el cliente
    // debe volver a iniciar sesion. 401 lo distingue del 403 de "sin permiso".
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public Map<String, String> manejarRefreshTokenInvalido(InvalidRefreshTokenException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    // Captura cuando se intenta agendar una cita en un horario ya ocupado
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ConflictoHorarioException.class)
    public Map<String, String> manejarConflictoHorario(ConflictoHorarioException ex) {
        log.warn("Conflicto de horario: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    // Captura operaciones que la peticion pide bien pero el estado del recurso no permite
    // (p. ej. pedir el recibo de un pago que aun no esta cobrado)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(EstadoInvalidoException.class)
    public Map<String, String> manejarEstadoInvalido(EstadoInvalidoException ex) {
        log.warn("Estado no valido para la operacion: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    // Captura errores de validacion de logica de negocio (fecha pasada, horario fuera de rango, etc.)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, String> manejarArgumentoInvalido(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return error;
    }

    // Captura cuando intentamos eliminar un recurso que tiene relaciones en la BD
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public Map<String, String> manejarViolacionDeIntegridad(org.springframework.dao.DataIntegrityViolationException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "No se puede eliminar este registro porque tiene otros datos asociados.");
        return error;
    }

    // El fichero subido pasa del tope: lo corta el contenedor antes de llegar al
    // controlador, asi que no se puede tratar como una validacion normal.
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Map<String, String> manejarSubidaDemasiadoGrande(MaxUploadSizeExceededException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El fichero es demasiado grande.");
        return error;
    }

    // Fallo del almacen de ficheros: el problema esta arriba, no en la peticion
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(AlmacenException.class)
    public Map<String, String> manejarFalloDeAlmacen(AlmacenException ex) {
        log.error("Fallo del almacen de ficheros", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "No se ha podido guardar el fichero. Intentelo de nuevo.");
        return error;
    }

    // Ruta que no existe. Sin este handler la traga el generico de Exception y sale un 500
    // "error interno", que es mentira: el servidor esta bien, es la URL la que no existe.
    // Importa mas de lo que parece con el asistente: cuando esta apagado su ruta no se
    // registra, y el cliente distingue "no esta desplegado" (404) de "ha fallado" (500).
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public Map<String, String> manejarRutaNoEncontrada(NoResourceFoundException ex) {
        log.debug("Ruta no encontrada: {}", ex.getResourcePath());
        Map<String, String> error = new HashMap<>();
        error.put("error", "El recurso solicitado no existe.");
        return error;
    }

    // Fallo del proveedor del modelo: el asistente es un extra, asi que se degrada con un
    // mensaje util y nunca tumba nada mas. La cuota agotada se distingue porque reintentar
    // no arregla nada hasta que el contador se reinicia.
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(AsistenteException.class)
    public Map<String, String> manejarFalloDelAsistente(AsistenteException ex) {
        Map<String, String> error = new HashMap<>();
        if (ex.isCuotaAgotada()) {
            log.warn("Cuota del asistente agotada", ex);
            error.put("error", "El asistente no esta disponible en este momento. "
                    + "Puedes consultar los servicios en la web o llamarnos por telefono.");
        } else {
            log.error("Fallo del asistente", ex);
            error.put("error", "El asistente no ha podido responder. Intentalo de nuevo en un momento.");
        }
        return error;
    }

    // Handler generico: atrapa cualquier excepcion no controlada y devuelve un mensaje seguro sin exponer el stack trace
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Map<String, String> manejarErrorGenerico(Exception ex) {
        log.error("Error interno no controlado", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Ha ocurrido un error interno en el servidor. Por favor intente mas tarde.");
        return error;
    }
}
