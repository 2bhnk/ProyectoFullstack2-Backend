package cl.duoc.machan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Creación/edición de producto (versión v2: requiere precio, stock y categoría). */
public record ProductoV2Request(
        @NotBlank(message = "El id es obligatorio") String id,
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        String nombreCorto,
        @NotNull(message = "El precio es obligatorio")
        @Min(value = 0, message = "El precio no puede ser negativo") Long precio,
        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo") Integer stock,
        String imagen,
        String distribuidor,
        String descripcion,
        Long categoriaId) {
}
