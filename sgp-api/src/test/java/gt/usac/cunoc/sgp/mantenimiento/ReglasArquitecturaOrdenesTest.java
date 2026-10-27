package gt.usac.cunoc.sgp.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;

import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoEjecucionRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

/**
 * Única puerta de entrada (DT-BE-07) y conservación de las órdenes (RN-MTO-09, DT-BD-11),
 * verificadas sobre el código del backend.
 */
class ReglasArquitecturaOrdenesTest {

  private static final Path CODIGO = Path.of("src/main/java/gt/usac/cunoc/sgp");
  private static final String PUERTA =
      "src/main/java/gt/usac/cunoc/sgp/mantenimiento/service/TransicionOrdenService.java";
  private static final String ENTIDAD =
      "src/main/java/gt/usac/cunoc/sgp/mantenimiento/entity/OrdenMantenimiento.java";

  @Test
  void soloElServicioDeTransicionesAplicaUnCambioDeEstado() {
    List<String> usos =
        archivosJava()
            .filter(archivo -> contenido(archivo).contains(".aplicarTransicion("))
            .map(Path::toString)
            .map(ruta -> ruta.replace('\\', '/'))
            .toList();

    assertThat(usos).containsExactly(PUERTA);
  }

  @Test
  void ningunControladorAccedeALasEntidadesNiRepositoriosDeOrdenes() {
    List<Path> controladores =
        archivosJava().filter(archivo -> archivo.toString().contains("controller")).toList();

    assertThat(controladores).isNotEmpty();
    for (Path controlador : controladores) {
      String fuente = contenido(controlador);
      assertThat(fuente)
          .as("%s no debe usar entidades ni repositorios de órdenes", controlador)
          .doesNotContain("gt.usac.cunoc.sgp.mantenimiento.entity")
          .doesNotContain("gt.usac.cunoc.sgp.mantenimiento.repository");
    }
  }

  @Test
  void laOrdenNoTieneOtroMetodoQueAsigneElEstado() {
    List<String> metodosConEstado =
        Arrays.stream(OrdenMantenimiento.class.getDeclaredMethods())
            .filter(metodo -> Modifier.isPublic(metodo.getModifiers()))
            .filter(
                metodo ->
                    metodo.getName().startsWith("set")
                        || Arrays.asList(metodo.getParameterTypes()).contains(EstadoOrden.class))
            .map(Method::getName)
            .toList();

    assertThat(metodosConEstado).containsExactly("aplicarTransicion");
    assertThat(contenido(Path.of(ENTIDAD))).doesNotContain("setEstado");
  }

  @Test
  void ningunRepositorioDeOrdenesPermiteBorrar() {
    for (Class<?> repositorio :
        List.of(
            OrdenMantenimientoRepository.class,
            HistorialEstadoOrdenRepository.class,
            OrdenMantenimientoEjecucionRepository.class)) {
      assertThat(CrudRepository.class.isAssignableFrom(repositorio))
          .as("%s no debe heredar operaciones de borrado", repositorio.getSimpleName())
          .isFalse();
      assertThat(JpaRepository.class.isAssignableFrom(repositorio)).isFalse();
      assertThat(repositorio.getMethods())
          .extracting(Method::getName)
          .noneMatch(nombre -> nombre.startsWith("delete") || nombre.startsWith("remove"));
    }
  }

  @Test
  void elHistorialDeEstadosEsInmutable() {
    assertThat(HistorialEstadoOrden.class.isAnnotationPresent(Immutable.class)).isTrue();
  }

  private static Stream<Path> archivosJava() {
    try (Stream<Path> archivos = Files.walk(CODIGO)) {
      return archivos.filter(archivo -> archivo.toString().endsWith(".java")).toList().stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String contenido(Path archivo) {
    try {
      return Files.readString(archivo);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
