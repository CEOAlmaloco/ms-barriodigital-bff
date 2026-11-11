package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Cliente HTTP hacia kafka-admin.
 * Timeouts como catalog; el proxy de rutas llega en EP3-44.
 */
@Configuration
public class KafkaAdminClientConfig {

    /** Crea el RestClient con la URL de kafka-admin. */
    @Bean
    RestClient kafkaAdminRestClient(@Value("${barriodigital.kafka-admin.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CatalogClientConfig.CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(CatalogClientConfig.READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
