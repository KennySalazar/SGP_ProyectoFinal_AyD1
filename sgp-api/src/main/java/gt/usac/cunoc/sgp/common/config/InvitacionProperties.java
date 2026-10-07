package gt.usac.cunoc.sgp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invitacion")
public class InvitacionProperties {

  private int vigenciaHoras = 72;
  private String urlActivacion = "http://localhost:4200/activar-cuenta";

  public int getVigenciaHoras() {
    return vigenciaHoras;
  }

  public void setVigenciaHoras(int vigenciaHoras) {
    this.vigenciaHoras = vigenciaHoras;
  }

  public String getUrlActivacion() {
    return urlActivacion;
  }

  public void setUrlActivacion(String urlActivacion) {
    this.urlActivacion = urlActivacion;
  }
}
