package gt.usac.cunoc.sgp.formulario.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.EstadoActaCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.service.ActaCalibracionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ActaCalibracionControllerSecurityTest.Config.class)
class ActaCalibracionControllerSecurityTest {
    @Autowired private ActaCalibracionService service;
    @Autowired private ActaCalibracionController controller;
    private final UUID id = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private UsernamePasswordAuthenticationToken autenticar(String rol) {
        var auth =
                new UsernamePasswordAuthenticationToken(
                        "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        auth.setDetails(new JwtData("usuario@ejemplo.com", id, RoleName.valueOf(rol), 0));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESTUDIANTE", "CATEDRATICO", "PROFESIONAL_EXTERNO"})
    void rechazaConsultaDescargaYCargaSinRolAdministrador(String rol) throws Exception {
        var auth = autenticar(rol);
        String ruta = "/api/v1/formularios/" + id + "/acta-calibracion";
        mvc.perform(get(ruta).principal(auth)).andExpect(status().isForbidden());
        mvc.perform(get(ruta + "/contenido").principal(auth)).andExpect(status().isForbidden());
        mvc.perform(
                        multipart(ruta)
                                .file(
                                        new MockMultipartFile("archivo", "acta.pdf", "application/pdf", new byte[] {1}))
                                .with(
                                        solicitud -> {
                                            solicitud.setMethod("PUT");
                                            return solicitud;
                                        })
                                .principal(auth))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void administradorConsultaSinActa() throws Exception {
        var auth = autenticar("ADMINISTRADOR");
        when(service.consultar(id))
                .thenReturn(
                        new EstadoActaCalibracionResponse(id, "SIN_ACTA", "Sin acta de calibracion", null));
        mvc.perform(get("/api/v1/formularios/" + id + "/acta-calibracion").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_ACTA"));
    }

    @Test
    void administradorDescargaSinUrlPublicaYConCacheDesactivada() throws Exception {
        var auth = autenticar("ADMINISTRADOR");
        when(service.descargar(id)).thenReturn(new byte[] {1, 2, 3});
        mvc.perform(get("/api/v1/formularios/" + id + "/acta-calibracion/contenido").principal(auth))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(
                        header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        ActaCalibracionService service() {
            return mock(ActaCalibracionService.class);
        }

        @Bean
        ActaCalibracionController controller(ActaCalibracionService service) {
            return new ActaCalibracionController(service);
        }
    }
}