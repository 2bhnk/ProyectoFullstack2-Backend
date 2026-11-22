package cl.duoc.machan.service;

import cl.duoc.machan.dto.AuthResponse;
import cl.duoc.machan.dto.LoginRequest;
import cl.duoc.machan.dto.RegistroRequest;
import cl.duoc.machan.dto.UsuarioResponse;
import cl.duoc.machan.exception.ApiException;
import cl.duoc.machan.model.Rol;
import cl.duoc.machan.model.Usuario;
import cl.duoc.machan.repository.UsuarioRepository;
import cl.duoc.machan.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticación: registro, login y emisión de tokens JWT. */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Registra un nuevo cliente (rol siempre CLIENTE por seguridad). */
    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        String email = request.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflicto("Este correo ya se encuentra registrado.");
        }

        Usuario usuario = new Usuario(request.nombre().trim(), email,
                passwordEncoder.encode(request.password()), Rol.CLIENTE);
        usuarioRepository.save(usuario);

        return emitirToken(usuario);
    }

    /** Valida credenciales contra la BD y emite el token JWT. */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.badRequest("Correo o contraseña incorrectos."));

        return emitirToken(usuario);
    }

    /** Emite un token de acceso con los datos del usuario. */
    public AuthResponse emitirToken(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        return AuthResponse.bearer(token, jwtService.getExpirationMs(), toResponse(usuario));
    }

    /** Renovación segura: emite un token nuevo si el actual sigue siendo válido. */
    public AuthResponse renovar(String tokenActual) {
        if (!jwtService.esValido(tokenActual)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "El token es inválido o expiró. Inicia sesión nuevamente.");
        }
        String email = jwtService.extraerEmail(tokenActual);
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.noEncontrado("Usuario no encontrado."));
        return emitirToken(usuario);
    }

    public UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }
}
