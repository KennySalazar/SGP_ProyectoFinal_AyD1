package gt.usac.cunoc.sgp.inspeccion.bdd;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.inspeccion.dto.AdjuntarFotografiaRequest;
import gt.usac.cunoc.sgp.inspeccion.service.EvidenciaFotograficaService;
import gt.usac.cunoc.sgp.inspeccion.service.RetiradaFotografiaService;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.es.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class Hu029Steps {
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JwtService jwt;
  @Autowired UserAccountRepository usuarios;
  @Autowired AlmacenamientoArchivos almacenamiento;
  @Autowired RetiradaFotografiaService retirada;
  @Autowired EvidenciaFotograficaService evidencia;
  @Autowired PlatformTransactionManager transacciones;

  private UUID autor, actor, inspeccion;
  private final Map<String, UUID> fotografias = new HashMap<>();
  private MvcResult respuesta;
  private JsonNode cuerpo;
  private Instant actividadOriginal, retiradaOriginal;
  private Throwable fallo;
  private AdjuntarFotografiaRequest cargaOriginal;
  private byte[] imagenOriginal;
  private String claveOriginal, objetoOriginal, objetoMiniatura;

  @Before
  public void prepararEscenario() {
    autor = UUID.randomUUID();
    fotografias.clear();
  }

  @Dado("una inspección propia en estado {string}")
  public void inspeccionPropia(String estado) {
    crearUsuario(autor, "CATEDRATICO");
    inspeccion = crearInspeccion(estado);
    actividadOriginal = actividad();
  }

  @Dado("una sesión de {string} como {string}")
  public void sesion(String rol, String relacion) {
    if (relacion.equals("visitante")) {
      actor = null;
      return;
    }
    actor = relacion.equals("autor") ? autor : UUID.randomUUID();
    crearUsuario(actor, rol);
    if (relacion.equals("asignado")) asignarCatedratico(actor);
  }

  @Dado("las siguientes fotografías registradas:")
  public void inventario(DataTable tabla) {
    for (var fila : tabla.asMaps()) {
      UUID foto = crearFotografia(inspeccion, fila.get("categoria"), fila.get("estado"));
      fotografias.putIfAbsent(fila.get("categoria"), foto);
    }
  }

  @Dado("evidencia completa con una fotografía disponible por categoría obligatoria")
  public void evidenciaCompleta() {
    for (String categoria : List.of("ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE")) {
      fotografias.put(categoria, crearFotografia(inspeccion, categoria, "DISPONIBLE"));
    }
  }

  @Dado("{int} fotografías disponibles adicionales en OTRA")
  public void fotografiasAdicionales(int cantidad) {
    for (int i = 0; i < cantidad; i++) crearFotografia(inspeccion, "OTRA", "DISPONIBLE");
  }

  @Dado("una fotografía disponible de otra inspección")
  public void fotografiaAjena() {
    fotografias.put("AJENA", crearFotografia(crearInspeccion("BORRADOR"), "CAUCE", "DISPONIBLE"));
  }

  @Dado("un documento PDF asociado a la inspección")
  public void documento() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into archivo(id,tipo_uso,nombre_almacenado,clave_objeto_original,mime_real,
          tamano_bytes,checksum_sha256,subido_por_id,confirmado_en)
        values (?,'DOCUMENTO_INSPECCION','documento.pdf',?,'application/pdf',100,repeat('a',64),?,CURRENT_TIMESTAMP)
        """,
        id,
        id + ".pdf",
        autor);
    jdbc.update(
        "insert into archivo_inspeccion(archivo_id,tipo_uso,inspeccion_id) values (?,'DOCUMENTO_INSPECCION',?)",
        id,
        inspeccion);
  }

  @Dado("la inspección fue eliminada")
  public void eliminada() {
    jdbc.update("update inspeccion set eliminado_en=CURRENT_TIMESTAMP where id=?", inspeccion);
  }

  @Dado("la inspección pasó al estado {string}")
  public void estado(String estado) {
    jdbc.update(
        "update inspeccion set estado=?,publicado_en=CURRENT_TIMESTAMP where id=?",
        estado,
        inspeccion);
  }

  @Dado("la fotografía {string} fue retirada hace una hora")
  public void retiradaPrevia(String categoria) {
    jdbc.update(
        "update archivo set estado='RETIRADO',retirado_en=CURRENT_TIMESTAMP - interval '1 hour' where id=?",
        fotografias.get(categoria));
    retiradaOriginal = fechaRetirada(fotografias.get(categoria));
  }

  @Dado("la fotografía {string} está purgada")
  public void purgada(String categoria) {
    jdbc.update(
        "update archivo set estado='PURGADO',purgado_en=CURRENT_TIMESTAMP where id=?",
        fotografias.get(categoria));
  }

  @Cuando("consulto el diagnóstico fotográfico")
  public void consultar() throws Exception {
    ejecutar(get(base() + "/evidencia-fotografica"));
  }

  @Cuando("retiro la fotografía {string}")
  public void retirar(String categoria) throws Exception {
    ejecutar(delete(base() + "/fotografias/" + fotografias.get(categoria)));
  }

  @Cuando("el llamador transaccional de prueba exige la evidencia")
  public void exigirEnTransaccion() throws Exception {
    ejecutar(post("/prueba/inspecciones/" + inspeccion + "/validar-transicion"));
  }

  @Cuando("exijo evidencia sin transacción")
  public void exigirSinTransaccion() {
    fallo = catchThrowable(() -> evidencia.exigirParaTransicion(inspeccion));
  }

  @Entonces("se rechaza por ausencia de transacción")
  public void transaccionObligatoria() {
    assertThat(fallo).isInstanceOf(IllegalTransactionStateException.class);
  }

  @Cuando("el llamador retira {string} y falla antes del commit")
  public void rollback(String categoria) {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transacciones)
                    .executeWithoutResult(
                        tx -> {
                          retirada.retirar(inspeccion, fotografias.get(categoria), autor);
                          throw new IllegalStateException("fallo del llamador de prueba");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("fallo del llamador de prueba");
  }

  @Dado("registro una fotografía real mediante HU-028 en {string}")
  public void cargaReal(String categoria) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
    imagenOriginal = bytes.toByteArray();
    claveOriginal = UUID.randomUUID().toString();
    cargaOriginal =
        new AdjuntarFotografiaRequest(
            UuidV7Generator.generate(),
            categoria,
            14.0,
            -90.0,
            Instant.now().minusSeconds(5),
            null,
            null);
    enviarCarga();
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(201);
    fotografias.put(categoria, cargaOriginal.id());
    objetoOriginal =
        jdbc.queryForObject(
            "select clave_objeto_original from archivo where id=?",
            String.class,
            cargaOriginal.id());
    objetoMiniatura =
        jdbc.queryForObject(
            "select clave_objeto_miniatura from archivo where id=?",
            String.class,
            cargaOriginal.id());
  }

  @Cuando("repito la carga original con la misma clave")
  public void repetirCarga() throws Exception {
    enviarCarga();
  }

  @Entonces("el original y la miniatura permanecen almacenados")
  public void bytesConservados() throws Exception {
    assertThat(almacenamiento.existe(objetoOriginal)).isTrue();
    assertThat(almacenamiento.existe(objetoMiniatura)).isTrue();
  }

  @Entonces("el original y la miniatura retirados responden 404")
  public void lecturaRetirada() throws Exception {
    for (String sufijo : List.of("contenido", "miniatura")) {
      ejecutar(get(base() + "/fotografias/" + cargaOriginal.id() + "/" + sufijo));
      status(404);
    }
  }

  @Entonces("el listado excluye la fotografía {string}")
  public void listadoSinRetirada(String categoria) throws Exception {
    ejecutar(get(base() + "/fotografias"));
    status(200);
    List<String> ids = new ArrayList<>();
    cuerpo.forEach(foto -> ids.add(foto.path("id").asText()));
    assertThat(ids).doesNotContain(fotografias.get(categoria).toString());
  }

  @Entonces("la respuesta HTTP es {int}")
  public void status(int esperado) {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(esperado);
  }

  @Entonces("la evidencia es {word} con {int} fotografías disponibles")
  public void resultado(String validez, int total) {
    assertThat(cuerpo.path("valida").asBoolean()).isEqualTo(validez.equals("válida"));
    assertThat(cuerpo.path("totalDisponibles").asLong()).isEqualTo(total);
    assertThat(cuerpo.path("maximoPermitido").asInt()).isEqualTo(60);
  }

  @Entonces("faltan las categorías {string}")
  public void faltantes(String categorias) {
    List<String> nombres = new ArrayList<>();
    cuerpo.path("faltantes").forEach(f -> nombres.add(f.path("categoria").asText()));
    assertThat(nombres).containsExactlyInAnyOrder(categorias.split(","));
  }

  @Entonces("no faltan categorías")
  public void sinFaltantes() {
    assertThat(cuerpo.path("faltantes").size()).isZero();
  }

  @Entonces("el código de error es {string}")
  public void codigo(String codigo) {
    assertThat(cuerpo.path("code").asText()).isEqualTo(codigo);
  }

  @Entonces("el error informa {int} fotografías y un máximo de {int}")
  public void errorInformativo(int total, int maximo) {
    assertThat(respuesta.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(cuerpo.path("totalDisponibles").asInt()).isEqualTo(total);
    assertThat(cuerpo.path("maximoPermitido").asInt()).isEqualTo(maximo);
  }

  @Entonces("la actividad de la inspección no cambió")
  public void actividadConservada() {
    assertThat(actividad()).isEqualTo(actividadOriginal);
  }

  @Entonces("la respuesta es privada y no contiene claves físicas")
  public void privacidad() {
    assertThat(respuesta.getResponse().getHeader("Cache-Control")).contains("no-store", "private");
    assertThat(cuerpo.toString()).doesNotContain("claveObjeto", "fixture/", "bucket");
  }

  @Entonces("la fotografía {string} está {string} y conserva su asociación")
  public void estadoArchivo(String categoria, String estado) {
    UUID id = fotografias.get(categoria);
    assertThat(jdbc.queryForObject("select estado from archivo where id=?", String.class, id))
        .isEqualTo(estado);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from archivo_inspeccion where archivo_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Entonces("la fecha original de retirada se conserva")
  public void fechaConservada() {
    assertThat(fechaRetirada(fotografias.get("CAUCE"))).isEqualTo(retiradaOriginal);
  }

  @Entonces("la fotografía {string} no tiene fecha de retirada")
  public void sinFecha(String categoria) {
    assertThat(fechaRetirada(fotografias.get(categoria))).isNull();
  }

  private void enviarCarga() throws Exception {
    ejecutar(
        multipart(base() + "/fotografias")
            .file(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", imagenOriginal))
            .file(
                new MockMultipartFile(
                    "metadatos", "", "application/json", json.writeValueAsBytes(cargaOriginal)))
            .header("Idempotency-Key", claveOriginal));
  }

  private void ejecutar(MockHttpServletRequestBuilder request) throws Exception {
    if (actor != null)
      request.header(
          HttpHeaders.AUTHORIZATION,
          "Bearer " + jwt.issueAccessToken(usuarios.findWithRoleById(actor).orElseThrow()));
    respuesta = mvc.perform(request).andReturn();
    var contenido = respuesta.getResponse().getContentAsString();
    cuerpo = contenido.isBlank() ? json.createObjectNode() : json.readTree(contenido);
  }

  private String base() {
    return "/api/v1/inspecciones/" + inspeccion;
  }

  private Instant actividad() {
    return jdbc.queryForObject(
            "select ultima_actividad_en from inspeccion where id=?", Timestamp.class, inspeccion)
        .toInstant();
  }

  private Instant fechaRetirada(UUID id) {
    var fecha =
        jdbc.queryForObject("select retirado_en from archivo where id=?", Timestamp.class, id);
    return fecha == null ? null : fecha.toInstant();
  }

  private void crearUsuario(UUID id, String rol) {
    jdbc.update(
        """
        insert into usuario(id,email,password_hash,rol_id,verificado,activado,activo)
        select ?,?,'prueba',id,true,true,true from rol where nombre=?
        on conflict(id) do update set rol_id=EXCLUDED.rol_id
        """,
        id,
        id + "@bdd.test",
        rol);
  }

  private UUID crearInspeccion(String estado) {
    UUID id = UUID.randomUUID(), puente = UUID.randomUUID(), version = UUID.randomUUID();
    jdbc.update(
        """
        insert into version_formulario(id,codigo,esquema,creado_por_id,estado,activa,
          hash_contenido,version_algoritmo_ic,publicado_por_id,publicado_en)
        values (?,?,'{}',?,'PUBLICADA',false,repeat('a',64),'bdd',?,CURRENT_TIMESTAMP)
        """,
        version,
        "bdd-" + version,
        autor,
        autor);
    jdbc.update(
        """
        insert into puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id)
        select ?,?,COALESCE((select max(correlativo_municipal)+1 from puente),1),id,'bdd','bdd',
          ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? from municipio where codigo_ine='0114'
        """,
        puente,
        "B-" + puente,
        autor);
    jdbc.update(
        """
        insert into inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,
          nombre_autor_capturado,fecha_inspeccion,dispositivo_id,datos,estado,publicado_en)
        values (?,?,?,?,'CATEDRATICO','autor',CURRENT_DATE,'bdd','{}',?,
          case when ?='PUBLICADA' then CURRENT_TIMESTAMP end)
        """,
        id,
        puente,
        version,
        autor,
        estado,
        estado);
    return id;
  }

  private UUID crearFotografia(UUID destino, String categoria, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into archivo(id,tipo_uso,categoria_foto,nombre_almacenado,clave_objeto_original,
          clave_objeto_miniatura,mime_real,tamano_bytes,checksum_sha256,ancho_px,alto_px,
          ubicacion_captura,capturado_en,subido_por_id,confirmado_en,estado)
        values (?,'FOTO_INSPECCION',?,'fixture.jpg',?,?,'image/jpeg',100,repeat('a',64),
          64,32,ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,?)
        """,
        id,
        categoria,
        "fixture/" + id + ".jpg",
        "fixture/" + id + "-mini.jpg",
        autor,
        estado);
    jdbc.update(
        "insert into archivo_inspeccion(archivo_id,tipo_uso,inspeccion_id) values (?,'FOTO_INSPECCION',?)",
        id,
        destino);
    return id;
  }

  private void asignarCatedratico(UUID catedratico) {
    UUID estudiante = UUID.randomUUID(), asignatura = UUID.randomUUID(), curso = UUID.randomUUID();
    UUID inscripcion = UUID.randomUUID(), asignacion = UUID.randomUUID();
    crearUsuario(estudiante, "ESTUDIANTE");
    jdbc.update(
        "insert into asignatura(id,codigo,nombre) values (?,?,'bdd')",
        asignatura,
        asignatura.toString());
    jdbc.update(
        "insert into curso(id,asignatura_id,periodo,catedratico_id,fecha_inicio,fecha_fin) values (?,?,'2026',?,'2026-01-01','2026-12-31')",
        curso,
        asignatura,
        catedratico);
    jdbc.update(
        "insert into curso_estudiante(id,curso_id,estudiante_id,inscrito_por_id,seccion) values (?,?,?,?,'A')",
        inscripcion,
        curso,
        estudiante,
        catedratico);
    UUID puente =
        jdbc.queryForObject("select puente_id from inspeccion where id=?", UUID.class, inspeccion);
    jdbc.update(
        "insert into asignacion_puente(id,curso_estudiante_id,puente_id,asignado_por_id) values (?,?,?,?)",
        asignacion,
        inscripcion,
        puente,
        catedratico);
    jdbc.update("update inspeccion set asignacion_puente_id=? where id=?", asignacion, inspeccion);
  }
}
