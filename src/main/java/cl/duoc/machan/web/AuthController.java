package cl.duoc.machan.web;

import cl.duoc.machan.config.OpenApiConfig;
import cl.duoc.machan.dto.AuthResponse;
import cl.duoc.machan.dto.LoginRequest;
import cl.duoc.machan.dto.RegistroRequest;
import cl.duoc.machan.dto.UsuarioResponse;
import cl.duoc.machan.exception.ApiException;
import cl.duoc.machan.model.Usuario;
import cl.duoc.machan.repository.UsuarioRepository;
import cl.duoc.machan.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de autenticación (públicos) y de sesión (requieren token). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro, login, sesión y renovación de tokens JWT")
public class AuthController {

    private final AuthService authService;
    private final UsuarioRepository usuarioRepository;

    public AuthController(AuthService authService, UsuarioRepository usuarioRepository) {
        this.authService = authService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/registro")
    @Operation(summary = "Registra un nuevo cliente",
            description = "Público. Crea el usuario con rol CLIENTE (nunca otro rol) y devuelve su token JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cliente registrado; incluye token y datos del usuario"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos (correo mal formado, contraseña corta)"),
        @ApiResponse(responseCode = "409", description = "El correo ya está registrado")
    })
    public ResponseEntity<AuthResponse> registro(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión",
            description = "Público. Valida credenciales (BCrypt) y devuelve un token JWT Bearer con el rol del usuario.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credenciales correctas; devuelve token"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Usuario de la sesión actual",
            description = "Permite al frontend restaurar la sesión tras recargar la página validando el token guardado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos del usuario autenticado"),
        @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    })
    public ResponseEntity<UsuarioResponse> me(@Parameter(hidden = true) Authentication authentication) {
        return ResponseEntity.ok(authService.toResponse(usuarioActual(authentication)));
    }

    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Renueva el token",
            description = "Emite un token nuevo con vigencia completa si el actual sigue siendo válido.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token renovado"),
        @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    })
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw ApiException.noAutorizado("Encabezado Authorization con token Bearer requerido.");
        }
        return ResponseEntity.ok(authService.renovar(authHeader.substring(7)));
    }

    private Usuario usuarioActual(Authentication authentication) {
        if (authentication == null) {
            throw ApiException.noAutorizado("Debes iniciar sesión.");
        }
        return usuarioRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> ApiException.noAutorizado("La sesión ya no es válida."));
    }
}
