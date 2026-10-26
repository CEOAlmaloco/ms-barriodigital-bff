package cl.duoc.barriodigital.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Perfil dev para levantar el BFF sin Azure AD: cualquier petición pasa sin JWT y no aplica fuera de ese perfil.
 * CORS lo define {@link CorsConfig}.
 * Arranque: mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    /** Cadena local sin sesión ni CSRF, con CORS y con todas las rutas permitidas. */
    @Bean
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
