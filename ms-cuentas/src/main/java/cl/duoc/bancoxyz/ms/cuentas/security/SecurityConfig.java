package cl.duoc.bancoxyz.ms.cuentas.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("SCOPE_soporte")
                        .requestMatchers(HttpMethod.GET, "/core/**").hasAnyAuthority("SCOPE_cuentas.leer", "SCOPE_cuentas.admin")
                        .requestMatchers(HttpMethod.POST, "/core/cuentas").hasAuthority("SCOPE_cuentas.admin")
                        .requestMatchers(HttpMethod.PATCH, "/core/cuentas/**").hasAuthority("SCOPE_cuentas.admin")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(recurso -> recurso.jwt(Customizer.withDefaults()))
                .build();
    }
}
