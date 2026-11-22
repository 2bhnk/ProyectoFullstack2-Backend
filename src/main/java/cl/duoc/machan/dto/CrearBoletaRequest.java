package cl.duoc.machan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Solicitud de creación de boleta (checkout del carrito). */
public record CrearBoletaRequest(
        @NotEmpty(message = "El carrito no puede estar vacío") List<@Valid ItemBoletaRequest> items,
        String cupon) {
}
