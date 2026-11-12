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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * Matriz de roles EP3-04 / EP3-45. JWT obligatorio fuera de health y OPTIONS.
 * {@link BarrioRoleGateFilter} corta sin rol o con más de uno.
 */
@Configuration
@Profile("!dev")
public class AzureSecurityConfig {

        /** Filtro de exactamente un rol (no se registra como servlet filter global). */
        @Bean
        BarrioRoleGateFilter barrioRoleGateFilter() {
                return new BarrioRoleGateFilter();
        }

        /** Configura las reglas de acceso, los roles y las respuestas de error. */
        @Bean
        SecurityFilterChain azureSecurityFilterChain(
                HttpSecurity http,
                BarrioRoleGateFilter barrioRoleGateFilter
        ) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health").permitAll()
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/requests")
                                                .hasRole("Vecino")
                                                .requestMatchers(HttpMethod.POST, "/api/catalog/procedures")
                                                .hasRole("Admin")
                                                .requestMatchers(HttpMethod.PUT, "/api/catalog/procedures/*")
                                                .hasRole("Admin")
                                                .requestMatchers("/api/admin/**").hasRole("Admin")
                                                .requestMatchers("/api/mq/**").hasRole("Admin")
                                                .requestMatchers("/api/kafka/**").hasRole("Admin")
                                                .requestMatchers(HttpMethod.GET, "/api/report/kpis",
                                                                "/api/report/top-procedures")
                                                .hasAnyRole("Admin", "Auditor")
                                                .requestMatchers(HttpMethod.GET, "/api/report/sla",
                                                                "/api/report/sla/**")
                                                .hasAnyRole("Funcionario", "Admin", "Auditor")
                                                .requestMatchers("/api/report/**").denyAll()
                                                .requestMatchers("/api/audit/**").hasAnyRole("Admin", "Auditor")
                                                .requestMatchers(HttpMethod.PUT, "/api/requests/*/status")
                                                .hasAnyRole("Funcionario", "Admin")
                                                .requestMatchers(HttpMethod.POST, "/api/requests/*/gestiones")
                                                .hasAnyRole("Funcionario", "Admin")
                                                .requestMatchers(HttpMethod.POST, "/api/requests/*/take")
                                                .hasRole("Funcionario")
                                                .requestMatchers(HttpMethod.PUT, "/api/requests/*/assignee")
                                                .hasAnyRole("Funcionario", "Admin")
                                                .requestMatchers(HttpMethod.GET, "/api/requests/assignees")
                                                .hasAnyRole("Funcionario", "Admin")
                                                .requestMatchers("/api/**")
                                                .hasAnyRole("Vecino", "Funcionario", "Admin", "Auditor")
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                new EntraRolesJwtConverter()))
                                                .authenticationEntryPoint(unauthorizedEntryPoint()))
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(unauthorizedEntryPoint())
                                                .accessDeniedHandler(forbiddenEntryPoint()))
                                .addFilterAfter(barrioRoleGateFilter, BearerTokenAuthenticationFilter.class);
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
