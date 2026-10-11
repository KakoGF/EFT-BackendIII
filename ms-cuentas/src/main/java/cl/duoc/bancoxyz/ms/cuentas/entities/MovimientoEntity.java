package cl.duoc.bancoxyz.ms.cuentas.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movimiento", indexes = @Index(name = "idx_movimiento_cuenta", columnList = "cuentaId"))
@Data
@NoArgsConstructor
public class MovimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long cuentaId;
    private LocalDate fecha;

    @Column(length = 30)
    private String tipoMovimiento;

    @Column(precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(length = 300)
    private String descripcion;

    @Column(length = 20)
    private String canal;

    @Column(length = 64)
    private String solicitudId;

    private LocalDateTime fechaRegistro;
}
