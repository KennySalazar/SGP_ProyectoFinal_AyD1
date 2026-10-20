package gt.usac.cunoc.sgp.sync.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.sync.service.PreparacionSalidaService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
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
import org.springframework.web.bind.annotation.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PreparacionSalidaControllerSecurityTest.Config.class)
class PreparacionSalidaControllerSecurityTest {
  @Autowired PreparacionSalidaController controller;
  @Autowired PreparacionSalidaService servicio;
  MockMvc mvc;
  UUID usuario = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    reset(servicio);
    SecurityContextHolder.clearContext();
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new Advice()).build();
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  UsernamePasswordAuthenticationToken sesion(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "prueba", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    auth.setDetails(new JwtData("prueba@test.com", usuario, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  @Test
  void rolesInspectoresPuedenSolicitarPaquete() throws Exception {
    for (var rol :
        List.of(RoleName.ESTUDIANTE, RoleName.CATEDRATICO, RoleName.PROFESIONAL_EXTERNO)) {
      mvc.perform(
              post("/api/v1/preparaciones-salida")
                  .principal(sesion(rol))
                  .contentType("application/json")
                  .content("{\"dispositivoId\":\"equipo\",\"puentesIds\":[]}"))
          .andExpect(status().isCreated())
          .andExpect(header().string("Cache-Control", "no-store"));
    }
    verify(servicio, times(3)).preparar(eq(usuario), any());
  }

  @Test
  void administradorNoPuedePreparar() throws Exception {
    mvc.perform(
            post("/api/v1/preparaciones-salida")
                .principal(sesion(RoleName.ADMINISTRADOR))
                .contentType("application/json")
                .content("{\"dispositivoId\":\"equipo\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void dispositivoVacioNoLlegaAlServicio() throws Exception {
    mvc.perform(
            post("/api/v1/preparaciones-salida")
                .principal(sesion(RoleName.CATEDRATICO))
                .contentType("application/json")
                .content("{\"dispositivoId\":\" \"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(servicio);
  }

  @Test
  void ultimaSeConsultaConUsuarioDeSesion() throws Exception {
    when(servicio.ultima(usuario, "equipo")).thenReturn(Optional.empty());
    mvc.perform(
            get("/api/v1/preparaciones-salida/ultima")
                .param("dispositivoId", "equipo")
                .principal(sesion(RoleName.ESTUDIANTE)))
        .andExpect(status().isNoContent());
    verify(servicio).ultima(usuario, "equipo");
  }

  @Test
  void busquedaMantieneFiltrosYPaginacion() throws Exception {
    UUID departamento = UUID.randomUUID();
    mvc.perform(
            get("/api/v1/preparaciones-salida/puentes")
                .principal(sesion(RoleName.CATEDRATICO))
                .param("departamentoId", departamento.toString())
                .param("busqueda", "GT-01")
                .param("pagina", "1")
                .param("tamanio", "50"))
        .andExpect(status().isOk());
    verify(servicio).disponibles(usuario, departamento, "GT-01", 1, 50);
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    PreparacionSalidaService servicio() {
      return mock(PreparacionSalidaService.class);
    }

    @Bean
    PreparacionSalidaController controller(PreparacionSalidaService servicio) {
      return new PreparacionSalidaController(servicio);
    }
  }

  @RestControllerAdvice
  static class Advice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void prohibido() {}
  }
}
