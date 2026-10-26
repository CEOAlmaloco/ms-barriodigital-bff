package cl.duoc.barriodigital.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Define quién puede acceder a cada ruta según su rol.
 * Las rutas protegidas requieren un JWT válido.
 */
@Configuration
@Profile("!dev")
public class AzureSecurityConfig {

        /** Configura las reglas de acceso, los roles y las respuestas de error. */
        @Bean
        SecurityFilterChain azureSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health").permitAll()
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/catalog/procedures")
                                                .hasRole("Admin")
                                                .requestMatchers(HttpMethod.PUT, "/api/catalog/procedures/*")
                                                .hasRole("Admin")
                                                .requestMatchers("/api/admin/**").hasRole("Admin")
                                                .requestMatchers("/api/mq/**").hasRole("Admin")
                                                .requestMatchers(HttpMethod.PUT, "/api/requests/{id}/status")
                                                .hasRole("Funcionario")
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                new EntraRolesJwtConverter()))
                                                .authenticationEntryPoint(unauthorizedEntryPoint()))
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(unauthorizedEntryPoint())
                                                .accessDeniedHandler(forbiddenEntryPoint()));
                return http.build();
        }

        /** Si falta el JWT o no es válido, responde 401 en JSON. */
        @Bean
        AuthenticationEntryPoint unauthorizedEntryPoint() {
                return (request, response, authException) -> SecurityJsonErrors.write(
                                request,
                                response,
                                401,
                                "Unauthorized",
                                authException.getMessage() == null ? "Unauthorized" : authException.getMessage());
        }

        /** Si el JWT es válido pero el rol no alcanza, responde 403 en JSON. */
        @Bean
        AccessDeniedHandler forbiddenEntryPoint() {
                return (request, response, accessDeniedException) -> SecurityJsonErrors.write(
                                request,
                                response,
                                403,
                                "Forbidden",
                                "No tienes el rol necesario para este recurso");
        }
}
