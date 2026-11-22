package cl.duoc.machan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Actualización de stock desde el panel de administración. */
public record StockRequest(
        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo") Integer stock) {
}
