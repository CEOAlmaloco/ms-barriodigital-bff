package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP hacia catalog.
 * El alta de un trámite lo consulta antes de reenviar, así que si catalog no conecta o no responde a tiempo el BFF responde 503 y no deja colgada la petición.
 * Usa el JDK HttpClient, con 2 segundos para conectar y 3 para leer.
 */
@Configuration
public class CatalogClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /** Crea el RestClient de catalog con esos timeouts. */
    @Bean
    RestClient catalogRestClient(@Value("${barriodigital.catalog.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
