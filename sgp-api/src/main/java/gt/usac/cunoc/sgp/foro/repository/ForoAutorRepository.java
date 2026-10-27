package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Datos públicos de quien escribe en el foro: solo su nombre de usuario y su rol. Nunca se expone
 * el correo ni el nombre completo, porque el foro puede leerlo un visitante sin sesión.
 */
public interface ForoAutorRepository extends Repository<UserAccount, UUID> {

  /** Nombre de usuario (si la cuenta ya lo tiene) y rol de un autor. */
  interface Autor {
    UUID getId();

    String getUsuario();

    String getRol();
  }

  @Query(
      value =
          """
          SELECT u.id AS id, u.nombre_usuario AS usuario, r.nombre AS rol
          FROM usuario u
          JOIN rol r ON r.id = u.rol_id
          WHERE u.id IN (:ids)
          """,
      nativeQuery = true)
  List<Autor> buscarPorIds(@Param("ids") Collection<UUID> ids);

  /**
   * Los usuarios que pueden recibir una mención (RN-FOR-10): su nombre de usuario coincide sin
   * distinguir mayúsculas y su cuenta está activa. {@code nombres} va en minúsculas.
   */
  @Query(
      value =
          """
          SELECT u.id FROM usuario u
          WHERE lower(u.nombre_usuario) IN (:nombres)
            AND u.activo AND u.activado AND u.verificado
          """,
      nativeQuery = true)
  List<UUID> buscarMencionables(@Param("nombres") Collection<String> nombres);
}
