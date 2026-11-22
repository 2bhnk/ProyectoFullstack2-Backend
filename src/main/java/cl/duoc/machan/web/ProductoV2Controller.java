package cl.duoc.machan.web;

import cl.duoc.machan.config.OpenApiConfig;
import cl.duoc.machan.dto.ProductoV2Request;
import cl.duoc.machan.dto.ProductoV2Response;
import cl.duoc.machan.dto.StockRequest;
import cl.duoc.machan.service.ProductoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API v2: productos con estadísticas (vendidos), categoría y descripción. */
@RestController
@RequestMapping("/api/v2/productos")
@Tag(name = "Productos v2", description = "CRUD de productos con estadísticas detalladas y categoría")
public class ProductoV2Controller {

    private final ProductoService productoService;

    public ProductoV2Controller(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @Operation(summary = "Lista los productos",
            description = "Público. Incluye vendidos, categoría y descripción. Filtros opcionales por categoría y texto.")
    @ApiResponse(responseCode = "200", description = "Listado del catálogo")
    public ResponseEntity<List<ProductoV2Response>> listar(
            @Parameter(description = "Filtra por id de categoría", example = "1")
            @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "Busca por texto en el nombre", example = "Daiwa")
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(productoService.listarV2(categoriaId, q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un producto", description = "Público. Incluye estadísticas avanzadas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto encontrado"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente")
    })
    public ResponseEntity<ProductoV2Response> obtener(
            @Parameter(description = "Id slug del producto", example = "nendoroid-daiwa-scarlet")
            @PathVariable String id) {
        return ResponseEntity.ok(productoService.obtenerV2(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Crea un producto", description = "Requiere rol ADMIN. La versión 2 exige precio, stock y categoriaId.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Producto creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o falta categoriaId"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Categoría inexistente"),
        @ApiResponse(responseCode = "409", description = "Ya existe un producto con ese id")
    })
    public ResponseEntity<ProductoV2Response> crear(@Valid @RequestBody ProductoV2Request request) {
        ProductoV2Response creado = productoService.crearV2(request);
        return ResponseEntity.created(URI.create("/api/v2/productos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Actualiza un producto", description = "Requiere rol ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto o categoría inexistente")
    })
    public ResponseEntity<ProductoV2Response> actualizar(
            @PathVariable String id, @Valid @RequestBody ProductoV2Request request) {
        return ResponseEntity.ok(productoService.actualizarV2(id, request));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Actualiza parcialmente el stock", description = "Requiere rol ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock actualizado"),
        @ApiResponse(responseCode = "400", description = "Stock negativo o ausente"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente")
    })
    public ResponseEntity<ProductoV2Response> patchStock(
            @PathVariable String id, @Valid @RequestBody StockRequest request) {
        productoService.actualizarStock(id, request);
        return ResponseEntity.ok(productoService.obtenerV2(id));
    }

    @PutMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Actualiza el stock (compatibilidad)", description = "Requiere rol ADMIN. Equivalente al PATCH.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock actualizado"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente")
    })
    public ResponseEntity<ProductoV2Response> actualizarStock(
            @PathVariable String id, @Valid @RequestBody StockRequest request) {
        productoService.actualizarStock(id, request);
        return ResponseEntity.ok(productoService.obtenerV2(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Elimina un producto", description = "Requiere rol ADMIN. No elimina productos ya vendidos.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Producto eliminado"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente"),
        @ApiResponse(responseCode = "409", description = "El producto aparece en boletas emitidas")
    })
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
