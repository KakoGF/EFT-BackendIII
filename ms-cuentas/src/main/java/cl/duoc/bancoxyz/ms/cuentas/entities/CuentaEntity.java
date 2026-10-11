package cl.duoc.bancoxyz.ms.cuentas.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cuenta")
@Data
@NoArgsConstructor
public class CuentaEntity {

    @Id
    private Long cuentaId;

    private Long clienteId;

    @Column(length = 150)
    private String titular;

    @Column(length = 20)
    private String tipo;

    @Column(precision = 15, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoCuenta estado;

    private LocalDate fechaApertura;
    private LocalDate fechaCierre;

    @Column(length = 200)
    private String motivoEstado;

    @Column(length = 20)
    private String origen;

    @Version
    private Long version;
}
