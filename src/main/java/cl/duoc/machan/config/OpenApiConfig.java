package cl.duoc.machan.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI/Swagger.
 *
 * Define el esquema de seguridad "bearerAuth" (JWT). Los endpoints protegidos lo declaran con
 * {@code @SecurityRequirement(name = "bearerAuth")}, por lo que en Swagger UI aparecen con candado:
 * se pulsa "Authorize", se pega el token devuelto por /api/v1/auth/login y se prueban directamente.
 *
 * Swagger UI: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "bearerAuth";

    @Value("${server.port:8080}")
    private String puerto;

    @Bean
    public OpenAPI machanOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MachanStore API")
                        .version("1.0.0")
                        .description(
                            "API REST de la tienda online **MachanStore** (figuras de colección Umamusume).\n\n"
                            + "**Roles:**\n"
                            + "- `ADMIN`: acceso total (productos, stock, categorías, usuarios, estado de boletas).\n"
                            + "- `VENDEDOR`: lectura de productos y de todas las órdenes.\n"
                            + "- `CLIENTE`: catálogo, compras y su propio historial.\n\n"
                            + "**Cómo probar endpoints protegidos:**\n"
                            + "1. Ejecuta `POST /api/v1/auth/login` (ej. `admin@machanstore.cl` / `admin123`).\n"
                            + "2. Copia el campo `token` de la respuesta.\n"
                            + "3. Pulsa **Authorize** y pégalo (sin el prefijo `Bearer`).\n\n"
                            + "Todos los errores responden JSON con `status`, `error`, `mensaje` y `path`.")
                        .contact(new Contact().name("Equipo Pride - Desarrollo Fullstack II")
                                .email("equipo@machanstore.cl")))
                .servers(List.of(new Server().url("http://localhost:" + puerto).description("Servidor local")))
                .tags(List.of(
                        new Tag().name("Autenticación").description("Registro, login, sesión y renovación de JWT"),
                        new Tag().name("Productos v1").description("Catálogo con datos básicos"),
                        new Tag().name("Productos v2").description("Catálogo con estadísticas y categoría"),
                        new Tag().name("Categorías").description("Categorías del catálogo"),
                        new Tag().name("Boletas / Órdenes").description("Compras y su historial"),
                        new Tag().name("Usuarios").description("Administración de usuarios (solo ADMIN)")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Pega el token JWT devuelto por POST /api/v1/auth/login")));
    }
}
