package gt.usac.cunoc.sgp.revision.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.revision.service.RevisionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RevisionControllerSecurityTest.Config.class)
class RevisionControllerSecurityTest {

  @Autowired private RevisionController controller;
  @Autowired private RevisionService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new SecurityAdvice())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void catedraticoPuedeConsultarSuBandeja() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    var authentication = autenticar(RoleName.CATEDRATICO, usuarioId);

    mockMvc
        .perform(get("/api/v1/revisiones/bandeja").principal(authentication))
        .andExpect(status().isOk());

    verify(service).listarBandeja(usuarioId, null, null, null, null, null, null, 0, 20);
  }

  @Test
  void revisadasUsaIdentidadAutenticadaYNoPermiteCache() throws Exception {
    UUID actor = UUID.randomUUID();
    mockMvc
        .perform(
            get("/api/v1/revisiones/revisadas")
                .param("pagina", "1")
                .param("tamanio", "10")
                .param("revisorId", UUID.randomUUID().toString())
                .principal(autenticar(RoleName.CATEDRATICO, actor)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    verify(service).listarResueltas(actor, 1, 10);
  }

  @Test
  void otrosRolesNoPuedenConsultarRevisadas() throws Exception {
    for (RoleName rol :
        List.of(RoleName.ESTUDIANTE, RoleName.PROFESIONAL_EXTERNO, RoleName.ADMINISTRADOR)) {
      mockMvc
          .perform(
              get("/api/v1/revisiones/revisadas").principal(autenticar(rol, UUID.randomUUID())))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }

  @Test
  void catedraticoPuedeIniciarRevisionPropia() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    var authentication = autenticar(RoleName.CATEDRATICO, usuarioId);

    mockMvc
        .perform(
            post("/api/v1/revisiones/{revisionId}/iniciar", revisionId).principal(authentication))
        .andExpect(status().isOk());

    verify(service).iniciar(revisionId, usuarioId);
  }

  @Test
  void catedraticoPuedeConsultarBorradoresDeSusCursos() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    var authentication = autenticar(RoleName.CATEDRATICO, usuarioId);

    mockMvc
        .perform(get("/api/v1/revisiones/borradores").principal(authentication))
        .andExpect(status().isOk());

    verify(service).listarBorradores(usuarioId, 0, 20);
  }

  @Test
  void estudianteNoPuedeConsultarBandeja() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE, UUID.randomUUID());

    mockMvc
        .perform(get("/api/v1/revisiones/bandeja").principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void profesionalYAdministradorNoPuedenIniciarRevision() throws Exception {
    UUID revisionId = UUID.randomUUID();
    for (RoleName role : List.of(RoleName.PROFESIONAL_EXTERNO, RoleName.ADMINISTRADOR)) {
      var authentication = autenticar(role, UUID.randomUUID());
      mockMvc
          .perform(
              post("/api/v1/revisiones/{revisionId}/iniciar", revisionId).principal(authentication))
          .andExpect(status().isForbidden());
    }

    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName role, UUID usuarioId) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "usuario@ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    authentication.setDetails(new JwtData("usuario@ejemplo.com", usuarioId, role, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    RevisionService revisionService() {
      return mock(RevisionService.class);
    }

    @Bean
    RevisionController revisionController(RevisionService revisionService) {
      return new RevisionController(revisionService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}
  }
}
