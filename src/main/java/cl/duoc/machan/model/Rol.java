package cl.duoc.machan.model;

/**
 * Roles del sistema según requerimiento del cliente:
 * - ADMIN: acceso total al sistema.
 * - VENDEDOR: visualiza productos (lista y detalle) y órdenes (lista y detalle).
 * - CLIENTE: solo accede a la tienda.
 */
public enum Rol {
    ADMIN,
    VENDEDOR,
    CLIENTE
}
