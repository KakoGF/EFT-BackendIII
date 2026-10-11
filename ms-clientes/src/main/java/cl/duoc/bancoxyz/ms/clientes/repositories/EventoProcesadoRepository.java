package cl.duoc.bancoxyz.ms.clientes.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.bancoxyz.ms.clientes.entities.EventoProcesadoEntity;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesadoEntity, String> {
}
