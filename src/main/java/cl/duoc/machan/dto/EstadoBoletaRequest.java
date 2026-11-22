package cl.duoc.machan.dto;

import cl.duoc.machan.model.EstadoBoleta;
import jakarta.validation.constraints.NotNull;

/** Cambio de estado de una boleta desde el panel de administración. */
public record EstadoBoletaRequest(
        @NotNull(message = "El estado es obligatorio") EstadoBoleta estado) {
}
