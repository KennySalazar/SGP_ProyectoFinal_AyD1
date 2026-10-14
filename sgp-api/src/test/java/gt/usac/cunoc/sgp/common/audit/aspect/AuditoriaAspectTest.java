package gt.usac.cunoc.sgp.common.audit.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditoriaAspectTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000006");

  private final AuditoriaWriter escritor = mock(AuditoriaWriter.class);
  private final AuditoriaSnapshotService snapshots = mock(AuditoriaSnapshotService.class);
  private final AuditoriaAspect aspect = new AuditoriaAspect(escritor, snapshots);
  private final ProceedingJoinPoint punto = mock(ProceedingJoinPoint.class);
  private final MethodSignature firma = mock(MethodSignature.class);

  @BeforeEach
  void prepararFirma() {
    when(punto.getSignature()).thenReturn(firma);
    when(firma.getName()).thenReturn("metodo");
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: registrar una acción exitosa crea el registro en auditoría")
  void accionExitosaCreaRegistro() throws Throwable {
    // Dado un usuario autenticado con su JwtData y una acción que termina correctamente
    autenticar();
    var posteriores = new ObjectMapper().readTree("{\"codigo\":\"GT-01-0114-0001\"}");
    when(punto.proceed()).thenReturn("resultado");
    when(snapshots.serializar("resultado")).thenReturn(posteriores);

    // Cuando el aspecto rodea la ejecución del servicio
    Object resultado = aspect.auditar(punto, anotacion("crearPuente"));

    // Entonces se escribe el registro con usuario, acción, entidad y valores posteriores
    assertEquals("resultado", resultado);
    verify(escritor)
        .registrar(
            eq(USUARIO_ID),
            eq(AccionAuditoria.CREAR),
            eq("puente"),
            isNull(),
            isNull(),
            eq(posteriores),
            eq(""));
  }

  @Test
  @DisplayName("Escenario: la acción fallida no genera registro de auditoría")
  void accionFallidaNoGeneraRegistro() throws Throwable {
    // Dado un usuario autenticado y un servicio que lanza ApiException
    autenticar();
    var excepcion =
        new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validacion", "Titulo", "Detalle");
    when(punto.proceed()).thenThrow(excepcion);

    // Cuando el aspecto rodea la ejecución del servicio
    // Entonces la excepción de negocio se propaga y no hay registro de auditoría
    assertThrows(ApiException.class, () -> aspect.auditar(punto, anotacion("crearPuente")));
    verifyNoInteractions(escritor);
  }

  @Test
  @DisplayName("Escenario: el fallo de auditoría no bloquea la operación de negocio")
  void falloDeAuditoriaNoBloqueaElNegocio() throws Throwable {
    // Dado un servicio que termina correctamente pero la escritura de auditoría falla
    autenticar();
    when(punto.proceed()).thenReturn("resultado");
    when(snapshots.serializar("resultado")).thenReturn(null);
    doThrow(new IllegalStateException("fallo tecnico"))
        .when(escritor)
        .registrar(
            eq(USUARIO_ID),
            eq(AccionAuditoria.CREAR),
            eq("puente"),
            isNull(),
            isNull(),
            isNull(),
            eq(""));

    // Cuando el aspecto rodea la ejecución del servicio
    Object resultado = aspect.auditar(punto, anotacion("crearPuente"));

    // Entonces la operación de negocio se completa igual
    assertEquals("resultado", resultado);
  }

  @Test
  @DisplayName("Escenario: acción de tarea programada usa usuario nulo y marca el proceso")
  void procesoAutomaticoUsaUsuarioNuloYMarca() throws Throwable {
    // Dado que no hay usuario autenticado y un método de proceso automático
    SecurityContextHolder.clearContext();
    when(punto.proceed()).thenReturn(null);

    // Cuando el aspecto rodea la ejecución del servicio
    aspect.auditar(punto, anotacion("provisionar"));

    // Entonces el registro queda con usuario nulo y la marca del proceso
    verify(escritor)
        .registrar(
            isNull(),
            eq(AccionAuditoria.CREAR),
            eq("usuario"),
            isNull(),
            isNull(),
            isNull(),
            eq("provision-admin"));
  }

  private void autenticar() {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "admin@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    authentication.setDetails(
        new JwtData("admin@ejemplo.com", USUARIO_ID, RoleName.ADMINISTRADOR, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private Auditable anotacion(String metodo, Class<?>... parametros) throws NoSuchMethodException {
    Method metodoReal = ServicioAuditable.class.getMethod(metodo, parametros);
    return metodoReal.getAnnotation(Auditable.class);
  }

  static class ServicioAuditable {

    @Auditable(accion = AccionAuditoria.CREAR, entidad = "puente")
    public String crearPuente() {
      return null;
    }

    @Auditable(accion = AccionAuditoria.CREAR, entidad = "usuario", proceso = "provision-admin")
    @Scheduled(fixedDelay = 3600000)
    public void provisionar() {}

    @Auditable(
        accion = AccionAuditoria.MODIFICAR,
        entidad = "version_formulario",
        idArg = "id",
        tipo = gt.usac.cunoc.sgp.formulario.entity.VersionFormulario.class,
        recargarPosteriores = true)
    public void modificarConfiguracion(UUID id) {}
  }

  @Test
  void recargaLaConfiguracionAnteriorYPosteriorConElUsuarioAutenticado() throws Throwable {
    autenticar();

    UUID versionId = UUID.randomUUID();
    ObjectMapper json = new ObjectMapper();

    var anteriores =
        json.readTree(
            """
                    {
                      "pesos": [{"elementoRef": "/vigas", "peso": 1}],
                      "reglasEvaluacionIc": {
                        "reglas_evaluacion_ic": {
                          "escala_severidad": {"SEVERO": 3}
                        }
                      }
                    }
                    """);

    var posteriores =
        json.readTree(
            """
                    {
                      "pesos": [{"elementoRef": "/vigas", "peso": 2}],
                      "reglasEvaluacionIc": {
                        "reglas_evaluacion_ic": {
                          "escala_severidad": {"SEVERO": 4}
                        }
                      }
                    }
                    """);

    when(firma.getParameterNames()).thenReturn(new String[] {"id"});
    when(punto.getArgs()).thenReturn(new Object[] {versionId});
    when(punto.proceed()).thenReturn(List.of("respuesta"));
    when(snapshots.cargar(gt.usac.cunoc.sgp.formulario.entity.VersionFormulario.class, versionId))
        .thenReturn(anteriores, posteriores);

    Object resultado = aspect.auditar(punto, anotacion("modificarConfiguracion", UUID.class));

    assertEquals(List.of("respuesta"), resultado);

    verify(escritor)
        .registrar(
            eq(USUARIO_ID),
            eq(AccionAuditoria.MODIFICAR),
            eq("version_formulario"),
            eq(versionId),
            eq(anteriores),
            eq(posteriores),
            eq(""));

    verify(snapshots, org.mockito.Mockito.times(2))
        .cargar(gt.usac.cunoc.sgp.formulario.entity.VersionFormulario.class, versionId);

    verify(snapshots, org.mockito.Mockito.never()).serializar(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void configuracionRechazadaNoRecargaElEstadoPosteriorNiEscribeAuditoria() throws Throwable {
    autenticar();

    UUID versionId = UUID.randomUUID();
    var anteriores = new ObjectMapper().readTree("{\"pesos\":[]}");

    when(firma.getParameterNames()).thenReturn(new String[] {"id"});
    when(punto.getArgs()).thenReturn(new Object[] {versionId});
    when(snapshots.cargar(gt.usac.cunoc.sgp.formulario.entity.VersionFormulario.class, versionId))
        .thenReturn(anteriores);

    when(punto.proceed())
        .thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                "version_inmutable",
                "Version inmutable",
                "La version publicada no puede modificarse."));

    ApiException error =
        assertThrows(
            ApiException.class,
            () -> aspect.auditar(punto, anotacion("modificarConfiguracion", UUID.class)));

    assertEquals("version_inmutable", error.getCode());

    verify(snapshots)
        .cargar(gt.usac.cunoc.sgp.formulario.entity.VersionFormulario.class, versionId);

    verifyNoInteractions(escritor);
  }
}
