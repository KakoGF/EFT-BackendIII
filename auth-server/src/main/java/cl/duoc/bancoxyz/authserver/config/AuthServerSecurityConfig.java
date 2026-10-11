package cl.duoc.bancoxyz.authserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
@EnableWebSecurity
public class AuthServerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain filtroServidorAutorizacion(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.exceptionHandling(errores -> errores.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filtroLogin(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder codificadorClaves() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService usuariosEnMemoria(
            PasswordEncoder codificadorClaves,
            @Value("${seguridad.cliente.usuario}") String usuarioCliente,
            @Value("${seguridad.cliente.clave}") String claveCliente) {

        UserDetails cliente = User.withUsername(usuarioCliente)
                .password(codificadorClaves.encode(claveCliente))
                .roles("CLIENTE")
                .build();

        return new InMemoryUserDetailsManager(cliente);
    }
}
