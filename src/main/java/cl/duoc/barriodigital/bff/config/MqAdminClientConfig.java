package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Cliente HTTP hacia mq-admin.
 * Usa los mismos timeouts que {@link CatalogClientConfig}: si no conecta o no responde, el proxy corta y el BFF responde 503.
 */
@Configuration
public class MqAdminClientConfig {

    /** Crea el RestClient con la URL de mq-admin y esos timeouts. */
    @Bean
    RestClient mqAdminRestClient(@Value("${barriodigital.mq-admin.base-url}") String baseUrl) {
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
