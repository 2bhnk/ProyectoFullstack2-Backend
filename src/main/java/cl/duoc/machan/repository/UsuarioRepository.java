package cl.duoc.machan.repository;

import cl.duoc.machan.model.Rol;
import cl.duoc.machan.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRol(Rol rol);
}
