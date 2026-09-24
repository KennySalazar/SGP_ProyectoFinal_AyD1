
package gt.usac.cunoc.sgp.common.security;

import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users, AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.users = users;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ")) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Bearer invalido"));
            return;
        }
        JwtData data = jwtService.parse(authorization.substring(7).trim()).orElse(null);
        UserAccount user = data == null ? null : users.findWithRoleById(data.userId()).orElse(null);
        if (!isValid(data, user)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("JWT invalido"));
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name())));
        authentication.setDetails(data);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean isValid(JwtData data, UserAccount user) {
        return data != null && user != null && user.isActive() && user.isActivated() && user.isVerified()
            && user.getEmail().equals(data.email()) && user.getRole().getName() == data.role()
            && user.getTokenVersion() == data.tokenVersion();
    }
}
