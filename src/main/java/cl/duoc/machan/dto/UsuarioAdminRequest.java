package cl.duoc.machan.dto;

import cl.duoc.machan.model.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta/edición de usuarios desde el panel de administración. */
public record UsuarioAdminRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo no válido") String email,
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password,
        @NotNull(message = "El rol es obligatorio") Rol rol) {
}
