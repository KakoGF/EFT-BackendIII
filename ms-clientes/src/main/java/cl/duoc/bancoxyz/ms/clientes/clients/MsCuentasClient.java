package cl.duoc.bancoxyz.ms.clientes.clients;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.duoc.bancoxyz.ms.clientes.dtos.core.CuentaCoreDTO;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MsCuentasClient {

    private final RestClient serviciosRestClient;
    private final LoadBalancerClient loadBalancerClient;
    private final String serviceId;

    public MsCuentasClient(
            RestClient serviciosRestClient,
            LoadBalancerClient loadBalancerClient,
            @Value("${ms-cuentas.service-id}") String serviceId) {
        this.serviciosRestClient = serviciosRestClient;
        this.loadBalancerClient = loadBalancerClient;
        this.serviceId = serviceId;
    }

    @CircuitBreaker(name = "msCuentas", fallbackMethod = "cuentasDelClienteFallback")
    public List<CuentaCoreDTO> cuentasDelCliente(Long clienteId) {
        ServiceInstance instancia = loadBalancerClient.choose(serviceId);
        if (instancia == null) {
            throw new IllegalStateException("No hay instancias de " + serviceId + " registradas en Eureka");
        }
        log.debug("[LB] Cuentas del cliente {} consultadas en {}:{}", clienteId, instancia.getHost(), instancia.getPort());
        return serviciosRestClient.get()
                .uri(instancia.getUri() + "/core/clientes/{id}/cuentas", clienteId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CuentaCoreDTO>>() {
                });
    }

    @SuppressWarnings("unused")
    private List<CuentaCoreDTO> cuentasDelClienteFallback(Long clienteId, Throwable error) {
        log.warn("[FALLBACK] ms-cuentas no responde para el cliente {}: se entrega el perfil sin cuentas. Causa: {}",
                clienteId, error.toString());
        return null;
    }
}
