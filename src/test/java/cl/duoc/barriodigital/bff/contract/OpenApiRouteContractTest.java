package cl.duoc.barriodigital.bff.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * EP3.5-04: el OpenAPI publicado debe cubrir las rutas del BFF y las rutas
 * documentadas deben existir en el HandlerMapping.
 */
@SpringBootTest
class OpenApiRouteContractTest {

    private static final Pattern PATH_LINE = Pattern.compile("^\\s{2}(/[^:]+):\\s*$", Pattern.MULTILINE);

    private static final Set<String> REQUIRED = Set.of(
            "/api/ping",
            "/api/requests",
            "/api/requests/{id}",
            "/api/requests/{id}/status",
            "/api/catalog/procedures");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void openApiCubreRutasPublicadasDelBff() throws Exception {
        Set<String> documented = loadOpenApiPaths();
        assertThat(documented).containsAll(REQUIRED);

        Set<String> patterns = mappings.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .map(OpenApiRouteContractTest::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String path : REQUIRED) {
            String wanted = normalize(path);
            boolean found = patterns.stream().anyMatch(p -> p.equals(wanted) || matchesTemplate(p, wanted));
            assertThat(found)
                    .as("ruta documentada %s debe existir en el BFF", path)
                    .isTrue();
        }
    }

    private static Set<String> loadOpenApiPaths() throws Exception {
        try (InputStream in = OpenApiRouteContractTest.class.getResourceAsStream("/openapi/openapi.yaml")) {
            assertThat(in).as("openapi.yaml publicado").isNotNull();
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> paths = new LinkedHashSet<>();
            Matcher m = PATH_LINE.matcher(yaml);
            while (m.find()) {
                paths.add(normalize(m.group(1).trim()));
            }
            assertThat(paths).isNotEmpty();
            return paths;
        }
    }

    private static String normalize(String path) {
        return path.replace("{proxy+}", "**").replaceAll("/+$", "");
    }

    private static boolean matchesTemplate(String pattern, String documented) {
        // /api/report/** cubre /api/report/{proxy+}
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return documented.equals(prefix) || documented.startsWith(prefix + "/");
        }
        return pattern.equals(documented);
    }
}
