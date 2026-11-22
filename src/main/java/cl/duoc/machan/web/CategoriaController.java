package cl.duoc.machan.web;

import cl.duoc.machan.config.OpenApiConfig;
import cl.duoc.machan.dto.CategoriaRequest;
import cl.duoc.machan.dto.CategoriaResponse;
import cl.duoc.machan.service.CategoriaService;
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

/** Categorías de productos (lectura pública, escritura solo ADMIN). */
@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorías", description = "Categorías del catálogo")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @Operation(summary = "Lista las categorías", description = "Público.")
    @ApiResponse(responseCode = "200", description = "Listado de categorías")
    public ResponseEntity<List<CategoriaResponse>> listar() {
        return ResponseEntity.ok(categoriaService.listar());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una categoría", description = "Público.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
        @ApiResponse(responseCode = "404", description = "Categoría inexistente")
    })
    public ResponseEntity<CategoriaResponse> obtener(
            @Parameter(description = "Id de la categoría", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(categoriaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Crea una categoría", description = "Requiere rol ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Categoría creada"),
        @ApiResponse(responseCode = "400", description = "Nombre vacío"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre")
    })
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse creada = categoriaService.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Edita una categoría", description = "Requiere rol ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
        @ApiResponse(responseCode = "400", description = "Nombre vacío"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Categoría inexistente"),
        @ApiResponse(responseCode = "409", description = "Nombre duplicado")
    })
    public ResponseEntity<CategoriaResponse> editar(
            @PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(categoriaService.actualizar(id, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Renombra una categoría", description = "Requiere rol ADMIN. Actualización parcial del nombre.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoría renombrada"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Categoría inexistente"),
        @ApiResponse(responseCode = "409", description = "Nombre duplicado")
    })
    public ResponseEntity<CategoriaResponse> renombrar(
            @PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(categoriaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Elimina una categoría", description = "Requiere rol ADMIN. Solo si no tiene productos.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Categoría eliminada"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Categoría inexistente"),
        @ApiResponse(responseCode = "409", description = "La categoría tiene productos asociados")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
