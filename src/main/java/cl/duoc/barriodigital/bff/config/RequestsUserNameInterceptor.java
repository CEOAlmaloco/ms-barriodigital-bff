package cl.duoc.barriodigital.bff.config;

import cl.duoc.barriodigital.bff.web.CatalogCaller;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

/**
 * Agrega {@code X-User-Name} a toda llamada a requests que no lo traiga, con el nombre del JWT ya validado y
 * codificado en URL. requests registra al personal en {@code staff_member} en cada llamada, también en las lecturas
 * (contrato EP3-05, sección 5.1): sin el nombre, la lista de funcionarios mostraría el oid.
 * Un header que la llamada ya puso, aunque vaya vacío, no se cambia. Sin JWT (perfil dev) no agrega nada.
 */
final class RequestsUserNameInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsKey(CatalogCaller.HEADER_USER_NAME)) {
            String name = CatalogCaller.identity(SecurityContextHolder.getContext().getAuthentication()).userName();
            if (name != null) {
                request.getHeaders().set(CatalogCaller.HEADER_USER_NAME, name);
            }
        }
        return execution.execute(request, body);
    }
}
