package cl.duoc.machan.dto;

/** Respuesta de autenticación: token JWT + datos del usuario. */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        UsuarioResponse usuario) {

    public static AuthResponse bearer(String token, long expiresInMs, UsuarioResponse usuario) {
        return new AuthResponse(token, "Bearer", expiresInMs, usuario);
    }
}
