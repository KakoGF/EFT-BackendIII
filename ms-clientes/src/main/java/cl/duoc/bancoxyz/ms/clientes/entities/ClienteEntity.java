package cl.duoc.bancoxyz.ms.clientes.entities;

import java.time.LocalDateTime;

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
@Table(name = "cliente")
@Data
@NoArgsConstructor
public class ClienteEntity {

    @Id
    private Long clienteId;

    @Column(unique = true, length = 12)
    private String rut;

    @Column(length = 150)
    private String nombre;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(length = 200)
    private String direccion;

    private Integer edad;

    @Column(length = 20)
    private String segmento;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NivelRiesgo nivelRiesgo;

    private Integer alertasSeguridad;

    @Column(length = 300)
    private String ultimaAlerta;

    @Column(length = 20)
    private String origen;

    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;

    @Version
    private Long version;
}
