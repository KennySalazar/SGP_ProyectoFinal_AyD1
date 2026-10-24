package gt.usac.cunoc.sgp.notificacion.repository;

import gt.usac.cunoc.sgp.notificacion.entity.Notificacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

  @Modifying
  @Query(
      value =
          """
              INSERT INTO notificacion
              (id, usuario_id, tipo, titulo, mensaje, entidad, entidad_id, clave_evento, creado_en)
              VALUES (:#{#aviso.id}, :#{#aviso.usuarioId}, :#{#aviso.tipo}, :#{#aviso.titulo},
                      :#{#aviso.mensaje}, :#{#aviso.entidad}, :#{#aviso.entidadId},
                      :#{#aviso.claveEvento}, :#{#aviso.creadoEn})
              ON CONFLICT (usuario_id, clave_evento) DO NOTHING
              """,
      nativeQuery = true)
  int insertarSiNoExiste(@Param("aviso") Notificacion aviso);

  boolean existsByUsuarioIdAndClaveEvento(UUID usuarioId, String claveEvento);

  Page<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId, Pageable pageable);

  Page<Notificacion> findByUsuarioIdAndLeidoEnIsNullOrderByCreadoEnDesc(
      UUID usuarioId, Pageable pageable);

  Optional<Notificacion> findByIdAndUsuarioId(UUID id, UUID usuarioId);

  long countByUsuarioIdAndLeidoEnIsNull(UUID usuarioId);

  @Modifying
  @Query(
      "UPDATE Notificacion n SET n.leidoEn = :ahora WHERE n.usuarioId = :usuarioId AND n.leidoEn IS NULL")
  int marcarTodasLeidas(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);
}
