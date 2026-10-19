package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Comprueba, contra PostgreSQL real, que la entidad coincide con la tabla de V10. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrdenMantenimientoPersistenciaIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.url", postgres::getJdbcUrl);
    r.add("spring.flyway.user", postgres::getUsername);
    r.add("spring.flyway.password", postgres::getPassword);
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager em;
  @Autowired OrdenMantenimientoRepository ordenes;

  private UUID usuario;
  private UUID puente;
  private UUID inspeccion;

  @BeforeEach
  void prepararPuenteEInspeccion() {
    usuario = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    puente = UUID.randomUUID();
    inspeccion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id) SELECT ?,?,'test',id FROM rol WHERE nombre='CATEDRATICO'",
        usuario,
        usuario + "@test.com");
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,esquema,creado_por_id) VALUES (?,?,'{}',?)",
        version,
        "test-" + version,
        usuario);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) SELECT ?,?,1,id,'test','test',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? FROM municipio WHERE codigo_ine='0114'",
        puente,
        "T-" + puente,
        usuario);
    jdbc.update(
        "INSERT INTO inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id) VALUES (?,?,?,?,'CATEDRATICO','test',CURRENT_DATE,'test')",
        inspeccion,
        puente,
        version,
        usuario);
  }

  @Test
  void persisteUnaPropuestaGeneradaConSuFundamentoYSusValoresIniciales() throws Exception {
    JsonNode fundamento =
        MAPPER.readTree("{\"regla\":\"REG-COR-001\",\"valorEncontrado\":\"SEVERO\"}");
    UUID instancia = UUID.randomUUID();

    OrdenMantenimiento propuesta =
        OrdenMantenimiento.nuevaPropuesta(
            puente,
            inspeccion,
            null,
            null,
            "/subestructura/pilas/socavacion",
            instancia,
            "REG-COR-001:/subestructura/pilas/socavacion:" + instancia,
            TipoMantenimiento.CORRECTIVO,
            "Reparar la viga de apoyo.",
            PrioridadMantenimiento.ALTA,
            fundamento,
            null);

    ordenes.saveAndFlush(propuesta);
    em.clear();

    OrdenMantenimiento leida = ordenes.findById(propuesta.getId()).orElseThrow();
    assertThat(leida.getEstado()).isEqualTo(EstadoOrdenMantenimiento.PROPUESTA);
    assertThat(leida.getPuenteId()).isEqualTo(puente);
    assertThat(leida.getInspeccionOrigenId()).isEqualTo(inspeccion);
    assertThat(leida.getInstanciaElementoId()).isEqualTo(instancia);
    assertThat(leida.getTipo()).isEqualTo(TipoMantenimiento.CORRECTIVO);
    assertThat(leida.getPrioridadSugerida()).isEqualTo(PrioridadMantenimiento.ALTA);
    assertThat(leida.getPrioridadConfirmada()).isEqualTo(PrioridadMantenimiento.ALTA);
    assertThat(leida.getFundamentoGeneracion()).isEqualTo(fundamento);
    assertThat(leida.getCreadoPorId()).isNull();
    assertThat(leida.getCreadoEn()).isNotNull();
    assertThat(leida.getActualizadoEn()).isNotNull();
    assertThat(leida.getVersion()).isZero();
  }

  @Test
  void permiteDosOrdenesManualesSinInspeccionNiClaveDeGeneracion() {
    ordenes.saveAndFlush(manual("Limpiar el cauce."));
    ordenes.saveAndFlush(manual("Pintar la señalización."));

    assertThat(ordenes.count()).isEqualTo(2);
  }

  @Test
  void rechazaGenerarDosVecesLaMismaClaveParaLaMismaInspeccion() {
    ordenes.saveAndFlush(generada("clave-1"));

    assertThatThrownBy(() -> ordenes.saveAndFlush(generada("clave-1")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private OrdenMantenimiento manual(String descripcion) {
    return OrdenMantenimiento.nuevaPropuesta(
        puente,
        null,
        null,
        null,
        null,
        null,
        null,
        TipoMantenimiento.RUTINARIO,
        descripcion,
        PrioridadMantenimiento.BAJA,
        null,
        usuario);
  }

  private OrdenMantenimiento generada(String clave) {
    return OrdenMantenimiento.nuevaPropuesta(
        puente,
        inspeccion,
        null,
        null,
        "/otros/drenajes_aledanos",
        null,
        clave,
        TipoMantenimiento.RUTINARIO,
        "Desobstruir drenajes.",
        PrioridadMantenimiento.BAJA,
        null,
        null);
  }
}
