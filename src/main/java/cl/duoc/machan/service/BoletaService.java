package cl.duoc.machan.service;

import cl.duoc.machan.dto.BoletaResponse;
import cl.duoc.machan.dto.CrearBoletaRequest;
import cl.duoc.machan.dto.DetalleBoletaResponse;
import cl.duoc.machan.dto.ItemBoletaRequest;
import cl.duoc.machan.exception.ApiException;
import cl.duoc.machan.model.Boleta;
import cl.duoc.machan.model.DetalleBoleta;
import cl.duoc.machan.model.EstadoBoleta;
import cl.duoc.machan.model.Producto;
import cl.duoc.machan.model.Rol;
import cl.duoc.machan.model.Usuario;
import cl.duoc.machan.repository.BoletaRepository;
import cl.duoc.machan.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de boletas (órdenes de compra):
 *  1. Agrupa las líneas del carrito por producto.
 *  2. Bloquea cada producto y VALIDA el stock de todas las líneas antes de tocar nada.
 *  3. Descuenta el stock y registra las unidades vendidas.
 *  4. Calcula subtotales, descuento por cupón y total en el servidor (nunca se confía en el cliente).
 *  5. Persiste la boleta y sus detalles. Todo ocurre en una sola transacción:
 *     si algo falla, no se descuenta stock ni se crea la boleta.
 *
 * Control de acceso: CLIENTE solo ve sus boletas; VENDEDOR y ADMIN ven todas;
 * solo ADMIN cambia el estado (cancelar repone el stock).
 */
@Service
@Transactional(readOnly = true)
public class BoletaService {

    /** Correos institucionales Duoc UC que obtienen el 20% de descuento. */
    private static final Pattern EMAIL_DUOC = Pattern.compile(
            "^[^\\s@]+@(duocuc\\.cl|profesor\\.duoc\\.cl)$", Pattern.CASE_INSENSITIVE);

    private static final int MAX_UNIDADES_POR_PRODUCTO = 50;

    private final BoletaRepository boletaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoService productoService;

    public BoletaService(
            BoletaRepository boletaRepository,
            UsuarioRepository usuarioRepository,
            ProductoService productoService) {
        this.boletaRepository = boletaRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoService = productoService;
    }

    /** Confirma la compra del carrito del usuario autenticado. */
    @Transactional
    public BoletaResponse crear(CrearBoletaRequest request) {
        Usuario usuario = usuarioActual();

        // 1. Agrupar cantidades por producto (el mismo producto puede venir en varias líneas).
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        for (ItemBoletaRequest item : request.items()) {
            cantidades.merge(item.productoId().trim(), item.cantidad(), Integer::sum);
        }

        // 2. Bloquear y validar stock de TODOS los productos antes de modificar nada.
        Map<String, Producto> productos = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> linea : cantidades.entrySet()) {
            Producto producto = productoService.buscarParaActualizar(linea.getKey());
            int cantidad = linea.getValue();
            if (cantidad > MAX_UNIDADES_POR_PRODUCTO) {
                throw ApiException.badRequest("No puedes comprar más de " + MAX_UNIDADES_POR_PRODUCTO
                        + " unidades de \"" + producto.getNombre() + "\" en una misma boleta.");
            }
            if (producto.getStock() < cantidad) {
                throw ApiException.badRequest("Stock insuficiente para \"" + producto.getNombre()
                        + "\" (disponible: " + producto.getStock() + ", solicitado: " + cantidad + ").");
            }
            productos.put(producto.getId(), producto);
        }

        // 3 y 4. Descontar stock y armar los detalles con el precio vigente del servidor.
        Boleta boleta = new Boleta();
        boleta.setUsuario(usuario);
        boleta.setFecha(LocalDateTime.now());
        boleta.setEstado(EstadoBoleta.PAGADA);

        long subtotal = 0L;
        for (Map.Entry<String, Integer> linea : cantidades.entrySet()) {
            Producto producto = productos.get(linea.getKey());
            int cantidad = linea.getValue();

            productoService.descontarStock(producto.getId(), cantidad);

            DetalleBoleta detalle = new DetalleBoleta(producto, cantidad, producto.getPrecio());
            boleta.agregarDetalle(detalle);
            subtotal = Math.addExact(subtotal, detalle.getSubtotal());
        }

        double porcentaje = porcentajeDescuento(request.cupon());
        long descuento = Math.round(subtotal * porcentaje);

        boleta.setSubtotal(subtotal);
        boleta.setDescuento(descuento);
        boleta.setTotal(Math.max(0L, subtotal - descuento));
        boleta.setCupon(porcentaje > 0 ? normalizarCupon(request.cupon()) : null);

        // 5. Persistir (los detalles se guardan en cascada).
        return toResponse(boletaRepository.save(boleta));
    }

    /**
     * Lista las boletas según el rol:
     *  - ADMIN / VENDEDOR: todas.
     *  - CLIENTE: solo su historial.
     */
    public List<BoletaResponse> listar() {
        Usuario usuario = usuarioActual();
        List<Boleta> boletas = (usuario.getRol() == Rol.CLIENTE)
                ? boletaRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId())
                : boletaRepository.findAllByOrderByFechaDesc();
        return boletas.stream().map(this::toResponse).toList();
    }

    /** Historial propio del usuario autenticado (cualquier rol). */
    public List<BoletaResponse> listarPropias() {
        Usuario usuario = usuarioActual();
        return boletaRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Detalle de una boleta con control de acceso por rol. */
    public BoletaResponse obtener(Long id) {
        Boleta boleta = buscar(id);

        Usuario usuario = usuarioActual();
        boolean esDueno = boleta.getUsuario().getId().equals(usuario.getId());
        boolean esStaff = usuario.getRol() == Rol.ADMIN || usuario.getRol() == Rol.VENDEDOR;

        if (!esDueno && !esStaff) {
            throw ApiException.prohibido("No tienes permiso para ver esta boleta.");
        }
        return toResponse(boleta);
    }

    /**
     * Cambia el estado de una boleta (solo ADMIN).
     * Cancelar repone el stock de cada producto; una boleta cancelada no puede reactivarse.
     */
    @Transactional
    public BoletaResponse cambiarEstado(Long id, EstadoBoleta nuevoEstado) {
        Boleta boleta = buscar(id);
        EstadoBoleta actual = boleta.getEstado();

        if (actual == nuevoEstado) {
            return toResponse(boleta);
        }
        if (actual == EstadoBoleta.CANCELADA) {
            throw ApiException.conflicto("La boleta #" + id + " ya está cancelada y no puede cambiar de estado.");
        }
        if (nuevoEstado == EstadoBoleta.CANCELADA) {
            for (DetalleBoleta detalle : boleta.getDetalles()) {
                productoService.reponerStock(detalle.getProducto().getId(), detalle.getCantidad());
            }
        }
        boleta.setEstado(nuevoEstado);
        return toResponse(boletaRepository.save(boleta));
    }

    // ---------- Reglas de negocio: cupones ----------

    /**
     * Reglas de descuento (las mismas del frontend):
     *  - DUOC20 / DUOCUC / correo @duocuc.cl o @profesor.duoc.cl → 20%
     *  - PRIDE10 / PRIDE → 10%
     *  - Cualquier otro código: sin descuento (no interrumpe la compra).
     */
    private double porcentajeDescuento(String cupon) {
        if (cupon == null || cupon.isBlank()) {
            return 0;
        }
        String valor = cupon.trim();
        String mayusculas = valor.toUpperCase(Locale.ROOT);

        if (mayusculas.equals("DUOC20") || mayusculas.equals("DUOCUC") || EMAIL_DUOC.matcher(valor).matches()) {
            return 0.20;
        }
        if (mayusculas.equals("PRIDE10") || mayusculas.equals("PRIDE")) {
            return 0.10;
        }
        return 0;
    }

    private String normalizarCupon(String cupon) {
        String valor = cupon.trim();
        if (EMAIL_DUOC.matcher(valor).matches()) {
            return "Convenio Duoc UC";
        }
        return valor.toUpperCase(Locale.ROOT);
    }

    // ---------- Utilidades ----------

    private Boleta buscar(Long id) {
        return boletaRepository.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("Boleta no encontrada con id " + id));
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw ApiException.noAutorizado("Debes iniciar sesión.");
        }
        return usuarioRepository.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> ApiException.noAutorizado("La sesión ya no es válida. Inicia sesión nuevamente."));
    }

    private BoletaResponse toResponse(Boleta boleta) {
        List<DetalleBoletaResponse> detalles = boleta.getDetalles().stream()
                .map(d -> new DetalleBoletaResponse(
                        d.getProducto().getId(),
                        d.getProducto().getNombre(),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        d.getSubtotal()))
                .toList();

        return new BoletaResponse(
                boleta.getId(),
                boleta.getFecha(),
                boleta.getUsuario().getNombre(),
                boleta.getUsuario().getEmail(),
                boleta.getSubtotal(),
                boleta.getDescuento(),
                boleta.getTotal(),
                boleta.getCupon(),
                boleta.getEstado(),
                detalles);
    }
}
