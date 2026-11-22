package cl.duoc.machan.dto;

import jakarta.validation.constraints.NotBlank;

/** Creación/edición de categoría. */
public record CategoriaRequest(@NotBlank(message = "El nombre es obligatorio") String nombre) {
}
