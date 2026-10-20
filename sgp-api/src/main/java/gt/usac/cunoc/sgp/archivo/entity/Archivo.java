package gt.usac.cunoc.sgp.archivo.entity;

import gt.usac.cunoc.sgp.archivo.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "archivo")
public class Archivo implements org.springframework.data.domain.Persistable<UUID> {
  @Transient private boolean nuevo = true;

  @PostLoad
  @PostPersist
  void persistido() {
    nuevo = false;
  }

  public boolean isNew() {
    return nuevo;
  }

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_uso", nullable = false, length = 30)
  private TipoUsoArchivo tipoUso;

  @Enumerated(EnumType.STRING)
  @Column(name = "categoria_foto", length = 30)
  private CategoriaFoto categoriaFoto;

  @Column(name = "nombre_original", length = 255)
  private String nombreOriginal;

  @Column(name = "nombre_almacenado", nullable = false, length = 255)
  private String nombreAlmacenado;

  @Column(name = "clave_objeto_original", nullable = false, length = 500)
  private String claveObjetoOriginal;

  @Column(name = "clave_objeto_miniatura", length = 500)
  private String claveObjetoMiniatura;

  @Column(name = "mime_real", nullable = false, length = 100)
  private String mimeReal;

  @Column(name = "tamano_bytes", nullable = false)
  private long tamanoBytes;

  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "ancho_px")
  private Integer anchoPx;

  @Column(name = "alto_px")
  private Integer altoPx;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  @Column(name = "ubicacion_captura", columnDefinition = "geography(Point,4326)")
  private Point ubicacionCaptura;

  @Column(name = "capturado_en")
  private Instant capturadoEn;

  @Column(name = "subido_por_id", nullable = false)
  private UUID subidoPorId;

  @Column(name = "confirmado_en", nullable = false)
  private Instant confirmadoEn;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoArchivo estado;

  @Column(name = "retirado_en")
  private Instant retiradoEn;

  @Column(name = "purgado_en")
  private Instant purgadoEn;

  @Column(name = "motivo_retiro")
  private String motivoRetiro;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected Archivo() {}

  public Archivo(
      UUID id,
      TipoUsoArchivo tipoUso,
      CategoriaFoto categoriaFoto,
      String nombreOriginal,
      String nombreAlmacenado,
      String claveObjetoOriginal,
      String claveObjetoMiniatura,
      String mimeReal,
      long tamanoBytes,
      String checksumSha256,
      Integer anchoPx,
      Integer altoPx,
      Point ubicacionCaptura,
      Instant capturadoEn,
      UUID subidoPorId,
      Instant confirmadoEn) {
    this.id = id;
    this.tipoUso = tipoUso;
    this.categoriaFoto = categoriaFoto;
    this.nombreOriginal = nombreOriginal;
    this.nombreAlmacenado = nombreAlmacenado;
    this.claveObjetoOriginal = claveObjetoOriginal;
    this.claveObjetoMiniatura = claveObjetoMiniatura;
    this.mimeReal = mimeReal;
    this.tamanoBytes = tamanoBytes;
    this.checksumSha256 = checksumSha256;
    this.anchoPx = anchoPx;
    this.altoPx = altoPx;
    this.ubicacionCaptura = ubicacionCaptura;
    this.capturadoEn = capturadoEn;
    this.subidoPorId = subidoPorId;
    this.confirmadoEn = confirmadoEn;
    this.estado = EstadoArchivo.DISPONIBLE;
    this.creadoEn = confirmadoEn;
  }

  public void retirar(Instant ahora) {
    if (estado == EstadoArchivo.DISPONIBLE) {
      estado = EstadoArchivo.RETIRADO;
      retiradoEn = ahora;
    }
  }

  /** Retira el archivo documentando por qué deja de contar como evidencia (HU-048). */
  public void retirar(Instant ahora, String motivo) {
    if (estado == EstadoArchivo.DISPONIBLE) {
      retirar(ahora);
      motivoRetiro = motivo;
    }
  }

  public UUID getId() {
    return id;
  }

  public TipoUsoArchivo getTipoUso() {
    return tipoUso;
  }

  public CategoriaFoto getCategoriaFoto() {
    return categoriaFoto;
  }

  public String getNombreOriginal() {
    return nombreOriginal;
  }

  public String getNombreAlmacenado() {
    return nombreAlmacenado;
  }

  public String getClaveObjetoOriginal() {
    return claveObjetoOriginal;
  }

  public String getClaveObjetoMiniatura() {
    return claveObjetoMiniatura;
  }

  public String getMimeReal() {
    return mimeReal;
  }

  public long getTamanoBytes() {
    return tamanoBytes;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public Integer getAnchoPx() {
    return anchoPx;
  }

  public Integer getAltoPx() {
    return altoPx;
  }

  public Point getUbicacionCaptura() {
    return ubicacionCaptura;
  }

  public Instant getCapturadoEn() {
    return capturadoEn;
  }

  public UUID getSubidoPorId() {
    return subidoPorId;
  }

  public Instant getConfirmadoEn() {
    return confirmadoEn;
  }

  public EstadoArchivo getEstado() {
    return estado;
  }

  public Instant getRetiradoEn() {
    return retiradoEn;
  }

  public Instant getPurgadoEn() {
    return purgadoEn;
  }

  public String getMotivoRetiro() {
    return motivoRetiro;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
