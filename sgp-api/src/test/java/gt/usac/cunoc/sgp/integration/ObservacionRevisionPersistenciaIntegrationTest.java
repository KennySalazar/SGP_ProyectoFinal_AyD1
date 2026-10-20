package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.revision.entity.ObservacionRevision;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ObservacionRevisionPersistenciaIntegrationTest {
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

  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager em;
  @Autowired ObservacionRevisionRepository observaciones;
  @Autowired RevisionRepository revisiones;
  private UUID creador;
  private Revision ciclo;
  private Revision otroCiclo;
  private static final Instant FECHA = Instant.parse("2026-10-18T12:00:00Z");

  @BeforeEach
  void preparar() {
    creador = UUID.randomUUID();
    UUID version = UUID.randomUUID(), puente = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id) SELECT ?,?,'test',id FROM rol WHERE nombre='CATEDRATICO'",
        creador,
        creador + "@hu037.test");
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,esquema,creado_por_id) VALUES (?,?,'{}',?)",
        version,
        "HU037-" + version,
        creador);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) SELECT ?,?,1,id,'test','test',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? FROM municipio WHERE codigo_ine='0114'",
        puente,
        "T-" + puente,
        creador);
    ciclo = crearCiclo(version, puente);
    otroCiclo = crearCiclo(version, puente);
  }

  private Revision crearCiclo(UUID version, UUID puente) {
    UUID inspeccion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id) VALUES (?,?,?,?,'CATEDRATICO','test',CURRENT_DATE,'test')",
        inspeccion,
        puente,
        version,
        creador);
    return revisiones.saveAndFlush(
        new Revision(
            UUID.randomUUID(),
            em.getReference(Inspeccion.class, inspeccion),
            1,
            creador,
            FECHA,
            0,
            JsonNodeFactory.instance.objectNode(),
            "a".repeat(64)));
  }

  private ObservacionRevision elemento(Revision revision) {
    return ObservacionRevision.paraElemento(
        UUID.randomUUID(),
        revision,
        "/tramos/longitud_m",
        UUID.fromString("01900000-0000-7000-8000-000000000001"),
        "Texto",
        creador,
        FECHA);
  }

  @Test
  void persisteElementoYRecuperaIdentidadSinAlterarUuid() {
    var original = observaciones.saveAndFlush(elemento(ciclo));
    UUID cicloId = ciclo.getId();
    em.clear();
    var guardada = observaciones.findByIdAndRevisionId(original.getId(), cicloId).orElseThrow();
    assertThat(guardada.getRevision().getId()).isEqualTo(cicloId);
    assertThat(guardada.getElementoRef()).isEqualTo(original.getElementoRef());
    assertThat(guardada.getInstanciaElementoId()).isEqualTo(original.getInstanciaElementoId());
    assertThat(guardada.getContenido()).isEqualTo("Texto");
    assertThat(guardada.getEstado()).isEqualTo(EstadoObservacion.PENDIENTE);
    assertThat(guardada.getCreadoPorId()).isEqualTo(creador);
    assertThat(guardada.getCreadoEn()).isEqualTo(FECHA);
    assertThat(guardada.getCategoriaFotografica()).isNull();
    assertThat(observaciones.findByIdAndRevisionId(original.getId(), otroCiclo.getId())).isEmpty();
  }

  @Test
  void persisteCategoriaSinReferenciaDeFormulario() {
    var categoria =
        observaciones.saveAndFlush(
            ObservacionRevision.paraCategoriaFotografica(
                UUID.randomUUID(), ciclo, CategoriaFoto.CAUCE, "Ver fotografías", creador, FECHA));
    em.clear();
    var guardada = observaciones.findById(categoria.getId()).orElseThrow();
    assertThat(guardada.getCategoriaFotografica()).isEqualTo(CategoriaFoto.CAUCE);
    assertThat(guardada.getElementoRef()).isNull();
    assertThat(guardada.getInstanciaElementoId()).isNull();
  }

  @Test
  void listaYCuentaPorCicloSinConfundirRevisionesPendientes() {
    var primera = observaciones.saveAndFlush(elemento(ciclo));
    var segunda = observaciones.saveAndFlush(elemento(ciclo));
    observaciones.saveAndFlush(elemento(otroCiclo));
    jdbc.update(
        "UPDATE observacion_revision SET estado='RESUELTA',resuelto_por_id=?,resuelto_en=? WHERE id=?",
        creador,
        java.sql.Timestamp.from(FECHA),
        primera.getId());
    em.clear();
    assertThat(observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(ciclo.getId()))
        .extracting(ObservacionRevision::getId)
        .containsExactlyInAnyOrder(primera.getId(), segunda.getId());
    assertThat(observaciones.countByRevisionId(ciclo.getId())).isEqualTo(2);
    assertThat(observaciones.countByRevisionIdAndEstado(ciclo.getId(), EstadoObservacion.PENDIENTE))
        .isEqualTo(1);
    assertThat(observaciones.countByRevisionId(otroCiclo.getId())).isEqualTo(1);
    assertThat(observaciones.countByRevisionId(UUID.randomUUID())).isZero();
  }

  @Test
  void generalPermaneceEnRevisionSinCrearObservacionPuntual() {
    jdbc.update(
        "UPDATE revision SET observacion_general=? WHERE id=?", "General del ciclo", ciclo.getId());
    assertThat(
            jdbc.queryForObject(
                "SELECT observacion_general FROM revision WHERE id=?", String.class, ciclo.getId()))
        .isEqualTo("General del ciclo");
    assertThat(observaciones.countByRevisionId(ciclo.getId())).isZero();
  }

  @Test
  void conservaFilasPermitidasPorV9InclusoSinReferencia() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO observacion_revision(id,revision_id,contenido,creado_por_id) VALUES (?,?,?,?)",
        id,
        ciclo.getId(),
        "Registro V9",
        creador);
    em.clear();
    var legado = observaciones.findById(id).orElseThrow();
    assertThat(legado.getElementoRef()).isNull();
    assertThat(legado.getCategoriaFotografica()).isNull();
    assertThat(legado.getEstado()).isEqualTo(EstadoObservacion.PENDIENTE);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "estado='DESCONOCIDO'",
        "estado='RESUELTA'",
        "elemento_ref=NULL",
        "categoria_fotografica='NO_EXISTE'",
        "categoria_fotografica='ACCESO'",
        "contenido=NULL",
        "revision_id='00000000-0000-0000-0000-000000000000'",
        "creado_por_id='00000000-0000-0000-0000-000000000000'"
      })
  void baseRechazaViolacionesDeV9YNuevaCategoria(String cambio) {
    var observacion = observaciones.saveAndFlush(elemento(ciclo));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE observacion_revision SET " + cambio + " WHERE id=?",
                    observacion.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
