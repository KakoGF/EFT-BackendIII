package cl.duoc.bancoxyz.ms.pagos.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import cl.duoc.bancoxyz.ms.pagos.dtos.core.CuentaCoreDTO;
import cl.duoc.bancoxyz.ms.pagos.exceptions.CuentaNoEncontradaException;

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

    @CircuitBreaker(name = "msCuentas", fallbackMethod = "obtenerCuentaFallback")
    public CuentaCoreDTO obtenerCuenta(Long cuentaId) {
        ServiceInstance instancia = loadBalancerClient.choose(serviceId);
        if (instancia == null) {
            throw new IllegalStateException("No hay instancias de " + serviceId + " registradas en Eureka");
        }
        log.debug("[LB] Consulta de la cuenta {} atendida por {}:{}", cuentaId, instancia.getHost(), instancia.getPort());
        try {
            return serviciosRestClient.get()
                    .uri(instancia.getUri() + "/core/cuentas/{id}", cuentaId)
                    .retrieve()
                    .body(CuentaCoreDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CuentaNoEncontradaException(cuentaId);
        }
    }

    @SuppressWarnings("unused")
    private CuentaCoreDTO obtenerCuentaFallback(Long cuentaId, Throwable error) {
        if (error instanceof CuentaNoEncontradaException noEncontrada) {
            throw noEncontrada;
        }
        log.warn("[FALLBACK] ms-cuentas no responde al consultar la cuenta {}: la solicitud sigue en modo conservador. Causa: {}",
                cuentaId, error.toString());
        return null;
    }
}
