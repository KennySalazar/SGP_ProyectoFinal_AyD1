package gt.usac.cunoc.sgp.curso.dto;

import java.util.UUID;

public record CatedraticoResponse(UUID id, String email, String nombre) {
  public CatedraticoResponse(UUID id, String email) {
    this(id, email, "Docente sin nombre registrado");
  }
}
