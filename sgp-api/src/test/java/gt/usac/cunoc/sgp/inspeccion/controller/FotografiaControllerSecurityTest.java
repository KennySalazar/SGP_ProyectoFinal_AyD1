package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.inspeccion.dto.*;
import gt.usac.cunoc.sgp.inspeccion.service.FotografiaService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = FotografiaController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class FotografiaControllerSecurityTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean FotografiaService servicio;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;

  private UsernamePasswordAuthenticationToken sesion(UUID usuario) {
    var a =
        new UsernamePasswordAuthenticationToken(
            "inspector", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    a.setDetails(new JwtData("test@example.com", usuario, RoleName.CATEDRATICO, 0));
    return a;
  }

  @Test
  void noHayLecturaAnonimaDeListadoOriginalNiMiniatura() throws Exception {
    String base = "/api/v1/inspecciones/" + UUID.randomUUID() + "/fotografias";
    for (String sufijo :
        List.of("", "/" + UUID.randomUUID() + "/contenido", "/" + UUID.randomUUID() + "/miniatura"))
      mvc.perform(get(base + sufijo)).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @Test
  void multipartUsaIdentidadDeSesionYRespuestaNoContieneClaves() throws Exception {
    UUID usuario = UUID.randomUUID(),
        inspeccion = UUID.randomUUID(),
        foto = gt.usac.cunoc.sgp.common.util.UuidV7Generator.generate();
    var r =
        new AdjuntarFotografiaRequest(
            foto, "ACCESO", 14.0, -90.0, Instant.parse("2026-10-15T12:00:00Z"), null, null);
    when(servicio.adjuntar(eq(inspeccion), eq(usuario), eq(r), any(), eq("clave")))
        .thenReturn(
            new FotografiaResponse(
                foto,
                inspeccion,
                CategoriaFoto.ACCESO,
                null,
                null,
                "image/jpeg",
                12,
                100,
                50,
                14,
                -90,
                r.capturadoEn()));
    mvc.perform(
            multipart("/api/v1/inspecciones/" + inspeccion + "/fotografias")
                .file(new MockMultipartFile("archivo", "imagen.jpg", "image/jpeg", new byte[] {1}))
                .file(
                    new MockMultipartFile(
                        "metadatos",
                        "metadatos.json",
                        "application/json",
                        json.writeValueAsBytes(r)))
                .header("Idempotency-Key", "clave")
                .with(authentication(sesion(usuario))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(foto.toString()))
        .andExpect(jsonPath("$.claveObjetoOriginal").doesNotExist());
  }

  @Test
  void lecturaPrivadaYPartesInvalidasTienenContratoExplicito() throws Exception {
    UUID usuario = UUID.randomUUID(), id = UUID.randomUUID(), foto = UUID.randomUUID();
    String base = "/api/v1/inspecciones/" + id + "/fotografias";
    when(servicio.contenido(id, foto, usuario, true))
        .thenReturn(new java.io.ByteArrayInputStream(new byte[] {1, 2}));
    mvc.perform(get(base + "/" + foto + "/miniatura").with(authentication(sesion(usuario))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store, private"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    mvc.perform(multipart(base).with(authentication(sesion(usuario))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(base)
                .file(
                    new MockMultipartFile(
                        "metadatos", "m.json", "application/json", "{".getBytes()))
                .file(new MockMultipartFile("archivo", "f.jpg", "image/jpeg", new byte[] {1}))
                .header("Idempotency-Key", "x")
                .with(authentication(sesion(usuario))))
        .andExpect(status().isBadRequest());
  }
}
