package cl.duoc.machan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Solicitud de inicio de sesión. */
public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo no válido") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {
}
