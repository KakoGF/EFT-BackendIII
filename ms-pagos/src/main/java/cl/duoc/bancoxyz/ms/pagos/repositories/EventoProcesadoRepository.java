package cl.duoc.bancoxyz.ms.pagos.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.bancoxyz.ms.pagos.entities.EventoProcesadoEntity;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesadoEntity, String> {
}
