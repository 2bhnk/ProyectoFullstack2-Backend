package cl.duoc.machan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Figura de colección del catálogo.
 * Relación: PRODUCTO (N) ---> (1) CATEGORIA
 */
@Entity
@Table(name = "productos")
public class Producto {

    /** Identificador slug del producto (ej: "nendoroid-daiwa-scarlet"). */
    @Id
    @Column(length = 100)
    private String id;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @Column(name = "nombre_corto")
    private String nombreCorto;

    /** Precio en pesos chilenos (CLP). */
    @NotNull(message = "El precio es obligatorio")
    @Min(value = 0, message = "El precio no puede ser negativo")
    @Column(nullable = false)
    private Long precio;

    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    @Column(nullable = false)
    private Integer stock = 0;

    /** Unidades vendidas (estadística para la versión v2 de la API). */
    @Min(value = 0, message = "Las unidades vendidas no pueden ser negativas")
    @Column(nullable = false)
    private Integer vendidos = 0;

    private String imagen;

    private String distribuidor;

    /** Copia desnormalizada del nombre de la categoría (para lectura rápida). */
    private String categoriaNombre;

    @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
    @Column(length = 4000)
    private String descripcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    public Producto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombreCorto() {
        return nombreCorto;
    }

    public void setNombreCorto(String nombreCorto) {
        this.nombreCorto = nombreCorto;
    }

    public Long getPrecio() {
        return precio;
    }

    public void setPrecio(Long precio) {
        this.precio = precio;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getVendidos() {
        return vendidos;
    }

    public void setVendidos(Integer vendidos) {
        this.vendidos = vendidos;
    }

    public String getImagen() {
        return imagen;
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    public String getDistribuidor() {
        return distribuidor;
    }

    public void setDistribuidor(String distribuidor) {
        this.distribuidor = distribuidor;
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }

    public void setCategoriaNombre(String categoriaNombre) {
        this.categoriaNombre = categoriaNombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
        this.categoriaNombre = categoria != null ? categoria.getNombre() : null;
    }
}
