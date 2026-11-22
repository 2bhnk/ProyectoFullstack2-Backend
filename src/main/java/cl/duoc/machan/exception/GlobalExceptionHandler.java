package cl.duoc.machan.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejador global: todas las respuestas de error llegan en el mismo formato JSON.
 *
 * <pre>
 * {
 *   "timestamp": "2026-11-21T22:00:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "mensaje": "Producto no encontrado con id \"x\"",
 *   "path": "/api/v1/productos/x",
 *   "errores": { "campo": "detalle" }   // solo en errores de validación
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Errores de negocio lanzados por los servicios (400, 401, 403, 404, 409). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> manejarApi(ApiException ex, HttpServletRequest req) {
        return responder(ex.getStatus(), ex.getMessage(), req, null);
    }

    /** 400: @Valid sobre el cuerpo de la petición. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos", req, campos);
    }

    /** 400: validación de parámetros de método o de entidades JPA. */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> manejarRestricciones(Exception ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos: " + ex.getMessage(), req, null);
    }

    /** 400: JSON mal formado o tipo de dato incorrecto (ej. rol inexistente). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o contiene valores no permitidos.", req, null);
    }

    /** 400: parámetro de ruta con tipo incorrecto (ej. /usuarios/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> manejarTipo(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' tiene un valor no válido.", req, null);
    }

    /** 400: falta un encabezado requerido. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> manejarHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "Falta el encabezado requerido: " + ex.getHeaderName(), req, null);
    }

    /** 401: credenciales incorrectas en el login. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> manejarCredenciales(BadCredentialsException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.", req, null);
    }

    /** 401: cualquier otro fallo de autenticación. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> manejarAutenticacion(AuthenticationException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, "No fue posible autenticar al usuario.", req, null);
    }

    /**
     * 403 (o 401 si no hay sesión): denegado por @PreAuthorize.
     * Un visitante anónimo recibe 401 para que el frontend lo redirija al login.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> manejarAcceso(AccessDeniedException ex, HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return responder(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para acceder a este recurso.", req, null);
        }
        return responder(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción.", req, null);
    }

    /** 404: ruta inexistente. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarRutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado.", req, null);
    }

    /** 405: método HTTP no soportado por la ruta. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMetodo(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED,
                "Método " + ex.getMethod() + " no permitido en esta ruta.", req, null);
    }

    /** 415: Content-Type no soportado. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Usa Content-Type: application/json.", req, null);
    }

    /** 409: violación de integridad (registro duplicado o referenciado por otra tabla). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarIntegridad(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT,
                "La operación entra en conflicto con datos existentes (registro duplicado o en uso).", req, null);
    }

    /** 500: error no controlado (no se exponen detalles internos al cliente). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarGeneral(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor. Intenta nuevamente más tarde.", req, null);
    }

    private ResponseEntity<Map<String, Object>> responder(
            HttpStatus status, String mensaje, HttpServletRequest req, Map<String, String> errores) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", status.getReasonPhrase());
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("path", req.getRequestURI());
        if (errores != null && !errores.isEmpty()) {
            cuerpo.put("errores", errores);
        }
        return ResponseEntity.status(status).body(cuerpo);
    }
}
