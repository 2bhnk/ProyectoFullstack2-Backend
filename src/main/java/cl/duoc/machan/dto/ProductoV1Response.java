package cl.duoc.machan.dto;

/** Respuesta de producto con datos básicos (versión v1). */
public record ProductoV1Response(
        String id,
        String nombre,
        String nombreCorto,
        Long precio,
        Integer stock,
        String imagen,
        String distribuidor) {
}
