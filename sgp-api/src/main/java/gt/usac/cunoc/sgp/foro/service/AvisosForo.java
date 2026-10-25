package gt.usac.cunoc.sgp.foro.service;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.foro.entity.ForoComentario;
import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.repository.ForoAutorRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoComentarioRepository;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Avisos que genera el foro (HU-066) mediante el servicio transversal de avisos (HU-040): las
 * menciones con {@code @usuario} (RN-FOR-10) y las alertas sobre un puente en estado Malo
 * (RN-FOR-07).
 *
 * <p>Los avisos se registran en la misma transacción que el comentario o el hilo y nunca incluyen
 * su texto ni lo que escribió un usuario: solo datos del sistema, como el código del puente
 * (DT-SEC-12).
 */
@Component
public class AvisosForo {

  static final String TIPO_MENCION = "FORO_MENCION";
  static final String TIPO_ALERTA_PUENTE_MALO = "FORO_ALERTA_PUENTE_MALO";

  /**
   * Un {@code @usuario} con la forma de un nombre de usuario (ReglasPerfil): de 3 a 30 caracteres,
   * que empieza y termina en letra o dígito. No cuenta si va pegado a otra palabra, como en un
   * correo.
   */
  private static final Pattern MENCION =
      Pattern.compile("(?<![A-Za-z0-9._@-])@([A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9])");

  private final ForoComentarioRepository comentarios;
  private final ForoAutorRepository autores;
  private final PuenteRepository puentes;
  private final UserAccountRepository usuarios;
  private final NotificacionService notificaciones;
  private final Clock clock;

  AvisosForo(
      ForoComentarioRepository comentarios,
      ForoAutorRepository autores,
      PuenteRepository puentes,
      UserAccountRepository usuarios,
      NotificacionService notificaciones,
      Clock clock) {
    this.comentarios = comentarios;
    this.autores = autores;
    this.puentes = puentes;
    this.usuarios = usuarios;
    this.notificaciones = notificaciones;
    this.clock = clock;
  }

  /** Los nombres mencionados en un texto, en minúsculas y sin repetir. */
  static Set<String> mencionados(String texto) {
    Set<String> nombres = new LinkedHashSet<>();
    if (texto == null) return nombres;
    Matcher matcher = MENCION.matcher(texto);
    while (matcher.find()) nombres.add(matcher.group(1).toLowerCase(Locale.ROOT));
    return nombres;
  }

  /**
   * Registra las menciones de un comentario y avisa a cada mencionado nuevo, una sola vez por
   * comentario: al editarlo solo se avisa a quien no estaba mencionado antes. Un nombre que no
   * existe o de una cuenta desactivada se ignora sin error, y nadie se avisa a sí mismo.
   */
  void avisarMenciones(ForoHilo hilo, ForoComentario comentario) {
    Set<String> nombres = mencionados(comentario.getContenido());
    if (nombres.isEmpty()) return;
    Instant ahora = clock.instant();
    String puente = null;
    for (UUID mencionado : autores.buscarMencionables(nombres)) {
      if (mencionado.equals(comentario.getAutorId())) continue;
      if (comentarios.registrarMencion(
              UuidV7Generator.generate(), comentario.getId(), mencionado, ahora)
          == 0) continue;
      if (puente == null) puente = codigoDe(hilo.getPuenteId());
      notificaciones.notificar(
          mencionado,
          TIPO_MENCION,
          "Te mencionaron en el foro",
          "Te mencionaron en un comentario del foro del puente " + puente + ".",
          "foro_comentario",
          comentario.getId(),
          "foro-mencion:" + comentario.getId());
    }
  }

  /**
   * Un hilo de categoría Alerta sobre un puente en estado Malo avisa a los Catedráticos y
   * Administradores activos, salvo a quien lo abrió (RN-FOR-07).
   */
  void avisarAlerta(ForoHilo hilo, Puente puente) {
    if (hilo.getCategoria() != CategoriaHilo.ALERTA || !"Malo".equals(puente.getEstadoActual())) {
      return;
    }
    Set<UUID> destinatarios = new LinkedHashSet<>();
    for (RoleName rol : List.of(RoleName.CATEDRATICO, RoleName.ADMINISTRADOR)) {
      usuarios.findByRole_NameAndActiveTrueOrderByEmailAsc(rol).stream()
          .map(UserAccount::getId)
          .forEach(destinatarios::add);
    }
    destinatarios.remove(hilo.getAutorId());
    for (UUID destinatario : destinatarios) {
      notificaciones.notificar(
          destinatario,
          TIPO_ALERTA_PUENTE_MALO,
          "Alerta sobre un puente en mal estado",
          "Se abrió una alerta en el foro del puente "
              + puente.getCodigo()
              + ", que está en estado Malo.",
          "foro_hilo",
          hilo.getId(),
          "foro-alerta-puente-malo:" + hilo.getId());
    }
  }

  private String codigoDe(UUID puenteId) {
    return puentes.findById(puenteId).map(Puente::getCodigo).orElse("");
  }
}
