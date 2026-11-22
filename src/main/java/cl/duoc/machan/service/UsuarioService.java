package cl.duoc.machan.service;

import cl.duoc.machan.dto.UsuarioAdminRequest;
import cl.duoc.machan.dto.UsuarioResponse;
import cl.duoc.machan.exception.ApiException;
import cl.duoc.machan.model.Rol;
import cl.duoc.machan.model.Usuario;
import cl.duoc.machan.repository.BoletaRepository;
import cl.duoc.machan.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios (panel administrativo).
 * Reglas: correo único, contraseña cifrada con BCrypt, el ADMIN no puede eliminarse
 * a sí mismo ni quitarse su propio rol, y no se eliminan usuarios con compras registradas.
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BoletaRepository boletaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            BoletaRepository boletaRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.boletaRepository = boletaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UsuarioResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    /** Crea un usuario con cualquier rol (solo ADMIN). La contraseña es obligatoria al crear. */
    @Transactional
    public UsuarioResponse crear(UsuarioAdminRequest request) {
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflicto("Este correo electrónico ya se encuentra registrado.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw ApiException.badRequest("La contraseña es obligatoria al crear un usuario.");
        }

        Usuario usuario = new Usuario(request.nombre().trim(), email,
                passwordEncoder.encode(request.password()), request.rol());
        return toResponse(usuarioRepository.save(usuario));
    }

    /** Edita un usuario (PUT); si no se envía contraseña se mantiene la actual. */
    @Transactional
    public UsuarioResponse editar(Long id, UsuarioAdminRequest request) {
        Usuario usuario = buscar(id);
        String email = request.email().trim().toLowerCase();

        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(otro -> {
            if (!otro.getId().equals(id)) {
                throw ApiException.conflicto("Ese correo ya está registrado por otro usuario.");
            }
        });
        validarNoAutoDegradarse(usuario, request.rol());

        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setRol(request.rol());
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }
        return toResponse(usuarioRepository.save(usuario));
    }

    /** Cambia solo el rol de un usuario (PATCH). */
    @Transactional
    public UsuarioResponse cambiarRol(Long id, Rol rol) {
        if (rol == null) {
            throw ApiException.badRequest("El rol es obligatorio.");
        }
        Usuario usuario = buscar(id);
        validarNoAutoDegradarse(usuario, rol);
        usuario.setRol(rol);
        return toResponse(usuarioRepository.save(usuario));
    }

    /** Elimina un usuario; impide eliminar la cuenta con la sesión activa y usuarios con compras. */
    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = buscar(id);

        if (esUsuarioActual(usuario)) {
            throw ApiException.prohibido("No puedes eliminar tu propia cuenta mientras tienes la sesión activa.");
        }
        if (boletaRepository.existsByUsuarioId(id)) {
            throw ApiException.conflicto("No se puede eliminar a " + usuario.getNombre()
                    + " porque tiene boletas registradas en su historial.");
        }
        usuarioRepository.delete(usuario);
    }

    /** Cuenta de usuarios por rol (estadísticas del panel). */
    public long contarPorRol(Rol rol) {
        return usuarioRepository.countByRol(rol);
    }

    // ---------- Utilidades ----------

    private void validarNoAutoDegradarse(Usuario usuario, Rol nuevoRol) {
        if (esUsuarioActual(usuario) && usuario.getRol() == Rol.ADMIN && nuevoRol != Rol.ADMIN) {
            throw ApiException.prohibido("No puedes quitarte el rol ADMIN a ti mismo.");
        }
    }

    private boolean esUsuarioActual(Usuario usuario) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && usuario.getEmail().equalsIgnoreCase(auth.getName());
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("Usuario no encontrado con id " + id));
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }
}
