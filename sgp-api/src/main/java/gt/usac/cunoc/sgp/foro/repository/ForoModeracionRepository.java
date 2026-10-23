package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Lo que el Catedrático y el Administrador revisan a posteriori en el foro (RN-REV-02). */
public interface ForoModeracionRepository extends Repository<ForoHilo, UUID> {

  /** Inspección publicada sin revisión previa, con su hilo si ya se abrió. */
  interface InspeccionSinRevision {
    UUID getInspeccionId();

    UUID getPuenteId();

    String getPuenteCodigo();

    String getPuenteNombre();

    LocalDate getFechaInspeccion();

    Instant getPublicadoEn();

    String getAutorUsuario();

    String getAutorRol();

    UUID getHiloId();

    long getTotalComentarios();
  }

  /**
   * Inspecciones que su autor (Catedrático o Profesional externo) publicó sin revisión previa, de
   * las más recientes a las más antiguas. Se reconocen por el evento PUBLICAR_SIN_REVISION de su
   * historial; las de un puente inactivo o eliminadas no se listan.
   */
  @Query(
      value =
          """
          SELECT i.id AS inspeccionId, p.id AS puenteId, p.codigo AS puenteCodigo,
            p.nombre AS puenteNombre, i.fechaInspeccion AS fechaInspeccion,
            i.publicadoEn AS publicadoEn, u.nombreUsuario AS autorUsuario,
            i.rolAutorCreacion AS autorRol, h.id AS hiloId,
            (SELECT COUNT(c) FROM ForoComentario c WHERE c.hiloId = h.id) AS totalComentarios
          FROM Inspeccion i
            JOIN i.puente p
            JOIN UserAccount u ON u.id = i.autorId
            LEFT JOIN ForoHilo h ON h.inspeccionId = i.id
          WHERE i.estado = 'PUBLICADA' AND i.eliminadoEn IS NULL AND p.activo = true
            AND EXISTS (SELECT 1 FROM HistorialEstadoInspeccion e
                        WHERE e.inspeccionId = i.id AND e.evento = 'PUBLICAR_SIN_REVISION')
          ORDER BY i.publicadoEn DESC NULLS LAST, i.id DESC
          """,
      countQuery =
          """
          SELECT COUNT(i) FROM Inspeccion i JOIN i.puente p
          WHERE i.estado = 'PUBLICADA' AND i.eliminadoEn IS NULL AND p.activo = true
            AND EXISTS (SELECT 1 FROM HistorialEstadoInspeccion e
                        WHERE e.inspeccionId = i.id AND e.evento = 'PUBLICAR_SIN_REVISION')
          """)
  Page<InspeccionSinRevision> listarInspeccionesSinRevision(Pageable pageable);
}
