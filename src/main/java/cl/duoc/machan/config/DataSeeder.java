package cl.duoc.machan.config;

import cl.duoc.machan.model.Categoria;
import cl.duoc.machan.model.Producto;
import cl.duoc.machan.model.Rol;
import cl.duoc.machan.model.Usuario;
import cl.duoc.machan.repository.CategoriaRepository;
import cl.duoc.machan.repository.ProductoRepository;
import cl.duoc.machan.repository.UsuarioRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial de datos IDEMPOTENTE: cada registro se inserta solo si no existe
 * (por correo, nombre de categoría o id de producto). Se puede reiniciar la aplicación
 * cuantas veces se quiera sin duplicar datos ni pisar cambios hechos desde el panel.
 *
 *  - Usuarios: admin (ADMIN), vendedor (VENDEDOR) y dos clientes de prueba (CLIENTE).
 *  - Categorías del catálogo.
 *  - Las 8 figuras del catálogo original del frontend (precios en CLP).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String CAT_NENDOROID = "Nendoroid / Chibi";
    private static final String CAT_POP_SP = "POP UP PARADE SP";
    private static final String CAT_POP_L = "POP UP PARADE (Tamaño L)";
    private static final String CAT_ESCALA = "Escala 1/7";
    private static final String CAT_PREMIUM = "Escala Premium 1/7";

    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            UsuarioRepository usuarioRepository,
            CategoriaRepository categoriaRepository,
            ProductoRepository productoRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int usuarios = sembrarUsuarios();
        int categorias = sembrarCategorias();
        int productos = sembrarProductos();
        log.info("DataSeeder: {} usuarios, {} categorías y {} productos nuevos (catálogo total: {}).",
                usuarios, categorias, productos, productoRepository.count());
    }

    // ---------- Usuarios ----------

    private int sembrarUsuarios() {
        int creados = 0;
        creados += crearUsuarioSiNoExiste("Administrador Machan", "admin@machanstore.cl", "admin123", Rol.ADMIN);
        creados += crearUsuarioSiNoExiste("Valentina Vendedora", "vendedor@machanstore.cl", "vendedor123", Rol.VENDEDOR);
        creados += crearUsuarioSiNoExiste("Cliente Invitado", "cliente@machanstore.cl", "cliente123", Rol.CLIENTE);
        creados += crearUsuarioSiNoExiste("Daniela Coleccionista", "daniela@correo.cl", "cliente123", Rol.CLIENTE);
        return creados;
    }

    private int crearUsuarioSiNoExiste(String nombre, String email, String password, Rol rol) {
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            return 0;
        }
        usuarioRepository.save(new Usuario(nombre, email, passwordEncoder.encode(password), rol));
        return 1;
    }

    // ---------- Categorías ----------

    private int sembrarCategorias() {
        int creadas = 0;
        for (String nombre : List.of(CAT_NENDOROID, CAT_POP_SP, CAT_POP_L, CAT_ESCALA, CAT_PREMIUM)) {
            if (!categoriaRepository.existsByNombreIgnoreCase(nombre)) {
                categoriaRepository.save(new Categoria(nombre));
                creadas++;
            }
        }
        return creadas;
    }

    private Categoria cat(String nombre) {
        return categoriaRepository.findByNombreIgnoreCase(nombre)
                .orElseThrow(() -> new IllegalStateException("Categoría faltante: " + nombre));
    }

    // ---------- Productos ----------

    private int sembrarProductos() {
        int creados = 0;
        for (Producto producto : catalogoInicial()) {
            if (!productoRepository.existsById(producto.getId())) {
                productoRepository.save(producto);
                creados++;
            }
        }
        return creados;
    }

    private Producto producto(String id, String nombre, String nombreCorto, long precio, int stock,
                              String imagen, String distribuidor, String categoria, String descripcion) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setNombreCorto(nombreCorto);
        p.setPrecio(precio);
        p.setStock(stock);
        p.setVendidos(0);
        p.setImagen(imagen);
        p.setDistribuidor(distribuidor);
        p.setCategoria(cat(categoria));
        p.setDescripcion(descripcion);
        return p;
    }

    private List<Producto> catalogoInicial() {
        return List.of(
            producto("nendoroid-daiwa-scarlet",
                "Good Smile Company Nendoroid Daiwa Scarlet (Umamusume: Pretty Derby)",
                "Nendoroid Daiwa Scarlet", 73990L, 8, "/images/DaiwaNen.webp", "Good Smile Company",
                CAT_NENDOROID,
                "¡Del juego 'Umamusume: Pretty Derby' llega una figura Nendoroid de Daiwa Scarlet, "
                + "la Umamusume que siempre lucha por alcanzar el primer puesto! Incluye tres expresiones faciales. "
                + "Figura articulada de plástico pintado, sin escala, con soporte incluido. Altura aproximada: 100 mm."),

            producto("pop-up-parade-mihono-bourbon",
                "Good Smile Company POP UP PARADE SP Mihono Bourbon (Umamusume: Pretty Derby)",
                "POP UP PARADE SP Mihono Bourbon", 65990L, 5, "/images/FiguraBourbon.webp", "Good Smile Company",
                CAT_POP_SP,
                "'Objetivo localizado. Comienza la Operación: Triple Corona'. ¡Ya está aquí una figura "
                + "especial de POP UP PARADE! Producto completo de plástico pintado, sin escala, con base incluida. "
                + "Altura aproximada de 250 mm."),

            producto("pop-up-parade-daiwa-scarlet-l",
                "Good Smile Company POP UP PARADE Daiwa Scarlet L Size Figure (Umamusume: Pretty Derby)",
                "POP UP PARADE Daiwa Scarlet L Size", 58990L, 6, "/images/FiguraDaiwa.webp", "Good Smile Company",
                CAT_POP_L,
                "POP UP PARADE Daiwa Scarlet (Tamaño L): ¡con el mismo impacto que una figura a escala! "
                + "Captura a la perfección su dinamismo, doble coleta al viento y carácter enérgico. "
                + "Altura aproximada de 225 mm."),

            producto("pop-up-parade-machan-costume",
                "Good Smile Collection [Unforgettable Sugar Candy] Aston Machan (Umamusume: Pretty Derby)",
                "Aston Machan [Unforgettable Sugar Candy]", 142990L, 3, "/images/FiguraMachanCostume.webp",
                "Good Smile Arts Shanghai", CAT_ESCALA,
                "'¡La dulce y esponjosa Machan, lista para apoyarte!' Del juego 'Umamusume: Pretty Derby' "
                + "llega una figura de Aston Machan luciendo su atuendo del evento de San Valentín. "
                + "Escala 1/7 con base incluida. Aproximadamente 230 mm de altura."),

            producto("alter-tokai-teio-horizon",
                "ALTER [Beyond the Horizon] Tokai Teio 1/7 Figure (Umamusume: Pretty Derby)",
                "ALTER Tokai Teio [Beyond the Horizon]", 217990L, 2, "/images/FiguraTeioAlt.jpg", "ALTER",
                CAT_PREMIUM,
                "Una obra maestra de ingeniería por la prestigiosa casa ALTER. Presenta a Tokai Teio "
                + "saltando llena de vitalidad con su traje de carreras 'Beyond the Horizon', con pintura degradada "
                + "y base con efectos translúcidos. Tamaño: Approx. H250mm (incl. base)."),

            producto("pop-up-parade-fine-motion-l",
                "Good Smile Company POP UP PARADE Fine Motion L Size Figure (Umamusume: Pretty Derby)",
                "POP UP PARADE Fine Motion L Size", 80990L, 4, "/images/FigurFineMo.jpg", "Good Smile Company",
                CAT_POP_L,
                "La noble princesa Fine Motion representada en escala L. Expresa toda su elegancia natural "
                + "y sonrisa amable, luciendo el uniforme de carreras con gran nivel de detalle. "
                + "Altura aproximada de 235 mm."),

            producto("phat-calstone-light-o",
                "Phat! Calstone Light O 1/7 Figure (Umamusume: Pretty Derby)",
                "Phat! Calstone Light O 1/7", 230990L, 3, "/images/FigutaCalstone.jpg", "Phat! Company",
                CAT_ESCALA,
                "'Soy Calstone Light O. Mi sueño es batir el récord de velocidad punta. Me gustan las líneas "
                + "rectas. Eso es todo.' Especialista en velocidad en pose cargada de aceleración, fabricada por "
                + "Phat! Company. Escala 1/7. Aproximadamente 260 mm de altura."),

            producto("nendoroid-silence-suzuka",
                "Good Smile Company Nendoroid Silence Suzuka Figure (Umamusume: Pretty Derby)",
                "Nendoroid Silence Suzuka", 63990L, 7, "/images/SuzukaNen.jpg", "Good Smile Company",
                CAT_NENDOROID,
                "La velocista silenciosa Silence Suzuka en su versión Nendoroid. Incluye expresiones "
                + "faciales, piernas intercambiables de carrera y su placa de valla de pista de césped. "
                + "Aproximadamente 100 mm de altura."));
    }
}
