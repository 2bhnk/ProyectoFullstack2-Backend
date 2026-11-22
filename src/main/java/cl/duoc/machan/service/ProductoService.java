package cl.duoc.machan.service;

import cl.duoc.machan.dto.ProductoV1Request;
import cl.duoc.machan.dto.ProductoV1Response;
import cl.duoc.machan.dto.ProductoV2Request;
import cl.duoc.machan.dto.ProductoV2Response;
import cl.duoc.machan.dto.StockRequest;
import cl.duoc.machan.exception.ApiException;
import cl.duoc.machan.model.Categoria;
import cl.duoc.machan.model.Producto;
import cl.duoc.machan.repository.BoletaRepository;
import cl.duoc.machan.repository.CategoriaRepository;
import cl.duoc.machan.repository.ProductoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio del catálogo de productos:
 *  - CRUD en dos versiones de API (v1 básica, v2 con estadísticas y categoría obligatoria).
 *  - Gestión de inventario: actualización, descuento y reposición de stock.
 */
@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final BoletaRepository boletaRepository;

    public ProductoService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            BoletaRepository boletaRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.boletaRepository = boletaRepository;
    }

    // ---------- V1: datos básicos ----------

    public List<ProductoV1Response> listarV1() {
        return productoRepository.findAll().stream().map(this::toV1).toList();
    }

    public ProductoV1Response obtenerV1(String id) {
        return toV1(buscar(id));
    }

    @Transactional
    public ProductoV1Response crearV1(ProductoV1Request request) {
        validarIdLibre(request.id());
        Producto producto = new Producto();
        producto.setId(request.id().trim());
        producto.setNombre(request.nombre().trim());
        producto.setNombreCorto(request.nombreCorto());
        producto.setPrecio(request.precio() != null ? request.precio() : 0L);
        producto.setStock(request.stock() != null ? request.stock() : 0);
        producto.setVendidos(0);
        producto.setImagen(request.imagen());
        producto.setDistribuidor(request.distribuidor());
        producto.setDescripcion(request.descripcion());
        if (request.categoriaId() != null) {
            producto.setCategoria(buscarCategoria(request.categoriaId()));
        }
        return toV1(productoRepository.save(producto));
    }

    @Transactional
    public ProductoV1Response actualizarV1(String id, ProductoV1Request request) {
        Producto producto = buscar(id);
        producto.setNombre(request.nombre().trim());
        producto.setNombreCorto(request.nombreCorto());
        if (request.precio() != null) producto.setPrecio(request.precio());
        if (request.stock() != null) producto.setStock(request.stock());
        producto.setImagen(request.imagen());
        producto.setDistribuidor(request.distribuidor());
        producto.setDescripcion(request.descripcion());
        // Si no se envía categoría se conserva la actual (no se borra por omisión).
        if (request.categoriaId() != null) {
            producto.setCategoria(buscarCategoria(request.categoriaId()));
        }
        return toV1(productoRepository.save(producto));
    }

    // ---------- V2: estadísticas detalladas ----------

    /** Lista con filtros opcionales por categoría y por texto en el nombre. */
    public List<ProductoV2Response> listarV2(Long categoriaId, String texto) {
        List<Producto> productos;
        if (categoriaId != null) {
            productos = productoRepository.findByCategoriaId(categoriaId);
        } else if (texto != null && !texto.isBlank()) {
            productos = productoRepository.findByNombreContainingIgnoreCase(texto.trim());
        } else {
            productos = productoRepository.findAll();
        }
        return productos.stream()
                .filter(p -> texto == null || texto.isBlank()
                        || p.getNombre().toLowerCase().contains(texto.trim().toLowerCase()))
                .map(this::toV2)
                .toList();
    }

    public ProductoV2Response obtenerV2(String id) {
        return toV2(buscar(id));
    }

    @Transactional
    public ProductoV2Response crearV2(ProductoV2Request request) {
        validarIdLibre(request.id());
        if (request.categoriaId() == null) {
            throw ApiException.badRequest("La versión 2 requiere una categoría (categoriaId).");
        }
        Producto producto = new Producto();
        producto.setId(request.id().trim());
        producto.setNombre(request.nombre().trim());
        producto.setNombreCorto(request.nombreCorto());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setVendidos(0);
        producto.setImagen(request.imagen());
        producto.setDistribuidor(request.distribuidor());
        producto.setDescripcion(request.descripcion());
        producto.setCategoria(buscarCategoria(request.categoriaId()));
        return toV2(productoRepository.save(producto));
    }

    @Transactional
    public ProductoV2Response actualizarV2(String id, ProductoV2Request request) {
        Producto producto = buscar(id);
        producto.setNombre(request.nombre().trim());
        producto.setNombreCorto(request.nombreCorto());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setImagen(request.imagen());
        producto.setDistribuidor(request.distribuidor());
        producto.setDescripcion(request.descripcion());
        if (request.categoriaId() != null) {
            producto.setCategoria(buscarCategoria(request.categoriaId()));
        }
        return toV2(productoRepository.save(producto));
    }

    // ---------- Operaciones comunes ----------

    /**
     * Elimina un producto. Si ya fue vendido (aparece en boletas) se rechaza con 409
     * para no romper el historial de compras; en ese caso se recomienda dejar su stock en 0.
     */
    @Transactional
    public void eliminar(String id) {
        Producto producto = buscar(id);
        if (boletaRepository.existeDetalleConProducto(id)) {
            throw ApiException.conflicto("No se puede eliminar \"" + producto.getNombre()
                    + "\" porque forma parte de boletas emitidas. Deja su stock en 0 para retirarlo de la venta.");
        }
        productoRepository.delete(producto);
    }

    /** Fija el stock de un producto (panel de inventario del ADMIN). */
    @Transactional
    public ProductoV1Response actualizarStock(String id, StockRequest request) {
        Producto producto = buscar(id);
        producto.setStock(request.stock());
        return toV1(productoRepository.save(producto));
    }

    /**
     * Descuenta stock con bloqueo pesimista y registra las unidades vendidas.
     * Lanza 400 si no hay existencias suficientes (regla de negocio).
     */
    @Transactional
    public Producto descontarStock(String productoId, int cantidad) {
        Producto producto = buscarParaActualizar(productoId);
        if (producto.getStock() < cantidad) {
            throw ApiException.badRequest("Stock insuficiente para \"" + producto.getNombre()
                    + "\" (disponible: " + producto.getStock() + ", solicitado: " + cantidad + ").");
        }
        producto.setStock(producto.getStock() - cantidad);
        producto.setVendidos(producto.getVendidos() + cantidad);
        return productoRepository.save(producto);
    }

    /** Repone stock (por ejemplo, al cancelar una boleta). */
    @Transactional
    public void reponerStock(String productoId, int cantidad) {
        Producto producto = buscarParaActualizar(productoId);
        producto.setStock(producto.getStock() + cantidad);
        producto.setVendidos(Math.max(0, producto.getVendidos() - cantidad));
        productoRepository.save(producto);
    }

    /** Lectura con bloqueo de escritura (SELECT ... FOR UPDATE). */
    @Transactional
    public Producto buscarParaActualizar(String id) {
        return productoRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> ApiException.noEncontrado("Producto no encontrado con id \"" + id + "\""));
    }

    private void validarIdLibre(String id) {
        if (productoRepository.existsById(id.trim())) {
            throw ApiException.conflicto("Ya existe un producto con el id \"" + id.trim() + "\".");
        }
    }

    private Categoria buscarCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> ApiException.noEncontrado("Categoría no encontrada con id " + categoriaId));
    }

    private Producto buscar(String id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("Producto no encontrado con id \"" + id + "\""));
    }

    // ---------- Mapeos entidad -> DTO ----------

    private ProductoV1Response toV1(Producto p) {
        return new ProductoV1Response(p.getId(), p.getNombre(), p.getNombreCorto(),
                p.getPrecio(), p.getStock(), p.getImagen(), p.getDistribuidor());
    }

    private ProductoV2Response toV2(Producto p) {
        return new ProductoV2Response(p.getId(), p.getNombre(), p.getNombreCorto(),
                p.getPrecio(), p.getStock(), p.getVendidos(), p.getImagen(), p.getDistribuidor(),
                p.getCategoria() != null ? p.getCategoria().getNombre() : p.getCategoriaNombre(),
                p.getDescripcion());
    }
}
