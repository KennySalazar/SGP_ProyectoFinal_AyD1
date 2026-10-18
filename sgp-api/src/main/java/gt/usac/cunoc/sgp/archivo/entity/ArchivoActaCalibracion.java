package gt.usac.cunoc.sgp.archivo.entity;

import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "archivo_acta_calibracion")
public class ArchivoActaCalibracion implements Persistable<UUID> {
    @Transient private boolean nuevo = true;

    @Id
    @Column(name = "archivo_id")
    private UUID archivoId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archivo_id", insertable = false, updatable = false)
    private Archivo archivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_uso", nullable = false, length = 30)
    private TipoUsoArchivo tipoUso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_formulario_id")
    private VersionFormulario versionFormulario;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected ArchivoActaCalibracion() {}

    public ArchivoActaCalibracion(Archivo archivo, VersionFormulario version, Instant creadoEn) {
        this.archivo = archivo;
        this.archivoId = archivo.getId();
        this.tipoUso = TipoUsoArchivo.ACTA_CALIBRACION;
        this.versionFormulario = version;
        this.creadoEn = creadoEn;
    }

    @PostLoad
    @PostPersist
    void persistido() {
        nuevo = false;
    }

    public boolean isNew() {
        return nuevo;
    }

    public UUID getId() {
        return archivoId;
    }

    public Archivo getArchivo() {
        return archivo;
    }

    public VersionFormulario getVersionFormulario() {
        return versionFormulario;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}