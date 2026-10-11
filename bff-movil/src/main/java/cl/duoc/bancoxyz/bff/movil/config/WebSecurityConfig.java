package cl.duoc.bancoxyz.bff.movil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("SCOPE_soporte")
                        .requestMatchers("/bff/movil/**").hasAuthority("SCOPE_canal.movil")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(recurso -> recurso.jwt(Customizer.withDefaults()))
                .build();
    }
}
