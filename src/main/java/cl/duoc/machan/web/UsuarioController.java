package cl.duoc.machan.web;

import cl.duoc.machan.config.OpenApiConfig;
import cl.duoc.machan.dto.CambioRolRequest;
import cl.duoc.machan.dto.UsuarioAdminRequest;
import cl.duoc.machan.dto.UsuarioResponse;
import cl.duoc.machan.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de usuarios: CRUD completo, exclusivo del rol ADMIN. */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema (solo ADMIN)")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
@PreAuthorize("hasRole('ADMIN')")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
    @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN")
})
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "Lista todos los usuarios")
    @ApiResponse(responseCode = "200", description = "Listado de usuarios (sin contraseñas)")
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente")
    })
    public ResponseEntity<UsuarioResponse> obtener(
            @Parameter(description = "Id del usuario", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crea un usuario con rol", description = "La contraseña es obligatoria y se guarda cifrada con BCrypt.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o falta contraseña"),
        @ApiResponse(responseCode = "409", description = "Correo ya registrado")
    })
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioAdminRequest request) {
        UsuarioResponse creado = usuarioService.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita un usuario", description = "Contraseña opcional: si se omite se conserva la actual.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente"),
        @ApiResponse(responseCode = "409", description = "Correo usado por otro usuario")
    })
    public ResponseEntity<UsuarioResponse> editar(
            @PathVariable Long id, @Valid @RequestBody UsuarioAdminRequest request) {
        return ResponseEntity.ok(usuarioService.editar(id, request));
    }

    @PatchMapping("/{id}/rol")
    @Operation(summary = "Cambia solo el rol de un usuario", description = "Un ADMIN no puede quitarse su propio rol.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rol actualizado"),
        @ApiResponse(responseCode = "400", description = "Rol inválido"),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente")
    })
    public ResponseEntity<UsuarioResponse> cambiarRol(
            @PathVariable Long id, @Valid @RequestBody CambioRolRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarRol(id, request.rol()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina un usuario",
            description = "No permite eliminar la propia cuenta activa ni usuarios con boletas registradas.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Usuario eliminado"),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente"),
        @ApiResponse(responseCode = "409", description = "El usuario tiene boletas registradas")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
