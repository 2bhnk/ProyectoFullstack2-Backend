package cl.duoc.machan.web;

import cl.duoc.machan.config.OpenApiConfig;
import cl.duoc.machan.dto.BoletaResponse;
import cl.duoc.machan.dto.CrearBoletaRequest;
import cl.duoc.machan.dto.EstadoBoletaRequest;
import cl.duoc.machan.service.BoletaService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Órdenes de compra (boletas). Todas las operaciones requieren token.
 * CLIENTE ve solo las suyas; ADMIN y VENDEDOR ven todas; solo ADMIN cambia el estado.
 */
@RestController
@RequestMapping("/api/v1/boletas")
@Tag(name = "Boletas / Órdenes", description = "Compras de la tienda y su historial")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
public class BoletaController {

    private final BoletaService boletaService;

    public BoletaController(BoletaService boletaService) {
        this.boletaService = boletaService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN','VENDEDOR')")
    @Operation(summary = "Confirma la compra del carrito",
            description = "Valida el stock de todos los productos, calcula subtotal, cupón y total en el servidor, "
                    + "descuenta el inventario y crea la boleta en una sola transacción. "
                    + "Cupones: DUOC20 / correo @duocuc.cl (20%), PRIDE10 (10%).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Boleta creada y stock descontado"),
        @ApiResponse(responseCode = "400", description = "Carrito vacío, cantidad inválida o stock insuficiente"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "404", description = "Algún producto no existe")
    })
    public ResponseEntity<BoletaResponse> crear(@Valid @RequestBody CrearBoletaRequest request) {
        BoletaResponse boleta = boletaService.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/boletas/" + boleta.id())).body(boleta);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista las órdenes según el rol",
            description = "ADMIN/VENDEDOR ven todas; CLIENTE solo las suyas. Ordenadas de la más reciente a la más antigua.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de boletas"),
        @ApiResponse(responseCode = "401", description = "Sin token")
    })
    public ResponseEntity<List<BoletaResponse>> listar() {
        return ResponseEntity.ok(boletaService.listar());
    }

    @GetMapping("/mis-boletas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Historial propio", description = "Boletas del usuario autenticado, cualquiera sea su rol.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial del usuario"),
        @ApiResponse(responseCode = "401", description = "Sin token")
    })
    public ResponseEntity<List<BoletaResponse>> misBoletas() {
        return ResponseEntity.ok(boletaService.listarPropias());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detalle de una orden", description = "El dueño, VENDEDOR o ADMIN pueden verla.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Boleta encontrada"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "La boleta pertenece a otro cliente"),
        @ApiResponse(responseCode = "404", description = "Boleta inexistente")
    })
    public ResponseEntity<BoletaResponse> obtener(
            @Parameter(description = "Id de la boleta", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(boletaService.obtener(id));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambia el estado de una boleta",
            description = "Solo ADMIN. Al pasar a CANCELADA se repone el stock de cada producto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Estado inválido"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "Boleta inexistente"),
        @ApiResponse(responseCode = "409", description = "La boleta ya estaba cancelada")
    })
    public ResponseEntity<BoletaResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody EstadoBoletaRequest request) {
        return ResponseEntity.ok(boletaService.cambiarEstado(id, request.estado()));
    }
}
