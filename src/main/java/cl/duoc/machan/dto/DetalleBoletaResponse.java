package cl.duoc.machan.dto;

/** Detalle de una línea de la boleta. */
public record DetalleBoletaResponse(
        String productoId,
        String nombreProducto,
        Integer cantidad,
        Long precioUnitario,
        Long subtotal) {
}
