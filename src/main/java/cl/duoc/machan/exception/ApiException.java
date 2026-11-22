package cl.duoc.machan.exception;

import org.springframework.http.HttpStatus;

/** Excepción de negocio con código HTTP asociado. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 400: datos o regla de negocio no cumplida. */
    public static ApiException badRequest(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje);
    }

    /** 401: falta autenticación. */
    public static ApiException noAutorizado(String mensaje) {
        return new ApiException(HttpStatus.UNAUTHORIZED, mensaje);
    }

    /** 403: autenticado pero sin permiso. */
    public static ApiException prohibido(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, mensaje);
    }

    /** 404: recurso inexistente. */
    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, mensaje);
    }

    /** 409: conflicto con el estado actual (duplicados, registros en uso). */
    public static ApiException conflicto(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, mensaje);
    }
}
