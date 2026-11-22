package cl.duoc.machan.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Boleta: registro de una compra realizada por un usuario.
 * Relación: USUARIO (1) ---> (N) BOLETA (1) ---> (N) DETALLE_BOLETA
 */
@Entity
@Table(name = "boletas")
public class Boleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Subtotal antes de descuentos (CLP). */
    @Min(0)
    @Column(nullable = false)
    private Long subtotal = 0L;

    /** Monto descontado por cupón (CLP). */
    @Min(0)
    @Column(nullable = false)
    private Long descuento = 0L;

    /** Total final pagado (CLP). */
    @Min(0)
    @Column(nullable = false)
    private Long total = 0L;

    /** Código de cupón aplicado (puede ser nulo). */
    private String cupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoBoleta estado = EstadoBoleta.PAGADA;

    @OneToMany(mappedBy = "boleta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DetalleBoleta> detalles = new ArrayList<>();

    public Boleta() {
    }

    public void agregarDetalle(DetalleBoleta detalle) {
        detalle.setBoleta(this);
        detalles.add(detalle);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public Long getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(Long subtotal) {
        this.subtotal = subtotal;
    }

    public Long getDescuento() {
        return descuento;
    }

    public void setDescuento(Long descuento) {
        this.descuento = descuento;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public String getCupon() {
        return cupon;
    }

    public void setCupon(String cupon) {
        this.cupon = cupon;
    }

    public EstadoBoleta getEstado() {
        return estado;
    }

    public void setEstado(EstadoBoleta estado) {
        this.estado = estado;
    }

    public List<DetalleBoleta> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleBoleta> detalles) {
        this.detalles = detalles;
    }
}
