package cl.duoc.machan.dto;

import cl.duoc.machan.model.Rol;
import jakarta.validation.constraints.NotNull;

/** Cambio parcial (PATCH) del rol de un usuario. */
public record CambioRolRequest(@NotNull(message = "El rol es obligatorio") Rol rol) {
}
