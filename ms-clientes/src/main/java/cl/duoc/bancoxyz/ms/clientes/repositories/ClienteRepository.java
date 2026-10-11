package cl.duoc.bancoxyz.ms.clientes.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.bancoxyz.ms.clientes.entities.ClienteEntity;

public interface ClienteRepository extends JpaRepository<ClienteEntity, Long> {

    List<ClienteEntity> findAllByOrderByClienteIdAsc();

    Optional<ClienteEntity> findTopByOrderByClienteIdDesc();

    boolean existsByRut(String rut);
}
