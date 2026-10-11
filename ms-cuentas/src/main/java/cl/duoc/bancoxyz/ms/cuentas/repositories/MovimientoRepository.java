package cl.duoc.bancoxyz.ms.cuentas.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.bancoxyz.ms.cuentas.entities.MovimientoEntity;

public interface MovimientoRepository extends JpaRepository<MovimientoEntity, Long> {

    List<MovimientoEntity> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId);
}
