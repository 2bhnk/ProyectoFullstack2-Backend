package cl.duoc.machan.service;

import cl.duoc.machan.dto.CategoriaRequest;
import cl.duoc.machan.dto.CategoriaResponse;
import cl.duoc.machan.exception.ApiException;
import cl.duoc.machan.model.Categoria;
import cl.duoc.machan.model.Producto;
import cl.duoc.machan.repository.CategoriaRepository;
import cl.duoc.machan.repository.ProductoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de categorías de productos (nombres únicos; no se eliminan si tienen productos). */
@Service
@Transactional(readOnly = true)
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, ProductoRepository productoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
    }

    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CategoriaResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw ApiException.conflicto("Ya existe una categoría con el nombre \"" + nombre + "\".");
        }
        return toResponse(categoriaRepository.save(new Categoria(nombre)));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = buscar(id);
        String nombre = request.nombre().trim();

        categoriaRepository.findByNombreIgnoreCase(nombre).ifPresent(otra -> {
            if (!otra.getId().equals(id)) {
                throw ApiException.conflicto("Ya existe otra categoría con el nombre \"" + nombre + "\".");
            }
        });

        categoria.setNombre(nombre);
        // Mantiene sincronizado el nombre desnormalizado en los productos de la categoría.
        for (Producto producto : productoRepository.findByCategoriaId(id)) {
            producto.setCategoria(categoria);
        }
        return toResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria categoria = buscar(id);
        if (productoRepository.existsByCategoriaId(id)) {
            throw ApiException.conflicto("No se puede eliminar la categoría \"" + categoria.getNombre()
                    + "\" porque tiene productos asociados.");
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("Categoría no encontrada con id " + id));
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre());
    }
}
