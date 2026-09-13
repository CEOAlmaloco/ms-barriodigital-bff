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
 * Resource Server Azure AD.
 * EP1-12: JWT inválido / ausente → 401.
 * EP1-13: autenticado sin rol Admin → 403 en {@code /api/admin/**}.
 * CORS: ver {@link CorsConfig} (EP1-16).
 * EP1-15: {@code /api/requests/**} exige JWT (cualquier rol autenticado).
 */
@Configuration
@Profile("!dev")
public class AzureSecurityConfig {

    @Bean
    SecurityFilterChain azureSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("Admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new EntraRolesJwtConverter()))
                        .authenticationEntryPoint(unauthorizedEntryPoint()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenEntryPoint()));
        return http.build();
    }

    @Bean
    AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                SecurityJsonErrors.write(
                        request,
                        response,
                        401,
                        "Unauthorized",
                        authException.getMessage() == null ? "Unauthorized" : authException.getMessage());
    }

    @Bean
    AccessDeniedHandler forbiddenEntryPoint() {
        return (request, response, accessDeniedException) ->
                SecurityJsonErrors.write(
                        request,
                        response,
                        403,
                        "Forbidden",
                        "No tienes el rol necesario para este recurso");
    }
}
