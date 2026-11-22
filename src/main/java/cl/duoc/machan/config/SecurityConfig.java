package cl.duoc.machan.config;

import cl.duoc.machan.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de seguridad global (stateless + JWT).
 *
 * Reglas de autorización por rol:
 *  - Público:   login, registro, catálogo (GET productos/categorías) y Swagger.
 *  - CLIENTE:   comprar (crear boleta) y ver su propio historial.
 *  - VENDEDOR:  lectura de productos y de todas las órdenes.
 *  - ADMIN:     acceso total (CRUD productos, stock, categorías, usuarios y estado de boletas).
 *
 * La autorización se aplica en dos capas: por URL aquí y con @PreAuthorize en cada controlador.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] RUTAS_SWAGGER = {
        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Orígenes del frontend permitidos, separados por coma (configurable con ALLOWED_ORIGINS). */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                // --- Preflight CORS ---
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // --- Público: autenticación ---
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro").permitAll()

                // --- Público: documentación Swagger y consola H2 (solo perfil dev) ---
                .requestMatchers(RUTAS_SWAGGER).permitAll()
                .requestMatchers("/h2-console/**").permitAll()

                // --- Público: catálogo (la tienda es accesible sin sesión) ---
                .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v2/productos/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categorias/**").permitAll()

                // --- Escritura de productos y stock: solo ADMIN ---
                .requestMatchers(HttpMethod.POST, "/api/v*/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v*/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v*/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v*/productos/**").hasRole("ADMIN")

                // --- Categorías: escritura solo ADMIN ---
                .requestMatchers(HttpMethod.POST, "/api/v*/categorias/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v*/categorias/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v*/categorias/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v*/categorias/**").hasRole("ADMIN")

                // --- Gestión de usuarios: solo ADMIN ---
                .requestMatchers("/api/v*/usuarios/**").hasRole("ADMIN")

                // --- Boletas: comprar y ver historial (autenticados); cambiar estado solo ADMIN ---
                .requestMatchers(HttpMethod.PATCH, "/api/v*/boletas/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v*/boletas/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v*/boletas/**").hasAnyRole("CLIENTE", "ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.GET, "/api/v*/boletas/**").authenticated()

                // --- Perfil propio y renovación de token ---
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/refresh").authenticated()

                // --- Cualquier otra ruta de la API requiere autenticación ---
                .requestMatchers("/api/**").authenticated()

                // --- Todo lo demás permitido (404 controlado por GlobalExceptionHandler) ---
                .anyRequest().permitAll()
            )
            // Respuestas JSON: 401 sin token o token inválido; 403 sin permiso suficiente
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        escribirError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                "Debes iniciar sesión para acceder a este recurso."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        escribirError(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                "No tienes permisos para realizar esta acción.")))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void escribirError(HttpServletRequest request, HttpServletResponse response,
                               int status, String error, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":" + status
                + ",\"error\":\"" + error
                + "\",\"mensaje\":\"" + mensaje
                + "\",\"path\":\"" + request.getRequestURI() + "\"}");
    }

    /**
     * CORS explícito para el frontend React (Vite :5173 / CRA :3000 por defecto).
     * Los orígenes se configuran con la propiedad app.cors.allowed-origins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization", "Location"));
        // El token viaja en el encabezado Authorization, no en cookies.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/v3/api-docs/**", config);
        return source;
    }

    /** Hash de contraseñas con BCrypt (factor de costo por defecto 10). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
