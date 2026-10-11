package cl.duoc.bancoxyz.ms.pagos.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transaccion")
@Data
@NoArgsConstructor
public class TransaccionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String solicitudId;

    @Column(length = 20)
    private String tipo;

    private Long cuentaOrigenId;
    private Long cuentaDestinoId;
    private Long clienteId;

    @Column(length = 150)
    private String titular;

    @Column(length = 60)
    private String referenciaPago;

    @Column(precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(length = 20)
    private String canal;

    @Column(length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoTransaccion estado;

    @Column(length = 300)
    private String motivo;

    @Column(length = 80)
    private String origen;

    @Column(length = 80)
    private String validadoPor;

    @Column(precision = 15, scale = 2)
    private BigDecimal saldoFinal;

    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaResolucion;
}
