package cl.duoc.bancoxyz.bff.web.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.duoc.bancoxyz.bff.web.dtos.core.ClienteCoreDTO;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MsClientesClient {

    private final RestClient backendRestClient;
    private final ServicioResolver servicioResolver;
    private final String serviceId;

    public MsClientesClient(
            RestClient backendRestClient,
            ServicioResolver servicioResolver,
            @Value("${ms-clientes.service-id}") String serviceId) {
        this.backendRestClient = backendRestClient;
        this.servicioResolver = servicioResolver;
        this.serviceId = serviceId;
    }

    @CircuitBreaker(name = "msClientes", fallbackMethod = "obtenerClienteFallback")
    public ClienteCoreDTO obtenerCliente(Long clienteId) {
        return backendRestClient.get()
                .uri(servicioResolver.urlBase(serviceId) + "/clientes/{id}", clienteId)
                .retrieve()
                .body(ClienteCoreDTO.class);
    }

    @SuppressWarnings("unused")
    private ClienteCoreDTO obtenerClienteFallback(Long clienteId, Throwable error) {
        log.warn("[FALLBACK] No se pudo obtener el perfil del cliente {}: la cuenta se muestra sin ese bloque. Causa: {}",
                clienteId, error.toString());
        return null;
    }
}
