package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapperImpl;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.service.*;
import gt.usac.cunoc.sgp.notificacion.mapper.NotificacionMapperImpl;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.mapper.RevisionResultadoMapperImpl;
import gt.usac.cunoc.sgp.revision.model.VeredictoRevision;
import gt.usac.cunoc.sgp.revision.service.VeredictoRevisionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TransicionInspeccionService.class,
  ReglasEvidenciaFotografica.class,
  PublicacionIndiceCondicionService.class,
  ConfirmacionEstadoIcService.class,
  PreparadorEntradaIcService.class,
  CalculoIndiceCondicionService.class,
  ResolverElementosIc.class,
  ConversorIndicadoresIc.class,
  ConversorMedicionesIc.class,
  AgregadorCalificacionesIc.class,
  EvaluadorCondicionesCriticasIc.class,
  HuellaEntradaIc.class,
  MotorPonderacionIc.class,
  AuditoriaWriter.class,
  InspeccionMapperImpl.class,
  JsonSchemaValidatorService.class,
  VeredictoRevisionService.class,
  gt.usac.cunoc.sgp.revision.service.ObservacionService.class,
  gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario.class,
  gt.usac.cunoc.sgp.inspeccion.validation.ValidadorCategoriaFotografica.class,
  gt.usac.cunoc.sgp.revision.service.AtencionObservacionesService.class,
  gt.usac.cunoc.sgp.revision.service.CicloRevisionService.class,
  gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService.class,
  gt.usac.cunoc.sgp.revision.mapper.ObservacionMapperImpl.class,
  RevisionResultadoMapperImpl.class,
  NotificacionService.class,
  NotificacionMapperImpl.class,
  VeredictoRevisionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class VeredictoRevisionIntegrationTest {
  private static final Instant AHORA = Instant.parse("2026-10-17T04:00:00Z");
  private static final String ALGORITMO = "ic-integracion-prueba-v1";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_publicacion_real_test")
          .withUsername("sgp_test")
          .withPassword("sgp_test");

  @DynamicPropertySource
  static void configurarBase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private TransicionInspeccionService transiciones;
  @Autowired private ConfirmacionEstadoIcService confirmaciones;
  @Autowired private VeredictoRevisionService veredictos;
  @Autowired private gt.usac.cunoc.sgp.revision.repository.RevisionRepository revisiones;
  @Autowired private gt.usac.cunoc.sgp.revision.service.AtencionObservacionesService atencion;
  @Autowired private gt.usac.cunoc.sgp.revision.service.ObservacionService observacionesService;
  private UUID cicloId;
  private UUID revisorId;

  private UUID autorId;
  private UUID puenteId;
  private UUID versionId;
  private UUID inspeccionId;

  @BeforeEach
  void preparar() throws Exception {

    autorId = UUID.randomUUID();
    puenteId = UUID.randomUUID();
    versionId = UUID.randomUUID();
    inspeccionId = UUID.randomUUID();
    crearUsuario(autorId, "CATEDRATICO");
    autenticar(autorId, RoleName.CATEDRATICO);

    JsonNode catalogo =
        json.readTree(
            """
        {
          "formulario":"sieca-v1",
          "elementos":[{
            "elemento_ref":"/vigas", "repetible":false,
            "portante":true, "indicadores":["dano"]
          }],
          "reglas_evaluacion_ic":{
            "version_algoritmo":"ic-integracion-prueba-v1",
            "escala_severidad":{"NO_PRESENTA":0,"LEVE":1,"MODERADO":2,"SEVERO":4},
            "agregacion":{
              "indicadores_de_un_elemento":"MAXIMO",
              "instancias_repetibles":"MAXIMO",
              "elementos_ausentes_no_portantes":4,
              "elementos_ausentes_portantes":4
            },
            "rangos_estado":{"BUENO":75,"REGULAR":50,"MALO":0},
            "conversion_indicadores":{"booleanos":{},"enumerados":{},"numericos":{}},
            "reglas_anulacion":[{
              "id":"portante-cortado", "descripcion":"Elemento portante cortado",
              "estado_resultante":"MALO", "elementos":["/vigas"],
              "condicion":{"campo":"cortado","igual_a":"SI"}
            }]
          }
        }
        """);
    String esquema =
        """
        {"type":"object","required":["vigas"],"properties":{
          "vigas":{"type":"object","required":["existencia","dano"],"properties":{
            "existencia":{"enum":["PRESENTE","AUSENTE","NO_APLICA"]},
            "dano":{"enum":["NO_PRESENTA","LEVE","MODERADO","SEVERO"]}
          }}
        }}
        """;
    jdbc.update(
        """
        INSERT INTO version_formulario (id,codigo,estado,activa,esquema,
          reglas_evaluacion_ic,hash_contenido,version_algoritmo_ic,
          creado_por_id,publicado_por_id,publicado_en)
        VALUES (?,?,'PUBLICADA',false,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?)
        """,
        versionId,
        "IT-" + versionId,
        esquema,
        catalogo.toString(),
        "a".repeat(64),
        ALGORITMO,
        autorId,
        autorId,
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));
    jdbc.update(
        """
        INSERT INTO configuracion_ic_elemento (id,version_formulario_id,elemento_ref,peso,descripcion)
        VALUES (?,?,'/vigas',1,'Peso exclusivo de prueba')
        """,
        UUID.randomUUID(),
        versionId);
    int correlativo =
        jdbc.queryForObject(
            """
        SELECT COALESCE(MAX(p.correlativo_municipal),0)+1 FROM puente p
        JOIN municipio m ON m.id=p.municipio_id WHERE m.codigo_ine='0114'
        """,
            Integer.class);
    jdbc.update(
        """
        INSERT INTO puente (id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id)
        SELECT ?,?,?,id,'Puente integracion real','CA-1',
          ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,?
        FROM municipio WHERE codigo_ine='0114'
        """,
        puenteId,
        "IT-" + puenteId,
        correlativo,
        autorId);
    jdbc.update(
        """
        INSERT INTO inspeccion (id,puente_id,version_formulario_id,autor_id,
          rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id,datos)
        VALUES (?,?,?,?,'CATEDRATICO','Autor de prueba',?,'equipo-prueba',
          '{"vigas":{"existencia":"PRESENTE","dano":"MODERADO","cortado":"NO"}}'::jsonb)
        """,
        inspeccionId,
        puenteId,
        versionId,
        autorId,
        LocalDate.of(2026, 10, 16));
    for (String categoria : List.of("ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE")) {
      UUID archivoId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO archivo (id,tipo_uso,categoria_foto,nombre_almacenado,
            clave_objeto_original,clave_objeto_miniatura,mime_real,tamano_bytes,checksum_sha256,
            ancho_px,alto_px,ubicacion_captura,capturado_en,subido_por_id,confirmado_en)
          VALUES (?,'FOTO_INSPECCION',?,?,?,?,'image/jpeg',100,?,10,10,
            ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,?,?,?)
          """,
          archivoId,
          categoria,
          archivoId + ".jpg",
          archivoId + ".jpg",
          archivoId + "-mini.jpg",
          "c".repeat(64),
          OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
          autorId,
          OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
      jdbc.update(
          "INSERT INTO archivo_inspeccion (archivo_id,tipo_uso,inspeccion_id) VALUES (?,'FOTO_INSPECCION',?)",
          archivoId,
          inspeccionId);
    }

    revisorId = UUID.randomUUID();
    crearUsuario(revisorId, "CATEDRATICO");
    jdbc.update(
        "update usuario set rol_id=(select id from rol where nombre='ESTUDIANTE') where id=?",
        autorId);
    jdbc.update(
        "update inspeccion set estado='EN_REVISION', rol_autor_creacion='ESTUDIANTE' where id=?",
        inspeccionId);
    cicloId = UUID.randomUUID();
    var snapshot =
        json.createObjectNode()
            .put("inspeccionId", inspeccionId.toString())
            .put("puenteId", puenteId.toString())
            .put("autorId", autorId.toString())
            .put("versionFormularioId", versionId.toString())
            .put("fechaInspeccion", "2026-10-16");
    snapshot.set(
        "datos",
        json.readTree(
            jdbc.queryForObject(
                "select datos::text from inspeccion where id=?", String.class, inspeccionId)));
    snapshot.putNull("identificacionCapturada");
    jdbc.update(
        "insert into revision(id,inspeccion_id,numero_ciclo,revisor_id,estado,enviada_en,version_inspeccion_enviada,contenido_enviado,hash_contenido) values(?,?,1,?,'EN_CURSO',CURRENT_TIMESTAMP,0,?::jsonb,?)",
        cicloId,
        inspeccionId,
        revisorId,
        snapshot.toString(),
        "a".repeat(64));
    autenticar(revisorId, RoleName.CATEDRATICO);
  }

  private void crearUsuario(UUID id, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (id,email,password_hash,rol_id,verificado,activado,activo,nombre_completo)
        SELECT ?,?,'hash-prueba',id,true,true,true,'Evaluador de prueba' FROM rol WHERE nombre=?
        """,
        id,
        id + "@ejemplo.com",
        rol);
  }

  private void autenticar(UUID id, RoleName rol) {
    var sesion =
        new UsernamePasswordAuthenticationToken(
            id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    sesion.setDetails(new JwtData(id + "@ejemplo.com", id, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(sesion);
  }

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  private EmitirVeredictoRequest solicitud(VeredictoRevision v) {
    return new EmitirVeredictoRequest(v, new BigDecimal("87.65"), "Observación del ciclo", null);
  }

  private String estado() {
    return jdbc.queryForObject(
        "select estado from inspeccion where id=?", String.class, inspeccionId);
  }

  private int cantidad(String tabla, String campo, UUID id) {
    return jdbc.queryForObject(
        "select count(*) from " + tabla + " where " + campo + "=?", Integer.class, id);
  }

  @Test
  void aprobacionPublicaConIcRealYNuncaExponeNotaEnAuditoriaOAvisos() {
    var r = veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.APROBADA));
    assertThat(r.calificacion()).isEqualByComparingTo("87.65");
    assertThat(r.estadoRevision()).isEqualTo("RESUELTA");
    assertThat(estado()).isEqualTo("PUBLICADA");
    assertThat(cantidad("resultado_ic", "inspeccion_id", inspeccionId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select inspeccion_vigente_id from puente where id=?", UUID.class, puenteId))
        .isEqualTo(inspeccionId);
    assertThat(
            jdbc.queryForObject(
                "select revision_id from historial_estado_inspeccion where inspeccion_id=?",
                UUID.class,
                inspeccionId))
        .isEqualTo(cicloId);
    String audit =
        jdbc.queryForObject(
            "select to_jsonb(a)::text from auditoria a where entidad='revision' and entidad_id=?",
            String.class,
            cicloId);
    assertThat(audit).doesNotContain("calificacion", "87.65");
    assertThat(
            jdbc.queryForObject(
                "select to_jsonb(n)::text from notificacion n where entidad_id=?",
                String.class,
                cicloId))
        .doesNotContain("87.65", "calificacion");
    autenticar(autorId, RoleName.ESTUDIANTE);
    assertThat(veredictos.resultado(cicloId, autorId).calificacion()).isEqualByComparingTo("87.65");
    assertThat(veredictos.historial(inspeccionId, autorId)).hasSize(1);
  }

  @Test
  void revisorPuedeAjustarEstadoConJustificacionYHashVigente() {
    var calculo = veredictos.estadoPuente(cicloId, revisorId).calculo();
    var request =
        new EmitirVeredictoRequest(
            VeredictoRevision.APROBADA,
            BigDecimal.TEN,
            null,
            new gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest(
                EstadoCondicion.BUENO, calculo.hashEntrada(), "Criterio del revisor"));
    veredictos.emitir(cicloId, revisorId, request);
    var ic =
        jdbc.queryForMap(
            "select estado_calculado,estado_confirmado,confirmado_por_id from resultado_ic where inspeccion_id=?",
            inspeccionId);
    assertThat(ic.get("estado_calculado")).isEqualTo("REGULAR");
    assertThat(ic.get("estado_confirmado")).isEqualTo("BUENO");
    assertThat(ic.get("confirmado_por_id")).isEqualTo(revisorId);
  }

  @Test
  void cambioSinJustificacionYHashAntiguoNoCierranElCiclo() {
    var calculo = veredictos.estadoPuente(cicloId, revisorId).calculo();
    for (var decision :
        List.of(
            new gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest(
                EstadoCondicion.BUENO, calculo.hashEntrada(), null),
            new gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest(
                EstadoCondicion.BUENO, "b".repeat(64), "Motivo"))) {
      assertThatThrownBy(
              () ->
                  veredictos.emitir(
                      cicloId,
                      revisorId,
                      new EmitirVeredictoRequest(
                          VeredictoRevision.APROBADA, BigDecimal.TEN, null, decision)))
          .isInstanceOf(ApiException.class);
      assertThat(estado()).isEqualTo("EN_REVISION");
    }
  }

  @Test
  void observacionesPendientesImpidenAprobar() {
    jdbc.update(
        "insert into observacion_revision(id,revision_id,contenido,creado_por_id) values(?,?,?,?)",
        UUID.randomUUID(),
        cicloId,
        "Revisar",
        revisorId);
    assertThatThrownBy(
            () -> veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.APROBADA)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("observaciones_pendientes"));
    assertThat(estado()).isEqualTo("EN_REVISION");
  }

  @Test
  void cambiosYRechazoRequierenGeneralYRechazoEsTerminal() {
    for (var v : List.of(VeredictoRevision.CAMBIOS_SOLICITADOS, VeredictoRevision.RECHAZADA))
      assertThatThrownBy(
              () ->
                  veredictos.emitir(
                      cicloId, revisorId, new EmitirVeredictoRequest(v, BigDecimal.TEN, " ", null)))
          .isInstanceOf(ApiException.class);
    veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.RECHAZADA));
    assertThat(estado()).isEqualTo("RECHAZADA");
    assertThatThrownBy(() -> transiciones.devolverABorrador(inspeccionId, autorId))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.APROBADA)))
        .isInstanceOf(ApiException.class);
    assertThat(cantidad("notificacion", "entidad_id", cicloId)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.01", "100.01", "1.001", "9999"})
  void rechazaCalificacionesFueraDeEscala(String valor) {
    assertThatThrownBy(
            () ->
                veredictos.emitir(
                    cicloId,
                    revisorId,
                    new EmitirVeredictoRequest(
                        VeredictoRevision.CAMBIOS_SOLICITADOS,
                        new BigDecimal(valor),
                        "Motivo",
                        null)))
        .isInstanceOf(ApiException.class);
    assertThat(estado()).isEqualTo("EN_REVISION");
  }

  @Test
  void privacidadPorRolYRegistro() {
    UUID otro = UUID.randomUUID();
    crearUsuario(otro, "CATEDRATICO");
    autenticar(otro, RoleName.CATEDRATICO);
    assertThatThrownBy(() -> veredictos.resultado(cicloId, otro)).isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> veredictos.emitir(cicloId, otro, solicitud(VeredictoRevision.RECHAZADA)))
        .isInstanceOf(ApiException.class);
    autenticar(autorId, RoleName.ESTUDIANTE);
    assertThatThrownBy(
            () -> veredictos.emitir(cicloId, autorId, solicitud(VeredictoRevision.APROBADA)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    autenticar(otro, RoleName.ADMINISTRADOR);
    assertThatThrownBy(() -> veredictos.resultado(cicloId, otro))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void falloDeCalculoRevierteVeredictoNotaHistorialAvisoYConfirmacion() {
    jdbc.update("delete from configuracion_ic_elemento where version_formulario_id=?", versionId);
    assertThatThrownBy(
            () -> veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.APROBADA)))
        .isInstanceOf(ApiException.class);
    assertThat(estado()).isEqualTo("EN_REVISION");
    assertThat(
            jdbc.queryForObject(
                "select calificacion from revision where id=?", BigDecimal.class, cicloId))
        .isNull();
    assertThat(cantidad("historial_estado_inspeccion", "inspeccion_id", inspeccionId)).isZero();
    assertThat(cantidad("notificacion", "entidad_id", cicloId)).isZero();
    assertThat(cantidad("confirmacion_estado_inspeccion", "inspeccion_id", inspeccionId)).isZero();
  }

  @Test
  void dosVeredictosSimultaneosProducenUnSoloCierre() throws Exception {
    var inicio = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> accion =
          () -> {
            autenticar(revisorId, RoleName.CATEDRATICO);
            try {
              inicio.await();
              veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.APROBADA));
              return true;
            } catch (ApiException e) {
              assertThat(e.getStatus().value()).isEqualTo(409);
              return false;
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = pool.submit(accion);
      var b = pool.submit(accion);
      inicio.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(cantidad("resultado_ic", "inspeccion_id", inspeccionId)).isEqualTo(1);
      assertThat(cantidad("notificacion", "entidad_id", cicloId)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private UUID observacionPendiente() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into observacion_revision(id,revision_id,elemento_ref,contenido,creado_por_id) values(?,?, '/vigas', 'Corregir dato',?)",
        id,
        cicloId,
        revisorId);
    return id;
  }

  private void asignarPuente() {
    UUID materia = UUID.randomUUID(),
        curso = UUID.randomUUID(),
        inscrito = UUID.randomUUID(),
        asignacion = UUID.randomUUID();
    jdbc.update(
        "insert into asignatura(id,codigo,nombre) values(?,?,'Materia')",
        materia,
        materia.toString());
    jdbc.update(
        "insert into curso(id,asignatura_id,periodo,catedratico_id,fecha_inicio,fecha_fin) values(?,?,'2026',?,'2026-01-01','2026-12-31')",
        curso,
        materia,
        revisorId);
    jdbc.update(
        "insert into curso_estudiante(id,curso_id,estudiante_id,seccion,inscrito_por_id) values(?,?,?,'A',?)",
        inscrito,
        curso,
        autorId,
        revisorId);
    jdbc.update(
        "insert into asignacion_puente(id,curso_estudiante_id,puente_id,asignado_por_id) values(?,?,?,?)",
        asignacion,
        inscrito,
        puenteId,
        revisorId);
    jdbc.update(
        "update inspeccion set asignacion_puente_id=? where id=?", asignacion, inspeccionId);
  }

  @Test
  void atenderResolverYReenviarConservaElCicloAnteriorYNotificaUnaVez() {
    asignarPuente();
    UUID obs = observacionPendiente();
    String snapshot =
        jdbc.queryForObject(
            "select contenido_enviado::text from revision where id=?", String.class, cicloId);
    veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.CAMBIOS_SOLICITADOS));
    autenticar(autorId, RoleName.ESTUDIANTE);
    assertThatThrownBy(
            () -> atencion.resolver(cicloId, obs, autorId, new ResolverObservacionRequest(null)))
        .isInstanceOf(ApiException.class);
    assertThat(atencion.atender(cicloId, autorId).estadoInspeccion()).isEqualTo("BORRADOR");
    assertThat(atencion.atender(cicloId, autorId).estadoInspeccion()).isEqualTo("BORRADOR");
    assertThatThrownBy(() -> transiciones.enviar(inspeccionId, autorId, "reenvio"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("observaciones_pendientes"));
    var resuelta =
        atencion.resolver(
            cicloId, obs, autorId, new ResolverObservacionRequest("Corregido en formulario"));
    assertThat(resuelta.estado().name()).isEqualTo("RESUELTA");
    assertThat(resuelta.resueltoPorId()).isEqualTo(autorId);
    assertThat(resuelta.resueltoEn()).isNotNull();
    assertThat(
            atencion.resolver(
                cicloId, obs, autorId, new ResolverObservacionRequest("Corregido en formulario")))
        .isEqualTo(resuelta);
    jdbc.update(
        "update inspeccion set datos=jsonb_set(datos,'{vigas,dano}','\"LEVE\"'::jsonb) where id=?",
        inspeccionId);
    var enviada = transiciones.enviar(inspeccionId, autorId, "reenvio");
    assertThat(transiciones.enviar(inspeccionId, autorId, "reenvio")).isEqualTo(enviada);
    assertThat(estado()).isEqualTo("ENVIADA");
    assertThat(cantidad("revision", "inspeccion_id", inspeccionId)).isEqualTo(2);
    var siguiente =
        jdbc.queryForMap(
            "select id,revisor_id,numero_ciclo,contenido_enviado::text,hash_contenido,calificacion from revision where inspeccion_id=? and numero_ciclo=2",
            inspeccionId);
    assertThat(siguiente.get("revisor_id")).isEqualTo(revisorId);
    assertThat(siguiente.get("contenido_enviado").toString()).contains("LEVE");
    assertThat(siguiente.get("hash_contenido").toString()).hasSize(64).isNotEqualTo("a".repeat(64));
    assertThat(siguiente.get("calificacion")).isNull();
    var bandeja =
        revisiones.buscarBandeja(
            revisorId,
            "",
            "",
            "",
            "",
            Instant.parse("2000-01-01T00:00:00Z"),
            Instant.parse("2100-01-01T00:00:00Z"),
            org.springframework.data.domain.PageRequest.of(0, 1));
    assertThat(bandeja.getTotalElements()).isEqualTo(1);
    assertThat(bandeja.getContent())
        .extracting(
            gt.usac.cunoc.sgp.revision.repository.RevisionRepository.RevisionBandejaProjection
                ::getRevisionId)
        .containsExactly((UUID) siguiente.get("id"));

    assertThat(cantidad("notificacion", "entidad_id", (UUID) siguiente.get("id"))).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select contenido_enviado::text from revision where id=?", String.class, cicloId))
        .isEqualTo(snapshot);
    assertThat(veredictos.historial(inspeccionId, autorId))
        .extracting(RevisionResultadoResponse::numeroCiclo)
        .containsExactly(2, 1);
    assertThat(veredictos.resultado(cicloId, autorId).calificacion()).isEqualByComparingTo("87.65");
    assertThatThrownBy(
            () ->
                atencion.resolver(
                    cicloId,
                    obs,
                    autorId,
                    new ResolverObservacionRequest("Corregido en formulario")))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void reenviarRevalidaFormularioYFotografiasYNoCreaCiclosSiFalla() {
    asignarPuente();
    veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.CAMBIOS_SOLICITADOS));
    autenticar(autorId, RoleName.ESTUDIANTE);
    atencion.atender(cicloId, autorId);
    jdbc.update("update inspeccion set datos='{}'::jsonb where id=?", inspeccionId);
    assertThatThrownBy(() -> transiciones.enviar(inspeccionId, autorId, "campos"))
        .isInstanceOf(ApiException.class);
    jdbc.update(
        "update inspeccion set datos='{\"vigas\":{\"existencia\":\"PRESENTE\",\"dano\":\"LEVE\"}}'::jsonb where id=?",
        inspeccionId);
    jdbc.update(
        "update archivo set estado='RETIRADO',retirado_en=CURRENT_TIMESTAMP where id in (select archivo_id from archivo_inspeccion where inspeccion_id=?)",
        inspeccionId);
    assertThatThrownBy(() -> transiciones.enviar(inspeccionId, autorId, "fotos"))
        .isInstanceOf(ApiException.class);
    assertThat(cantidad("revision", "inspeccion_id", inspeccionId)).isEqualTo(1);
    assertThat(estado()).isEqualTo("BORRADOR");
  }

  @Test
  void resolverExigeAutorEstadoYCicloCorrecto() {
    UUID obs = observacionPendiente();
    veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.CAMBIOS_SOLICITADOS));
    assertThatThrownBy(() -> atencion.atender(cicloId, revisorId))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    UUID otro = UUID.randomUUID();
    crearUsuario(otro, "ESTUDIANTE");
    autenticar(otro, RoleName.ESTUDIANTE);
    assertThatThrownBy(() -> atencion.atender(cicloId, otro)).isInstanceOf(ApiException.class);
    autenticar(autorId, RoleName.ESTUDIANTE);
    atencion.atender(cicloId, autorId);
    assertThatThrownBy(
            () ->
                atencion.resolver(
                    cicloId, UUID.randomUUID(), autorId, new ResolverObservacionRequest(null)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                atencion.resolver(
                    cicloId, obs, autorId, new ResolverObservacionRequest("x".repeat(2001))))
        .isInstanceOf(ApiException.class);
    atencion.resolver(cicloId, obs, autorId, new ResolverObservacionRequest(null));
    assertThatThrownBy(
            () ->
                atencion.resolver(
                    cicloId, obs, autorId, new ResolverObservacionRequest("Otra respuesta")))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void reenviosSimultaneosConMismaClaveCreanSoloUnCiclo() throws Exception {
    asignarPuente();
    veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.CAMBIOS_SOLICITADOS));
    autenticar(autorId, RoleName.ESTUDIANTE);
    atencion.atender(cicloId, autorId);
    var inicio = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<UUID> enviar =
          () -> {
            autenticar(autorId, RoleName.ESTUDIANTE);
            try {
              inicio.await();
              return transiciones.enviar(inspeccionId, autorId, "concurrente").id();
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = pool.submit(enviar);
      var b = pool.submit(enviar);
      inicio.countDown();
      assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
      assertThat(cantidad("revision", "inspeccion_id", inspeccionId)).isEqualTo(2);
    } finally {
      pool.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "100"})
  void admiteLosExtremosDeLaEscala(String nota) {
    assertThat(
            veredictos
                .emitir(
                    cicloId,
                    revisorId,
                    new EmitirVeredictoRequest(
                        VeredictoRevision.CAMBIOS_SOLICITADOS,
                        new BigDecimal(nota),
                        "Revisar",
                        null))
                .calificacion())
        .isEqualByComparingTo(nota);
  }

  @Test
  void unaObservacionConcurrenteNuncaQuedaPendienteEnUnCicloAprobado() throws Exception {
    var inicio = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var crear =
          pool.submit(
              () -> {
                autenticar(revisorId, RoleName.CATEDRATICO);
                try {
                  inicio.await();
                  observacionesService.crear(
                      cicloId,
                      revisorId,
                      new CrearObservacionRequest(
                          new DestinoObservacion(
                              gt.usac.cunoc.sgp.revision.model.TipoDestinoObservacion
                                  .CATEGORIA_FOTOGRAFICA,
                              null,
                              null,
                              gt.usac.cunoc.sgp.archivo.model.CategoriaFoto.ACCESO),
                          "Revisar evidencia"));
                  return true;
                } catch (ApiException e) {
                  assertThat(e.getStatus().value()).isIn(409, 422);
                  return false;
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var aprobar =
          pool.submit(
              () -> {
                autenticar(revisorId, RoleName.CATEDRATICO);
                try {
                  inicio.await();
                  veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.APROBADA));
                  return true;
                } catch (ApiException e) {
                  assertThat(e.getStatus().value()).isIn(409, 422);
                  return false;
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      inicio.countDown();
      assertThat(List.of(crear.get(20, TimeUnit.SECONDS), aprobar.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      if ("PUBLICADA".equals(estado()))
        assertThat(cantidad("observacion_revision", "revision_id", cicloId)).isZero();
      else {
        assertThat(estado()).isEqualTo("EN_REVISION");
        assertThat(cantidad("observacion_revision", "revision_id", cicloId)).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void ciclosTerminalesImpidenAtenderYResolver() {
    UUID obs = observacionPendiente();
    veredictos.emitir(cicloId, revisorId, solicitud(VeredictoRevision.RECHAZADA));
    autenticar(autorId, RoleName.ESTUDIANTE);
    assertThatThrownBy(() -> atencion.atender(cicloId, autorId)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> atencion.resolver(cicloId, obs, autorId, null))
        .isInstanceOf(ApiException.class);
    assertThat(estado()).isEqualTo("RECHAZADA");
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper()
          .findAndRegisterModules()
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }
}
