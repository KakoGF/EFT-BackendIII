package cl.duoc.bancoxyz.bff.cajero.clients;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.bff.cajero.dtos.core.TransaccionCoreDTO;
import cl.duoc.bancoxyz.bff.cajero.exceptions.SolicitudRechazadaException;

@Component
public class MsPagosClient {

    private final RestClient backendRestClient;
    private final ServicioResolver servicioResolver;
    private final String serviceId;
    private final ObjectMapper objectMapper;

    public MsPagosClient(
            RestClient backendRestClient,
            ServicioResolver servicioResolver,
            @Value("${ms-pagos.service-id}") String serviceId,
            ObjectMapper objectMapper) {
        this.backendRestClient = backendRestClient;
        this.servicioResolver = servicioResolver;
        this.serviceId = serviceId;
        this.objectMapper = objectMapper;
    }

    public TransaccionCoreDTO obtenerTransaccion(String solicitudId) {
        try {
            return backendRestClient.get()
                    .uri(servicioResolver.urlBase(serviceId) + "/pagos/transacciones/{id}", solicitudId)
                    .retrieve()
                    .body(TransaccionCoreDTO.class);
        } catch (HttpClientErrorException ex) {
            throw new SolicitudRechazadaException(ex.getStatusCode().value(), extraerMensaje(ex));
        }
    }

    public List<TransaccionCoreDTO> listarTransacciones(Long cuentaId) {
        return backendRestClient.get()
                .uri(servicioResolver.urlBase(serviceId) + "/pagos/cuentas/{id}/transacciones", cuentaId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TransaccionCoreDTO>>() {
                });
    }

    private String extraerMensaje(HttpClientErrorException ex) {
        try {
            JsonNode cuerpo = objectMapper.readTree(ex.getResponseBodyAsString());
            if (cuerpo != null && cuerpo.hasNonNull("mensaje")) {
                return cuerpo.get("mensaje").asText();
            }
        } catch (Exception ignorada) {
            return ex.getStatusText();
        }
        return ex.getStatusText();
    }
}
