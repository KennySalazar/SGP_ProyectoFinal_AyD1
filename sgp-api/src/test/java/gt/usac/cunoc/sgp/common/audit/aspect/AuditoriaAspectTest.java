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

  private Auditable anotacion(String metodo) throws NoSuchMethodException {
    Method metodoReal = ServicioAuditable.class.getMethod(metodo);
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
  }
}
