package cl.duoc.bancoxyz.bff.web.clients;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import cl.duoc.bancoxyz.bff.web.dtos.core.CuentaCoreDTO;
import cl.duoc.bancoxyz.bff.web.dtos.core.MovimientoCoreDTO;
import cl.duoc.bancoxyz.bff.web.exceptions.CuentaNoEncontradaException;

@Component
public class MsCuentasClient {

    private final RestClient backendRestClient;
    private final ServicioResolver servicioResolver;
    private final String serviceId;

    public MsCuentasClient(
            RestClient backendRestClient,
            ServicioResolver servicioResolver,
            @Value("${ms-cuentas.service-id}") String serviceId) {
        this.backendRestClient = backendRestClient;
        this.servicioResolver = servicioResolver;
        this.serviceId = serviceId;
    }

    public List<CuentaCoreDTO> obtenerCuentas() {
        return backendRestClient.get()
                .uri(servicioResolver.urlBase(serviceId) + "/core/cuentas")
                .retrieve()
                .body(new ParameterizedTypeReference<List<CuentaCoreDTO>>() {
                });
    }

    public CuentaCoreDTO obtenerCuenta(Long cuentaId) {
        try {
            return backendRestClient.get()
                    .uri(servicioResolver.urlBase(serviceId) + "/core/cuentas/{id}", cuentaId)
                    .retrieve()
                    .body(CuentaCoreDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CuentaNoEncontradaException(cuentaId);
        }
    }

    public List<MovimientoCoreDTO> obtenerMovimientos(Long cuentaId) {
        try {
            return backendRestClient.get()
                    .uri(servicioResolver.urlBase(serviceId) + "/core/cuentas/{id}/movimientos", cuentaId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MovimientoCoreDTO>>() {
                    });
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CuentaNoEncontradaException(cuentaId);
        }
    }
}
