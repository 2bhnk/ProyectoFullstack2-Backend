package cl.duoc.machan.web;

import cl.duoc.machan.config.OpenApiConfig;
import cl.duoc.machan.dto.ProductoV1Request;
import cl.duoc.machan.dto.ProductoV1Response;
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
import org.springframework.web.bind.annotation.RestController;

/** API v1: datos básicos de productos (lectura pública, escritura solo ADMIN). */
@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos v1", description = "CRUD de productos con datos básicos")
public class ProductoV1Controller {

    private final ProductoService productoService;

    public ProductoV1Controller(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @Operation(summary = "Lista todos los productos", description = "Público. Solo datos básicos (catálogo).")
    @ApiResponse(responseCode = "200", description = "Listado del catálogo")
    public ResponseEntity<List<ProductoV1Response>> listar() {
        return ResponseEntity.ok(productoService.listarV1());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un producto", description = "Público.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto encontrado"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente")
    })
    public ResponseEntity<ProductoV1Response> obtener(
            @Parameter(description = "Id slug del producto", example = "nendoroid-daiwa-scarlet")
            @PathVariable String id) {
        return ResponseEntity.ok(productoService.obtenerV1(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Crea un producto", description = "Requiere rol ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Producto creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Categoría inexistente"),
        @ApiResponse(responseCode = "409", description = "Ya existe un producto con ese id")
    })
    public ResponseEntity<ProductoV1Response> crear(@Valid @RequestBody ProductoV1Request request) {
        ProductoV1Response creado = productoService.crearV1(request);
        return ResponseEntity.created(URI.create("/api/v1/productos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Actualiza un producto", description = "Requiere rol ADMIN. Reemplaza los datos del producto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto o categoría inexistente")
    })
    public ResponseEntity<ProductoV1Response> actualizar(
            @PathVariable String id, @Valid @RequestBody ProductoV1Request request) {
        return ResponseEntity.ok(productoService.actualizarV1(id, request));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Actualiza parcialmente el stock",
            description = "Requiere rol ADMIN. Modifica solo el stock del producto (panel de inventario).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock actualizado"),
        @ApiResponse(responseCode = "400", description = "Stock negativo o ausente"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente")
    })
    public ResponseEntity<ProductoV1Response> patchStock(
            @PathVariable String id, @Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(productoService.actualizarStock(id, request));
    }

    @PutMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @Operation(summary = "Actualiza el stock (compatibilidad)",
            description = "Requiere rol ADMIN. Equivalente a PATCH /{id}/stock, se mantiene para el frontend existente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock actualizado"),
        @ApiResponse(responseCode = "400", description = "Stock negativo o ausente"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Producto inexistente")
    })
    public ResponseEntity<ProductoV1Response> actualizarStock(
            @PathVariable String id, @Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(productoService.actualizarStock(id, request));
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
