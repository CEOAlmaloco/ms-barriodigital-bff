package cl.duoc.barriodigital.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntraRolesJwtConverterTest {

    @Test
    void mapeaRolesDeEntraARolePrefix() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user")
                .claim("roles", List.of("Admin", "Auditor"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        Collection<GrantedAuthority> authorities = EntraRolesJwtConverter.extractRoles(jwt);
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_Admin", "ROLE_Auditor");
    }

    @Test
    void sinRolesQuedaVacio() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(EntraRolesJwtConverter.extractRoles(jwt)).isEmpty();
    }
}
