package cl.duoc.machan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solicitud de registro de cliente. El rol siempre es CLIENTE por seguridad. */
public record RegistroRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo no válido") String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password) {
}
