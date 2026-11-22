package cl.duoc.machan.repository;

import cl.duoc.machan.model.Producto;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, String> {

    List<Producto> findByCategoriaId(Long categoriaId);

    List<Producto> findByNombreContainingIgnoreCase(String texto);

    long countByStockLessThanEqual(Integer stock);

    boolean existsByCategoriaId(Long categoriaId);

    /**
     * Lee el producto con bloqueo pesimista de escritura (SELECT ... FOR UPDATE).
     * Evita que dos compras simultáneas vendan la misma última unidad.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdParaActualizar(@Param("id") String id);
}
