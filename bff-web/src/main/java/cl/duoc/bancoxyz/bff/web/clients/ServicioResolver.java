package cl.duoc.bancoxyz.bff.web.clients;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;

import cl.duoc.bancoxyz.bff.web.exceptions.ServicioNoDisponibleException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServicioResolver {

    private final LoadBalancerClient loadBalancerClient;

    public String urlBase(String serviceId) {
        ServiceInstance instancia = loadBalancerClient.choose(serviceId);
        if (instancia == null) {
            throw new ServicioNoDisponibleException(serviceId);
        }
        log.debug("[LB] {} -> {}:{}", serviceId, instancia.getHost(), instancia.getPort());
        return instancia.getUri().toString();
    }
}
