package cl.duoc.bancoxyz.ms.cuentas.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.bancoxyz.ms.cuentas.entities.CuentaEntity;

public interface CuentaRepository extends JpaRepository<CuentaEntity, Long> {

    List<CuentaEntity> findAllByOrderByCuentaIdAsc();

    List<CuentaEntity> findByClienteIdOrderByCuentaIdAsc(Long clienteId);

    Optional<CuentaEntity> findTopByOrderByCuentaIdDesc();
}
