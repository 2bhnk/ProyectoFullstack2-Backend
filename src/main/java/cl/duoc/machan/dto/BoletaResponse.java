package cl.duoc.machan.dto;

import cl.duoc.machan.model.EstadoBoleta;
import java.time.LocalDateTime;
import java.util.List;

/** Respuesta completa de una boleta/orden. */
public record BoletaResponse(
        Long id,
        LocalDateTime fecha,
        String cliente,
        String emailCliente,
        Long subtotal,
        Long descuento,
        Long total,
        String cupon,
        EstadoBoleta estado,
        List<DetalleBoletaResponse> detalles) {
}
