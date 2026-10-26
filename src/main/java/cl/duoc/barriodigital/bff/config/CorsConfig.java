package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Permite que el navegador, por defecto en http://localhost:4200, llame al BFF.
 * No se usa el origen "*" porque la petición lleva credenciales y el header Authorization.
 */
@Configuration
public class CorsConfig {

    /** Define orígenes, métodos y headers permitidos, incluida Authorization. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${barriodigital.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(splitCsv(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Parte la lista de orígenes separada por comas y descarta los vacíos. */
    private static List<String> splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
