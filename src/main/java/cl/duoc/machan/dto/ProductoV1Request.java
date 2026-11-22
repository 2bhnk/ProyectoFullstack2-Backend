package cl.duoc.machan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Creación de producto (versión v1: datos básicos). */
public record ProductoV1Request(
        @NotBlank(message = "El id es obligatorio") String id,
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        String nombreCorto,
        @Min(value = 0, message = "El precio no puede ser negativo") Long precio,
        @Min(value = 0, message = "El stock no puede ser negativo") Integer stock,
        String imagen,
        String distribuidor,
        String descripcion,
        Long categoriaId) {
}
