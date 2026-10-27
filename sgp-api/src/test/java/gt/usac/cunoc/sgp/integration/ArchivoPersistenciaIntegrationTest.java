package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import gt.usac.cunoc.sgp.archivo.entity.*;
import gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapperImpl;
import gt.usac.cunoc.sgp.archivo.model.*;
import gt.usac.cunoc.sgp.archivo.repository.*;
import gt.usac.cunoc.sgp.archivo.service.*;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RegistroFotografiasInspeccionJpa.class, FotografiaMapperImpl.class})
@Testcontainers
class ArchivoPersistenciaIntegrationTest {
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
  @Autowired ArchivoRepository archivos;
  @Autowired ArchivoInspeccionRepository vinculos;
  @Autowired InspeccionRepository inspecciones;
  @Autowired RegistroFotografiasInspeccion registro;
  @Autowired gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapper mapper;

  @Test
  void persisteGeografiaAsociacionYRetiradaSinCambiarFecha() {
    UUID usuario = UUID.randomUUID(),
        version = UUID.randomUUID(),
        puente = UUID.randomUUID(),
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
    var i = inspecciones.findByIdAndEliminadoEnIsNullForUpdate(inspeccion).orElseThrow();
    var punto =
        new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(-90, 14));
    Instant ahora = Instant.parse("2026-10-15T12:00:00Z");
    UUID id = UUID.randomUUID();
    var archivo =
        new Archivo(
            id,
            TipoUsoArchivo.FOTO_INSPECCION,
            CategoriaFoto.ACCESO,
            "test.jpg",
            "original.jpg",
            "test/" + id,
            "test/mini-" + id,
            "image/jpeg",
            123,
            "a".repeat(64),
            800,
            600,
            punto,
            ahora,
            usuario,
            ahora);
    archivos.saveAndFlush(archivo);
    vinculos.saveAndFlush(new ArchivoInspeccion(archivo, i, null, null, ahora));
    em.clear();
    assertThat(registro.consultar(inspeccion))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.id()).isEqualTo(id);
              assertThat(f.categoria()).isEqualTo(CategoriaFoto.ACCESO);
            });
    assertThat(archivos.findById(id).orElseThrow().getUbicacionCaptura().getX()).isEqualTo(-90);
    assertThat(registro.buscar(UUID.randomUUID(), id)).isEmpty();
    var respuesta = mapper.toResponse(vinculos.findById(id).orElseThrow());
    assertThat(respuesta.id()).isEqualTo(id);
    assertThat(respuesta.latitud()).isEqualTo(14);
    assertThat(respuesta.longitud()).isEqualTo(-90);
    assertThat(respuesta.mimeReal()).isEqualTo("image/jpeg");
    registro.retirar(inspeccion, id, ahora);
    registro.retirar(inspeccion, id, ahora.plusSeconds(30));
    em.flush();
    em.clear();
    assertThat(archivos.findById(id).orElseThrow().getRetiradoEn()).isEqualTo(ahora);
    assertThat(registro.buscar(inspeccion, id).orElseThrow().estado())
        .isEqualTo(EstadoArchivo.RETIRADO);
  }
}
