package cl.duoc.machan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.machan.model.Producto;
import cl.duoc.machan.repository.CategoriaRepository;
import cl.duoc.machan.repository.ProductoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración de la API REST (contexto Spring completo + H2 en memoria + MockMvc).
 *
 * Cubre los flujos críticos:
 *  1. Registro y login con obtención de un token JWT válido.
 *  2. Acceso denegado (401 sin token / 403 con rol insuficiente) a rutas de ADMIN.
 *  3. CRUD de productos (v1 y v2) y actualización de stock con rol ADMIN.
 *  4. Compra (boleta): descuento real de stock en la base de datos, rechazo por stock
 *     insuficiente con rollback completo, cancelación con reposición de stock.
 *  5. Manejo de errores JSON (400, 404, 409), CORS y Swagger.
 *
 * Cada prueba usa productos/correos propios para no depender del orden de ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String ORIGEN_FRONT = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private static String tokenAdmin;
    private static String tokenCliente;
    private static String tokenVendedor;

    // ===================== Utilidades =====================

    private String adminToken() throws Exception {
        if (tokenAdmin == null) {
            tokenAdmin = login("admin@machanstore.cl", "admin123");
        }
        return tokenAdmin;
    }

    private String clienteToken() throws Exception {
        if (tokenCliente == null) {
            tokenCliente = login("cliente@machanstore.cl", "cliente123");
        }
        return tokenCliente;
    }

    private String vendedorToken() throws Exception {
        if (tokenVendedor == null) {
            tokenVendedor = login("vendedor@machanstore.cl", "vendedor123");
        }
        return tokenVendedor;
    }

    private String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.usuario.rol").isNotEmpty())
                .andReturn();
        return json(result).get("token").asText();
    }

    private String registrarYObtenerToken(String nombre, String email, String password) throws Exception {
        String body = "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int stockEnBd(String productoId) {
        return productoRepository.findById(productoId).map(Producto::getStock).orElseThrow();
    }

    private int vendidosEnBd(String productoId) {
        return productoRepository.findById(productoId).map(Producto::getVendidos).orElseThrow();
    }

    // ===================== 1. Autenticación JWT =====================

    @Nested
    @DisplayName("1. Registro y login con JWT")
    class Autenticacion {

        @Test
        @DisplayName("Login de ADMIN devuelve token y rol ADMIN")
        void loginAdminDevuelveTokenYRol() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@machanstore.cl\",\"password\":\"admin123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.expiresInMs").isNumber())
                    .andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
                    .andExpect(jsonPath("$.usuario.password").doesNotExist());
        }

        @Test
        @DisplayName("Registro crea CLIENTE, el token sirve para /me y para volver a loguearse")
        void registroCreaClienteYTokenEsValido() throws Exception {
            String token = registrarYObtenerToken("Nuevo Cliente", "nuevo@correo.cl", "secreto1");

            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("nuevo@correo.cl"))
                    .andExpect(jsonPath("$.rol").value("CLIENTE"));

            login("nuevo@correo.cl", "secreto1");
        }

        @Test
        @DisplayName("El registro ignora cualquier intento de autoasignarse rol ADMIN")
        void registroNoPermiteEscalarRol() throws Exception {
            mockMvc.perform(post("/api/v1/auth/registro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Pillo\",\"email\":\"pillo@correo.cl\","
                                    + "\"password\":\"secreto1\",\"rol\":\"ADMIN\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.usuario.rol").value("CLIENTE"));
        }

        @Test
        @DisplayName("Renovar token emite uno nuevo válido")
        void refreshEmiteNuevoToken() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn();

            String nuevo = json(result).get("token").asText();
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(nuevo)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Password incorrecto responde 401 JSON")
        void loginConPasswordIncorrectoResponde401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@machanstore.cl\",\"password\":\"mala\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }

        @Test
        @DisplayName("Correo repetido responde 409")
        void registroCorreoRepetidoResponde409() throws Exception {
            mockMvc.perform(post("/api/v1/auth/registro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Otro\",\"email\":\"cliente@machanstore.cl\",\"password\":\"secreto1\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("Datos de registro inválidos responden 400 con detalle por campo")
        void registroInvalidoResponde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/registro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"\",\"email\":\"no-es-correo\",\"password\":\"123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.email").isNotEmpty())
                    .andExpect(jsonPath("$.errores.password").isNotEmpty())
                    .andExpect(jsonPath("$.errores.nombre").isNotEmpty());
        }

        @Test
        @DisplayName("/me sin token o con token adulterado responde 401")
        void endpointMeRequiereTokenValido() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken() + "x")))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("admin@machanstore.cl"));
        }
    }

    // ===================== 2. Control de acceso por rol =====================

    @Nested
    @DisplayName("2. Acceso denegado a rutas de ADMIN")
    class ControlDeAcceso {

        @Test
        @DisplayName("Crear producto sin token: 401")
        void crearProductoSinTokenResponde401() throws Exception {
            mockMvc.perform(post("/api/v1/productos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"test-x\",\"nombre\":\"Test\",\"precio\":100,\"stock\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Crear producto como CLIENTE: 403")
        void crearProductoComoClienteResponde403() throws Exception {
            mockMvc.perform(post("/api/v1/productos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"test-x\",\"nombre\":\"Test\",\"precio\":100,\"stock\":1}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("PATCH de stock como CLIENTE o VENDEDOR: 403; sin token: 401")
        void patchStockSoloAdmin() throws Exception {
            String body = "{\"stock\":999}";
            mockMvc.perform(patch("/api/v1/productos/nendoroid-daiwa-scarlet/stock")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch("/api/v1/productos/nendoroid-daiwa-scarlet/stock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v2/productos/nendoroid-daiwa-scarlet/stock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(vendedorToken()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());

            assertThat(stockEnBd("nendoroid-daiwa-scarlet")).isNotEqualTo(999);
        }

        @Test
        @DisplayName("Eliminar producto como CLIENTE: 403")
        void eliminarProductoComoClienteResponde403() throws Exception {
            mockMvc.perform(delete("/api/v1/productos/nendoroid-daiwa-scarlet")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken())))
                    .andExpect(status().isForbidden());
            assertThat(productoRepository.existsById("nendoroid-daiwa-scarlet")).isTrue();
        }

        @Test
        @DisplayName("Gestionar categorías como CLIENTE: 403")
        void categoriasEscrituraSoloAdmin() throws Exception {
            mockMvc.perform(post("/api/v1/categorias")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Hackeada\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Usuarios: 401 sin token, 403 como CLIENTE y VENDEDOR, 200 como ADMIN")
        void listadoUsuariosSoloAdmin() throws Exception {
            mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(clienteToken())))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(vendedorToken())))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].password").doesNotExist());
        }

        @Test
        @DisplayName("Cambiar estado de boleta como CLIENTE: 403")
        void cambiarEstadoBoletaSoloAdmin() throws Exception {
            mockMvc.perform(patch("/api/v1/boletas/1/estado")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"CANCELADA\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Catálogo y categorías son públicos")
        void catalogoEsPublico() throws Exception {
            mockMvc.perform(get("/api/v1/productos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].nombre").isNotEmpty());

            mockMvc.perform(get("/api/v1/categorias"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
        }
    }

    // ===================== 3. CRUD de productos (ADMIN) =====================

    @Nested
    @DisplayName("3. CRUD de productos y stock como ADMIN")
    class CrudProductos {

        @Test
        @DisplayName("v1: crear, leer, actualizar, PATCH stock, PUT stock y eliminar")
        void crudProductosV1ComoAdmin() throws Exception {
            mockMvc.perform(post("/api/v1/productos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"figura-test\",\"nombre\":\"Figura Test\",\"nombreCorto\":\"Test\","
                                    + "\"precio\":15990,\"stock\":5,\"imagen\":\"/images/x.webp\","
                                    + "\"distribuidor\":\"Marca\",\"descripcion\":\"Desc\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/productos/figura-test"))
                    .andExpect(jsonPath("$.id").value("figura-test"));

            mockMvc.perform(get("/api/v1/productos/figura-test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.precio").value(15990));

            mockMvc.perform(put("/api/v1/productos/figura-test")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"figura-test\",\"nombre\":\"Figura Editada\",\"precio\":19990}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Figura Editada"))
                    .andExpect(jsonPath("$.precio").value(19990))
                    .andExpect(jsonPath("$.stock").value(5));

            mockMvc.perform(patch("/api/v1/productos/figura-test/stock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stock\":12}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").value(12));
            assertThat(stockEnBd("figura-test")).isEqualTo(12);

            mockMvc.perform(put("/api/v1/productos/figura-test/stock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stock\":20}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").value(20));

            mockMvc.perform(delete("/api/v1/productos/figura-test")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isNoContent());

            assertThat(productoRepository.existsById("figura-test")).isFalse();
            mockMvc.perform(get("/api/v1/productos/figura-test")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("v2: crear con categoría, leer estadísticas, PATCH stock y eliminar")
        void crudProductosV2ComoAdmin() throws Exception {
            Long categoriaId = categoriaRepository.findByNombreIgnoreCase("Escala 1/7").orElseThrow().getId();

            mockMvc.perform(post("/api/v2/productos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"figura-v2\",\"nombre\":\"Figura V2\",\"precio\":99990,"
                                    + "\"stock\":4,\"categoriaId\":" + categoriaId + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.categoria").value("Escala 1/7"))
                    .andExpect(jsonPath("$.vendidos").value(0));

            mockMvc.perform(patch("/api/v2/productos/figura-v2/stock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stock\":7}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").value(7))
                    .andExpect(jsonPath("$.categoria").value("Escala 1/7"));

            mockMvc.perform(get("/api/v2/productos").param("categoriaId", String.valueOf(categoriaId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == 'figura-v2')]").exists());

            mockMvc.perform(delete("/api/v2/productos/figura-v2")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("v2 sin categoría responde 400; id duplicado responde 409")
        void validacionesDeCreacion() throws Exception {
            mockMvc.perform(post("/api/v2/productos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"sin-cat\",\"nombre\":\"Sin categoría\",\"precio\":1000,\"stock\":1}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(post("/api/v1/productos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"nendoroid-daiwa-scarlet\",\"nombre\":\"Duplicado\",\"precio\":1,\"stock\":1}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Stock negativo responde 400 con detalle del campo")
        void stockNegativoResponde400() throws Exception {
            mockMvc.perform(patch("/api/v1/productos/nendoroid-daiwa-scarlet/stock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stock\":-3}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.stock").isNotEmpty());
        }

        @Test
        @DisplayName("Detalle v2 incluye estadísticas y categoría")
        void detalleProductoV2IncluyeEstadisticas() throws Exception {
            mockMvc.perform(get("/api/v2/productos/nendoroid-daiwa-scarlet"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vendidos").isNumber())
                    .andExpect(jsonPath("$.categoria").value("Nendoroid / Chibi"));
        }

        @Test
        @DisplayName("Producto inexistente responde 404 JSON")
        void productoInexistenteResponde404() throws Exception {
            mockMvc.perform(get("/api/v1/productos/no-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.path").value("/api/v1/productos/no-existe"));
        }
    }

    // ===================== 4. Boletas y stock =====================

    @Nested
    @DisplayName("4. Compras: validación y descuento de stock")
    class Boletas {

        @Test
        @DisplayName("Compra válida: calcula totales en el servidor y descuenta stock en la BD")
        void compraValidaCreaBoletaYDescuentaStock() throws Exception {
            String id = "nendoroid-silence-suzuka";
            int stockAntes = stockEnBd(id);
            int vendidosAntes = vendidosEnBd(id);

            String body = "{\"items\":[{\"productoId\":\"" + id + "\",\"cantidad\":2}],\"cupon\":\"DUOC20\"}";

            MvcResult result = mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subtotal").value(63990 * 2))
                    .andExpect(jsonPath("$.descuento").value((int) Math.round(63990 * 2 * 0.20)))
                    .andExpect(jsonPath("$.total").value((int) Math.round(63990 * 2 * 0.80)))
                    .andExpect(jsonPath("$.cupon").value("DUOC20"))
                    .andExpect(jsonPath("$.estado").value("PAGADA"))
                    .andExpect(jsonPath("$.detalles[0].cantidad").value(2))
                    .andExpect(jsonPath("$.detalles[0].precioUnitario").value(63990))
                    .andReturn();

            // Verificación directa en la base de datos
            assertThat(stockEnBd(id)).isEqualTo(stockAntes - 2);
            assertThat(vendidosEnBd(id)).isEqualTo(vendidosAntes + 2);

            // Y a través de la API pública
            mockMvc.perform(get("/api/v1/productos/" + id))
                    .andExpect(jsonPath("$.stock").value(stockAntes - 2));

            long boletaId = json(result).get("id").asLong();
            mockMvc.perform(get("/api/v1/boletas/" + boletaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailCliente").value("cliente@machanstore.cl"));

            mockMvc.perform(get("/api/v1/boletas/mis-boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + boletaId + ")]").exists());
        }

        @Test
        @DisplayName("El precio lo define el servidor y líneas repetidas se agrupan")
        void lineasRepetidasSeAgrupan() throws Exception {
            String id = "pop-up-parade-daiwa-scarlet-l";
            int stockAntes = stockEnBd(id);

            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"" + id + "\",\"cantidad\":1,\"precio\":1},"
                                    + "{\"productoId\":\"" + id + "\",\"cantidad\":1}]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.detalles.length()").value(1))
                    .andExpect(jsonPath("$.detalles[0].cantidad").value(2))
                    .andExpect(jsonPath("$.total").value(58990 * 2));

            assertThat(stockEnBd(id)).isEqualTo(stockAntes - 2);
        }

        @Test
        @DisplayName("Stock insuficiente: 400 y NO se descuenta nada (rollback de toda la compra)")
        void compraConStockInsuficienteHaceRollback() throws Exception {
            int daiwaAntes = stockEnBd("nendoroid-daiwa-scarlet");
            int teioAntes = stockEnBd("alter-tokai-teio-horizon");

            String body = "{\"items\":["
                    + "{\"productoId\":\"nendoroid-daiwa-scarlet\",\"cantidad\":1},"
                    + "{\"productoId\":\"alter-tokai-teio-horizon\",\"cantidad\":" + (teioAntes + 1) + "}]}";

            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Stock insuficiente")));

            assertThat(stockEnBd("nendoroid-daiwa-scarlet")).isEqualTo(daiwaAntes);
            assertThat(stockEnBd("alter-tokai-teio-horizon")).isEqualTo(teioAntes);
        }

        @Test
        @DisplayName("Producto inexistente en el carrito: 404; carrito vacío: 400")
        void carritoInvalido() throws Exception {
            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"no-existe\",\"cantidad\":1}]}"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[]}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"nendoroid-daiwa-scarlet\",\"cantidad\":0}]}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ADMIN cancela una boleta: se repone el stock; cancelar dos veces no se permite")
        void cancelarBoletaReponeStock() throws Exception {
            String id = "pop-up-parade-machan-costume";
            int stockAntes = stockEnBd(id);

            MvcResult result = mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"" + id + "\",\"cantidad\":1}]}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            long boletaId = json(result).get("id").asLong();
            assertThat(stockEnBd(id)).isEqualTo(stockAntes - 1);

            mockMvc.perform(patch("/api/v1/boletas/" + boletaId + "/estado")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"CANCELADA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"));
            assertThat(stockEnBd(id)).isEqualTo(stockAntes);

            mockMvc.perform(patch("/api/v1/boletas/" + boletaId + "/estado")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"PAGADA\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Un cliente no puede ver la boleta de otro cliente (403)")
        void clienteNoVeBoletaAjena() throws Exception {
            String otroToken = registrarYObtenerToken("Otra Clienta", "otra@correo.cl", "secreto1");
            MvcResult result = mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(otroToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"phat-calstone-light-o\",\"cantidad\":1}]}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            long boletaId = json(result).get("id").asLong();

            mockMvc.perform(get("/api/v1/boletas/" + boletaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken())))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/boletas/" + boletaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(vendedorToken())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Compra y listado de boletas sin token: 401")
        void boletasRequierenAutenticacion() throws Exception {
            mockMvc.perform(post("/api/v1/boletas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"nendoroid-daiwa-scarlet\",\"cantidad\":1}]}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/boletas")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("No se elimina un producto que ya fue vendido (409)")
        void productoVendidoNoSePuedeEliminar() throws Exception {
            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(clienteToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"pop-up-parade-fine-motion-l\",\"cantidad\":1}]}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(delete("/api/v1/productos/pop-up-parade-fine-motion-l")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isConflict());
            assertThat(productoRepository.existsById("pop-up-parade-fine-motion-l")).isTrue();
        }
    }

    // ===================== Usuarios y categorías (ADMIN) =====================

    @Nested
    @DisplayName("Administración de usuarios y categorías")
    class Administracion {

        @Test
        @DisplayName("ADMIN crea, edita, cambia rol (PATCH) y elimina usuarios")
        void crudUsuarios() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Vendedor Nuevo\",\"email\":\"v2@machanstore.cl\","
                                    + "\"password\":\"clave123\",\"rol\":\"VENDEDOR\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rol").value("VENDEDOR"))
                    .andReturn();
            long id = json(result).get("id").asLong();

            mockMvc.perform(put("/api/v1/usuarios/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Vendedor Editado\",\"email\":\"v2@machanstore.cl\",\"rol\":\"VENDEDOR\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Vendedor Editado"));

            // La contraseña se mantuvo al omitirla en el PUT
            login("v2@machanstore.cl", "clave123");

            mockMvc.perform(patch("/api/v1/usuarios/" + id + "/rol")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rol\":\"CLIENTE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value("CLIENTE"));

            mockMvc.perform(delete("/api/v1/usuarios/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/usuarios/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("El ADMIN no puede eliminar su propia cuenta activa ni quitarse el rol")
        void adminNoPuedeEliminarseNiDegradarse() throws Exception {
            MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andReturn();
            long adminId = json(me).get("id").asLong();

            mockMvc.perform(delete("/api/v1/usuarios/" + adminId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());

            mockMvc.perform(patch("/api/v1/usuarios/" + adminId + "/rol")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rol\":\"CLIENTE\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(jsonPath("$.rol").value("ADMIN"));
        }

        @Test
        @DisplayName("No se elimina un usuario con boletas (409)")
        void usuarioConBoletasNoSeElimina() throws Exception {
            String token = registrarYObtenerToken("Compradora", "compradora@correo.cl", "secreto1");
            mockMvc.perform(post("/api/v1/boletas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productoId\":\"pop-up-parade-mihono-bourbon\",\"cantidad\":1}]}"))
                    .andExpect(status().isCreated());

            MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andReturn();
            long id = json(me).get("id").asLong();

            mockMvc.perform(delete("/api/v1/usuarios/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Categorías: CRUD como ADMIN, 409 por duplicado o por tener productos")
        void crudCategorias() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/categorias")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Figma\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            long id = json(result).get("id").asLong();

            mockMvc.perform(post("/api/v1/categorias")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"figma\"}"))
                    .andExpect(status().isConflict());

            mockMvc.perform(patch("/api/v1/categorias/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Figma Articuladas\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Figma Articuladas"));

            mockMvc.perform(delete("/api/v1/categorias/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isNoContent());

            Long conProductos = categoriaRepository.findByNombreIgnoreCase("Nendoroid / Chibi").orElseThrow().getId();
            mockMvc.perform(delete("/api/v1/categorias/" + conProductos)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isConflict());
        }
    }

    // ===================== Errores, CORS y Swagger =====================

    @Nested
    @DisplayName("Errores JSON, CORS y Swagger")
    class Infraestructura {

        @Test
        @DisplayName("JSON mal formado responde 400 (no 500)")
        void jsonMalFormadoResponde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{esto no es json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Ruta inexistente responde 404 JSON")
        void rutaInexistenteResponde404() throws Exception {
            mockMvc.perform(get("/no-existe-esta-ruta"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Preflight CORS desde el frontend React permite PATCH y Authorization")
        void preflightCorsPermitido() throws Exception {
            mockMvc.perform(options("/api/v1/productos/nendoroid-daiwa-scarlet/stock")
                            .header(HttpHeaders.ORIGIN, ORIGEN_FRONT)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_FRONT))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            org.hamcrest.Matchers.containsString("PATCH")));
        }

        @Test
        @DisplayName("CORS rechaza orígenes no configurados")
        void corsRechazaOrigenDesconocido() throws Exception {
            mockMvc.perform(options("/api/v1/productos")
                            .header(HttpHeaders.ORIGIN, "http://sitio-malicioso.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Swagger/OpenAPI disponible sin token y con esquema bearerAuth")
        void swaggerDisponibleSinToken() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths").isNotEmpty())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.paths['/api/v1/productos/{id}/stock'].patch").exists());
        }
    }
}
