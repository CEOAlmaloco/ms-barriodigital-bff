package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RequestsClientConfig {

    @Bean
    RestClient requestsRestClient(
            @Value("${barriodigital.requests.base-url}") String baseUrl,
            @Value("${barriodigital.requests.connect-timeout:2s}") Duration connectTimeout,
            @Value("${barriodigital.requests.read-timeout:3s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
