package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = InspeccionController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class InspeccionControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private InspeccionService inspeccionService;
  @MockBean private JwtService jwtService;
  @MockBean private UserAccountRepository userAccountRepository;
  @MockBean private java.time.Clock clock;

  @Test
  @DisplayName("POST sin autenticación retorna 401 Unauthorized")
  void iniciarInspeccion_sinAutenticacion_retorna401() throws Exception {
    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            UUID.randomUUID(),
            LocalDate.of(2026, 10, 15),
            "dispositivo-1",
            null,
            null,
            null,
            null,
            false,
            null);

    mvc.perform(
            post("/api/v1/inspecciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST autenticado como Catedrático retorna 201 Created")
  void iniciarInspeccion_autenticado_retorna201() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    UUID puenteId = UUID.randomUUID();
    UUID inspeccionId = UUID.randomUUID();

    JwtData jwtData = new JwtData("profesor@cunoc.edu.gt", usuarioId, RoleName.CATEDRATICO, 0);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            jwtData.email(), null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    auth.setDetails(jwtData);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            inspeccionId,
            puenteId,
            LocalDate.of(2026, 10, 15),
            "dispositivo-1",
            "Prof. Juan",
            14.6407,
            -90.5132,
            new BigDecimal("5.0"),
            false,
            null);

    IniciarInspeccionResponse response =
        new IniciarInspeccionResponse(
            inspeccionId,
            puenteId,
            "P-01-01-0001",
            "Puente Test",
            UUID.randomUUID(),
            "sieca-v1",
            usuarioId,
            "CATEDRATICO",
            "Prof. Juan",
            "BORRADOR",
            LocalDate.of(2026, 10, 15),
            "dispositivo-1",
            14.6407,
            -90.5132,
            new BigDecimal("5.0"),
            BigDecimal.ZERO,
            false,
            null,
            false,
            null,
            List.of(),
            Instant.now());

    when(inspeccionService.iniciarInspeccion(any(), eq(usuarioId), eq("idempotency-123")))
        .thenReturn(response);

    mvc.perform(
            post("/api/v1/inspecciones")
                .header("Idempotency-Key", "idempotency-123")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(inspeccionId.toString()))
        .andExpect(jsonPath("$.estado").value("BORRADOR"))
        .andExpect(jsonPath("$.codigoPuente").value("P-01-01-0001"));
  }

  @Test
  @DisplayName("GET datos permanentes vigentes retorna 200 con datos")
  void obtenerDatosPermanentes_retorna200() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    UUID puenteId = UUID.randomUUID();

    JwtData jwtData = new JwtData("profesor@cunoc.edu.gt", usuarioId, RoleName.CATEDRATICO, 0);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            jwtData.email(), null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    auth.setDetails(jwtData);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);

    DatosPermanentesResponse dp =
        new DatosPermanentesResponse(
            new BigDecimal("100.00"),
            3,
            new BigDecimal("8.00"),
            null,
            null,
            null,
            null,
            "Viga losa",
            null,
            2,
            "Concreto",
            "Concreto",
            "HS-20",
            2010,
            "SI",
            "TANGENTE",
            false,
            null,
            null,
            null,
            null,
            null);

    when(inspeccionService.obtenerDatosPermanentesVigentes(puenteId)).thenReturn(Optional.of(dp));

    mvc.perform(
            get("/api/v1/inspecciones/puentes/" + puenteId + "/datos-permanentes")
                .principal(auth)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipologiaPuente").value("Viga losa"))
        .andExpect(jsonPath("$.anioConstruccion").value(2010));
  }
}
