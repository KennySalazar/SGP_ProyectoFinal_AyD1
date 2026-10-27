package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.service.DocumentoInspeccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Autenticación, delegación del rol y forma de la descarga de documentos (HU-070). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DocumentoInspeccionControllerSecurityTest.Config.class)
class DocumentoInspeccionControllerSecurityTest {

  private static final UUID USUARIO = UUID.fromString("019a0000-0000-7000-8000-000000007001");
  private static final UUID INSPECCION = UUID.fromString("019a0000-0000-7000-8000-000000007002");
  private static final UUID DOCUMENTO = UUID.fromString("019a0000-0000-7000-8000-000000007003");
  private static final String BASE = "/api/v1/inspecciones/" + INSPECCION + "/documentos";

  @Autowired private DocumentoInspeccionController controller;
  @Autowired private DocumentoInspeccionService documentos;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(documentos);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void unVisitanteRecibe401EnTodosLosExtremos() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mockMvc.perform(multipart(BASE).file(archivo())).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(BASE + "/" + DOCUMENTO + "/contenido"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete(BASE + "/" + DOCUMENTO)).andExpect(status().isUnauthorized());
    verifyNoInteractions(documentos);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"ESTUDIANTE", "CATEDRATICO", "PROFESIONAL_EXTERNO"})
  void elInspectorAdjuntaConSuIdentidadYConsultaConSuRol(RoleName rol) throws Exception {
    mockMvc
        .perform(multipart(BASE).file(archivo()).principal(autenticar(rol)))
        .andExpect(status().isCreated());
    verify(documentos).adjuntar(eq(INSPECCION), eq(USUARIO), any());

    mockMvc.perform(get(BASE).principal(autenticar(rol))).andExpect(status().isOk());
    verify(documentos).listar(INSPECCION, USUARIO, rol);
  }

  @Test
  void laDescargaUsaElNombreOriginalComoAdjuntoSinExponerElAlmacenamiento() throws Exception {
    when(documentos.descargar(INSPECCION, DOCUMENTO, USUARIO, RoleName.CATEDRATICO))
        .thenReturn(
            new DocumentoInspeccionService.Descarga(
                new ByteArrayInputStream("%PDF-1.7".getBytes()), "Plano estructural ñ.pdf"));

    mockMvc
        .perform(
            get(BASE + "/" + DOCUMENTO + "/contenido").principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", "no-store, private"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("attachment;"),
                        org.hamcrest.Matchers.containsString(
                            "filename*=UTF-8''Plano%20estructural%20%C3%B1.pdf"))));
  }

  @Test
  void quienNoEsElAutorRecibe403AlAdjuntar() throws Exception {
    when(documentos.adjuntar(eq(INSPECCION), eq(USUARIO), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.FORBIDDEN,
                "documento_no_autorizado",
                "Documento de inspección",
                "Solo el autor de la inspección puede adjuntar o retirar documentos."));

    mockMvc
        .perform(multipart(BASE).file(archivo()).principal(autenticar(RoleName.ESTUDIANTE)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("documento_no_autorizado"));
  }

  private static MockMultipartFile archivo() {
    return new MockMultipartFile("archivo", "informe.pdf", "application/pdf", new byte[] {1});
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "usuario@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    auth.setDetails(new JwtData("usuario@ejemplo.com", USUARIO, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    DocumentoInspeccionService documentoInspeccionService() {
      return mock(DocumentoInspeccionService.class);
    }

    @Bean
    DocumentoInspeccionController documentoInspeccionController(
        DocumentoInspeccionService documentos) {
      return new DocumentoInspeccionController(documentos);
    }
  }
}
