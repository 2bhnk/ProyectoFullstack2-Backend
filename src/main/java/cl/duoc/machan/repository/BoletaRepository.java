package cl.duoc.machan.repository;

import cl.duoc.machan.model.Boleta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {

    /** Órdenes de un usuario específico (cliente ve solo las suyas). */
    List<Boleta> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    List<Boleta> findAllByOrderByFechaDesc();

    boolean existsByUsuarioId(Long usuarioId);

    /** Indica si un producto aparece en alguna boleta (no se puede eliminar sin romper el historial). */
    @Query("select case when count(d) > 0 then true else false end "
            + "from DetalleBoleta d where d.producto.id = :productoId")
    boolean existeDetalleConProducto(@Param("productoId") String productoId);
}
