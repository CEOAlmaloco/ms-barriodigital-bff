package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP hacia kafka-admin.
 * Connect como catalog; lectura 25 s (mayor que el tope de 23 s del replay y menor que los 30 s del Gateway).
 */
@Configuration
public class KafkaAdminClientConfig {

    static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    /** Crea el RestClient con la URL de kafka-admin. */
    @Bean
    RestClient kafkaAdminRestClient(@Value("${barriodigital.kafka-admin.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CatalogClientConfig.CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
