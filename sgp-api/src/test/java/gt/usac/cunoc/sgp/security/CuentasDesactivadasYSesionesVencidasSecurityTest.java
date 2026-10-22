package gt.usac.cunoc.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas de seguridad para cuentas desactivadas, no verificadas y sesiones/tokens vencidos.
 *
 * <p>Criterio: Cuando un usuario desactivado o con un token vencido invoca un endpoint protegido,
 * recibe 401 o 403 y no obtiene datos.
 */
class CuentasDesactivadasYSesionesVencidasSecurityTest {

  private JwtService jwtService;
  private UserAccountRepository users;
  private ProblemAuthenticationEntryPoint authenticationEntryPoint;
  private FilterChain filterChain;
  private JwtAuthenticationFilter filter;

  private UUID userId;
  private UserAccount user;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    jwtService = mock(JwtService.class);
    users = mock(UserAccountRepository.class);
    authenticationEntryPoint = mock(ProblemAuthenticationEntryPoint.class);
    filterChain = mock(FilterChain.class);

    filter = new JwtAuthenticationFilter(jwtService, users, authenticationEntryPoint);

    userId = UUID.randomUUID();
    Role role = crearRol(RoleName.ESTUDIANTE);
    user = new UserAccount("estudiante@cunoc.edu.gt", "hashedPassword", role, true, true);
    user.verify();
    ReflectionTestUtils.setField(user, "id", userId);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Cuenta desactivada (activo = false) es rechazada con 401 y no ejecuta el filtro")
  void cuentaDesactivadaRetorna401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    JwtData jwtData =
        new JwtData("estudiante@cunoc.edu.gt", userId, RoleName.ESTUDIANTE, user.getTokenVersion());
    when(jwtService.parse("valid-token")).thenReturn(Optional.of(jwtData));

    user.deactivate(); // activo = false
    when(users.findWithRoleById(userId)).thenReturn(Optional.of(user));

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint)
        .commence(
            any(HttpServletRequest.class),
            any(HttpServletResponse.class),
            any(BadCredentialsException.class));
    verify(filterChain, never()).doFilter(any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("Token vencido o inválido es rechazado con 401 y no ejecuta el filtro")
  void tokenVencidoRetorna401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer expired-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Token vencido no es parseable
    when(jwtService.parse("expired-token")).thenReturn(Optional.empty());

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint)
        .commence(
            any(HttpServletRequest.class),
            any(HttpServletResponse.class),
            any(BadCredentialsException.class));
    verify(filterChain, never()).doFilter(any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("Cuenta no activada (activada = false) es rechazada con 401")
  void cuentaNoActivadaRetorna401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    Role role = crearRol(RoleName.ESTUDIANTE);
    UserAccount pendingUser =
        new UserAccount("estudiante@cunoc.edu.gt", "hash", role, false, false);
    pendingUser.verify(); // verificada pero no activada
    ReflectionTestUtils.setField(pendingUser, "id", userId);

    JwtData jwtData =
        new JwtData(
            "estudiante@cunoc.edu.gt", userId, RoleName.ESTUDIANTE, pendingUser.getTokenVersion());
    when(jwtService.parse("token")).thenReturn(Optional.of(jwtData));
    when(users.findWithRoleById(userId)).thenReturn(Optional.of(pendingUser));

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint)
        .commence(
            any(HttpServletRequest.class),
            any(HttpServletResponse.class),
            any(BadCredentialsException.class));
    verify(filterChain, never()).doFilter(any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("Cuenta no verificada (verificada = false) es rechazada con 401")
  void cuentaNoVerificadaRetorna401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    Role role = crearRol(RoleName.ESTUDIANTE);
    UserAccount unverifiedUser =
        new UserAccount("estudiante@cunoc.edu.gt", "hash", role, false, false);
    ReflectionTestUtils.setField(unverifiedUser, "id", userId);

    JwtData jwtData =
        new JwtData(
            "estudiante@cunoc.edu.gt",
            userId,
            RoleName.ESTUDIANTE,
            unverifiedUser.getTokenVersion());
    when(jwtService.parse("token")).thenReturn(Optional.of(jwtData));
    when(users.findWithRoleById(userId)).thenReturn(Optional.of(unverifiedUser));

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint)
        .commence(
            any(HttpServletRequest.class),
            any(HttpServletResponse.class),
            any(BadCredentialsException.class));
    verify(filterChain, never()).doFilter(any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("TokenVersion desactualizada (sesión invalidada) es rechazada con 401")
  void tokenVersionDesactualizadaRetorna401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer old-session-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // El token tiene versión 0 pero el usuario incrementó a versión 1
    user.incrementTokenVersion();
    JwtData jwtData = new JwtData("estudiante@cunoc.edu.gt", userId, RoleName.ESTUDIANTE, 0);
    when(jwtService.parse("old-session-token")).thenReturn(Optional.of(jwtData));
    when(users.findWithRoleById(userId)).thenReturn(Optional.of(user));

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint)
        .commence(
            any(HttpServletRequest.class),
            any(HttpServletResponse.class),
            any(BadCredentialsException.class));
    verify(filterChain, never()).doFilter(any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("Usuario activo con token vigente pasa correctamente la autenticación")
  void usuarioActivoPasaAutenticacion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    JwtData jwtData =
        new JwtData("estudiante@cunoc.edu.gt", userId, RoleName.ESTUDIANTE, user.getTokenVersion());
    when(jwtService.parse("valid-token")).thenReturn(Optional.of(jwtData));
    when(users.findWithRoleById(userId)).thenReturn(Optional.of(user));

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo("estudiante@cunoc.edu.gt");
  }

  private Role crearRol(RoleName name) {
    try {
      Constructor<Role> c = Role.class.getDeclaredConstructor();
      c.setAccessible(true);
      Role r = c.newInstance();
      ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(r, "name", name);
      ReflectionTestUtils.setField(r, "description", name.name());
      return r;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
