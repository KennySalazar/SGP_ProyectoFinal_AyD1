package gt.usac.cunoc.sgp.usuario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "rol")
public class Role {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "nombre", nullable = false, unique = true, length = 40)
  private RoleName name;

  @Column(name = "descripcion", nullable = false, length = 255)
  private String description;

  protected Role() {}

  public UUID getId() {
    return id;
  }

  public RoleName getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }
}
