package cl.duoc.machan.dto;

/** Respuesta de producto con estadísticas detalladas (versión v2). */
public record ProductoV2Response(
        String id,
        String nombre,
        String nombreCorto,
        Long precio,
        Integer stock,
        Integer vendidos,
        String imagen,
        String distribuidor,
        String categoria,
        String descripcion) {
}
