package cl.duoc.machan.security;

import cl.duoc.machan.model.Rol;
import cl.duoc.machan.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de creación, validación y renovación de tokens JWT.
 * El token firma con HMAC-SHA256 y contiene el correo, el rol y tiempos de vida.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Genera un token de acceso (1 hora por defecto). */
    public String generarToken(Usuario usuario) {
        return construir(usuario, expirationMs);
    }

    /** Genera un token de renovación (24 horas por defecto). */
    public String generarTokenRenovacion(Usuario usuario) {
        return construir(usuario, refreshExpirationMs);
    }

    private String construir(Usuario usuario, long ttlMs) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol().name())
                .claim("id", usuario.getId())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /** Extrae el correo (subject) de un token. Lanza excepción si es inválido o expiró. */
    public String extraerEmail(String token) {
        return parsear(token).getSubject();
    }

    /** Extrae el rol del token. */
    public Rol extraerRol(String token) {
        return Rol.valueOf(parsear(token).get("rol", String.class));
    }

    /** Valida que el token sea auténtico y no haya expirado. */
    public boolean esValido(String token) {
        try {
            parsear(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Milisegundos restantes de vigencia del token (negativo si expiró). */
    public long milisegundosRestantes(String token) {
        Date exp = parsear(token).getExpiration();
        return exp.getTime() - System.currentTimeMillis();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
