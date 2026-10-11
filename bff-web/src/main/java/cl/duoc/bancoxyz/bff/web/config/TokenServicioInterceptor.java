package cl.duoc.bancoxyz.bff.web.config;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenServicioInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager gestorTokens;
    private final String registroCliente;
    private final String nombreServicio;

    public TokenServicioInterceptor(OAuth2AuthorizedClientManager gestorTokens, String registroCliente,
            String nombreServicio) {
        this.gestorTokens = gestorTokens;
        this.registroCliente = registroCliente;
        this.nombreServicio = nombreServicio;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        OAuth2AuthorizeRequest pedido = OAuth2AuthorizeRequest.withClientRegistrationId(registroCliente)
                .principal(nombreServicio)
                .build();
        OAuth2AuthorizedClient clienteAutorizado = gestorTokens.authorize(pedido);
        if (clienteAutorizado == null) {
            throw new IllegalStateException("El auth-server no entrego un token para " + nombreServicio);
        }
        log.debug("[OAUTH2] Token de servicio vigente hasta {}", clienteAutorizado.getAccessToken().getExpiresAt());
        request.getHeaders().setBearerAuth(clienteAutorizado.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
