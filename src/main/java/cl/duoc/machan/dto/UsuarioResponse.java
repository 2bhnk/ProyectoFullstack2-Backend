package cl.duoc.machan.dto;

import cl.duoc.machan.model.Rol;

/** Datos públicos de un usuario (sin contraseña). */
public record UsuarioResponse(Long id, String nombre, String email, Rol rol) {
}
