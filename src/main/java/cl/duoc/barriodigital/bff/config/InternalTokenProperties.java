package cl.duoc.barriodigital.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Token compartido BFF ↔ mq-admin / kafka-admin ({@code X-Internal-Token}).
 * Viene de {@code INTERNAL_TOKEN} (EP3-43).
 */
@Component
@ConfigurationProperties(prefix = "barriodigital.security")
public class InternalTokenProperties {

    private String internalToken = "";

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken == null ? "" : internalToken;
    }
}
