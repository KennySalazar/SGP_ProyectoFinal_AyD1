package gt.usac.cunoc.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prueba automatizada del inventario completo de endpoints (DT-SEC-03, DT-CAL-03).
 *
 * <p>Recorre todos los controladores REST registrados en el paquete de la aplicación y verifica que
 * cada método que expone un endpoint declare explícitamente su autorización (@PreAuthorize) o
 * pertenezca a la lista de endpoints públicos autorizados.
 */
class EndpointsAuthorizationScanTest {

  private static final String APP_PACKAGE = "gt.usac.cunoc.sgp";

  /** Endpoints públicos declarados explícitamente por especificación funcional. */
  private static final Set<String> ENDPOINTS_PUBLICOS_ESPECIFICADOS =
      Set.of(
          "/api/v1/auth/register",
          "/api/v1/auth/register/resend",
          "/api/v1/auth/register/verify",
          "/api/v1/auth/login",
          "/api/v1/auth/login/verify",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout",
          "/api/v1/auth/password-recovery",
          "/api/v1/auth/password-recovery/verify",
          "/api/v1/invitaciones/validacion",
          "/api/v1/invitaciones/aceptacion",
          "/api/v1/puentes",
          "/api/v1/puentes/{id}",
          "/api/v1/catalogos/departamentos",
          "/api/v1/puentes/{puenteId}/hilos",
          "/api/v1/inspecciones/{inspeccionId}/hilo",
          "/api/v1/hilos/{hiloId}",
          "/api/v1/hilos/{hiloId}/comentarios");

  record EndpointDescriptor(
      String httpMethod,
      String fullPath,
      String controllerClass,
      String methodName,
      String preAuthorizeExpression,
      boolean isPublicWhitelisted) {}

  @Test
  @DisplayName("DT-SEC-03: Todo endpoint de la API declara @PreAuthorize o está en lista pública")
  void todosLosEndpointsDeclaranAutorizacionOEstanEnListaPublica() throws ClassNotFoundException {
    List<Class<?>> controllers = findControllers();
    assertThat(controllers).isNotEmpty();

    List<EndpointDescriptor> inventario = new ArrayList<>();
    List<String> infracciones = new ArrayList<>();

    for (Class<?> controllerClass : controllers) {
      String basePath = getBasePath(controllerClass);
      PreAuthorize classPreAuthorize =
          AnnotatedElementUtils.findMergedAnnotation(controllerClass, PreAuthorize.class);

      for (Method method : controllerClass.getDeclaredMethods()) {
        MappingInfo mapping = getMappingInfo(method, basePath);
        if (mapping == null) {
          continue;
        }

        PreAuthorize methodPreAuthorize =
            AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        String effectivePreAuthorize = null;
        if (methodPreAuthorize != null) {
          effectivePreAuthorize = methodPreAuthorize.value();
        } else if (classPreAuthorize != null) {
          effectivePreAuthorize = classPreAuthorize.value();
        }

        boolean whitelisted = ENDPOINTS_PUBLICOS_ESPECIFICADOS.contains(mapping.path());

        inventario.add(
            new EndpointDescriptor(
                mapping.httpMethod(),
                mapping.path(),
                controllerClass.getSimpleName(),
                method.getName(),
                effectivePreAuthorize != null ? effectivePreAuthorize : "NINGUNA",
                whitelisted));

        if (effectivePreAuthorize == null && !whitelisted) {
          infracciones.add(
              String.format(
                  "Endpoint sin autorizacion: %s %s en %s#%s",
                  mapping.httpMethod(),
                  mapping.path(),
                  controllerClass.getSimpleName(),
                  method.getName()));
        }
      }
    }

    assertThat(infracciones)
        .withFailMessage(
            "Se encontraron endpoints sin declaracion de autorizacion (@PreAuthorize): %s",
            infracciones)
        .isEmpty();

    // Confirmar que se inventariaron todos los endpoints de los 32 controladores (más de 50
    // endpoints)
    assertThat(inventario.size()).isGreaterThanOrEqualTo(50);
  }

  @Test
  @DisplayName("DT-SEC-03: No existen expresiones @PreAuthorize vacías o en blanco")
  void expresionesPreAuthorizeSonValidas() throws ClassNotFoundException {
    List<Class<?>> controllers = findControllers();

    for (Class<?> controllerClass : controllers) {
      PreAuthorize classPreAuthorize =
          AnnotatedElementUtils.findMergedAnnotation(controllerClass, PreAuthorize.class);
      if (classPreAuthorize != null) {
        assertThat(classPreAuthorize.value().trim())
            .withFailMessage("Clase %s tiene @PreAuthorize vacio", controllerClass.getSimpleName())
            .isNotEmpty();
      }

      for (Method method : controllerClass.getDeclaredMethods()) {
        PreAuthorize methodPreAuthorize =
            AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (methodPreAuthorize != null) {
          assertThat(methodPreAuthorize.value().trim())
              .withFailMessage(
                  "Metodo %s#%s tiene @PreAuthorize vacio",
                  controllerClass.getSimpleName(), method.getName())
              .isNotEmpty();
        }
      }
    }
  }

  private List<Class<?>> findControllers() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<Class<?>> controllers = new ArrayList<>();
    for (BeanDefinition bd : scanner.findCandidateComponents(APP_PACKAGE)) {
      Class<?> clazz = ClassUtils.forName(bd.getBeanClassName(), getClass().getClassLoader());
      controllers.add(clazz);
    }
    return controllers;
  }

  private String getBasePath(Class<?> clazz) {
    RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
    if (rm != null && rm.value().length > 0) {
      return rm.value()[0];
    }
    return "";
  }

  private record MappingInfo(String httpMethod, String path) {}

  private MappingInfo getMappingInfo(Method method, String basePath) {
    GetMapping get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
    if (get != null) {
      return new MappingInfo("GET", combine(basePath, get.value()));
    }
    PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
    if (post != null) {
      return new MappingInfo("POST", combine(basePath, post.value()));
    }
    PutMapping put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
    if (put != null) {
      return new MappingInfo("PUT", combine(basePath, put.value()));
    }
    PatchMapping patch = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
    if (patch != null) {
      return new MappingInfo("PATCH", combine(basePath, patch.value()));
    }
    DeleteMapping delete = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
    if (delete != null) {
      return new MappingInfo("DELETE", combine(basePath, delete.value()));
    }
    RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    if (rm != null && rm.method().length > 0) {
      return new MappingInfo(rm.method()[0].name(), combine(basePath, rm.value()));
    }
    return null;
  }

  private String combine(String basePath, String[] paths) {
    String sub = (paths != null && paths.length > 0) ? paths[0] : "";
    if (basePath.isEmpty()) {
      return sub;
    }
    if (sub.isEmpty()) {
      return basePath;
    }
    if (basePath.endsWith("/") && sub.startsWith("/")) {
      return basePath + sub.substring(1);
    }
    if (!basePath.endsWith("/") && !sub.startsWith("/")) {
      return basePath + "/" + sub;
    }
    return basePath + sub;
  }
}
