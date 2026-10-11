package cl.duoc.bancoxyz.ms.pagos.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

@Configuration
public class ServiciosClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager gestorTokensServicio(
            ClientRegistrationRepository registrosClientes,
            OAuth2AuthorizedClientService servicioClientesAutorizados) {

        AuthorizedClientServiceOAuth2AuthorizedClientManager gestor =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrosClientes, servicioClientesAutorizados);
        gestor.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return gestor;
    }

    @Bean
    public RestClient serviciosRestClient(
            @Value("${servicios.timeout.conexion-ms}") long timeoutConexionMs,
            @Value("${servicios.timeout.lectura-ms}") long timeoutLecturaMs,
            OAuth2AuthorizedClientManager gestorTokensServicio) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutConexionMs))
                .withReadTimeout(Duration.ofMillis(timeoutLecturaMs));

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new TokenServicioInterceptor(gestorTokensServicio, "backend", "ms-pagos"))
                .build();
    }
}
