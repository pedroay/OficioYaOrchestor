package eci.edu.dosw.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import eci.edu.dosw.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Sin @Component: si lo tuviera, Spring Boot lo registraria como
// filtro de servlet fuera de la cadena de seguridad.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PREFIX = "Bearer ";
    // SERVICE se excluye: el token de servicio es interno y nunca debe autenticar tráfico externo.
    private static final Set<String> ROLES_VALIDOS = Arrays.stream(Role.values())
            .map(Role::name)
            .filter(nombre -> !nombre.equals("SERVICE"))
            .collect(Collectors.toSet());

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            autenticar(header.substring(PREFIX.length()).trim());
        }
        chain.doFilter(request, response);
    }

    private void autenticar(String token) {
        try {
            Claims claims = jwtService.parse(token);
            String subject = claims.getSubject();
            if (subject == null || claims.getIssuedAt() == null
                    || JwtService.SERVICE_SUBJECT.equals(subject)) {
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(subject, null, autoridades(claims)));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rechazado: {}", e.getClass().getSimpleName());
        }
    }

    private List<SimpleGrantedAuthority> autoridades(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(ROLES_VALIDOS::contains)
                .map(nombre -> new SimpleGrantedAuthority("ROLE_" + nombre))
                .toList();
    }
}