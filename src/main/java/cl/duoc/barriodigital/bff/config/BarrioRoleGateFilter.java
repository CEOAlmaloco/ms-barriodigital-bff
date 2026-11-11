package cl.duoc.barriodigital.bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exige exactamente uno de los cuatro roles de BarrioDigital en {@code /api/**} (EP3-04 / EP3-45).
 * Solo se registra en la cadena de Spring Security (perfil no-dev).
 */
public class BarrioRoleGateFilter extends OncePerRequestFilter {

    public static final String MSG_NO_ROLE = "No tienes un rol asignado en BarrioDigital";
    public static final String MSG_MULTI_ROLE = "El usuario debe tener un solo rol";

    private static final Set<String> BARRIO_ROLES = Set.of(
            "ROLE_Vecino", "ROLE_Funcionario", "ROLE_Admin", "ROLE_Auditor");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return true;
        }
        return path.startsWith("/api/requests/meta/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }
        Set<String> barrio = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(BARRIO_ROLES::contains)
                .collect(Collectors.toSet());
        if (barrio.isEmpty()) {
            SecurityJsonErrors.write(request, response, 403, "Forbidden", MSG_NO_ROLE);
            return;
        }
        if (barrio.size() > 1) {
            SecurityJsonErrors.write(request, response, 403, "Forbidden", MSG_MULTI_ROLE);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
