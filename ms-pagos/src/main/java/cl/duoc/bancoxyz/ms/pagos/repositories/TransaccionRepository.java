package cl.duoc.bancoxyz.ms.pagos.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.bancoxyz.ms.pagos.entities.TransaccionEntity;

public interface TransaccionRepository extends JpaRepository<TransaccionEntity, Long> {

    Optional<TransaccionEntity> findBySolicitudId(String solicitudId);

    List<TransaccionEntity> findByCuentaOrigenIdOrCuentaDestinoIdOrderByIdDesc(Long cuentaOrigenId, Long cuentaDestinoId);
}
