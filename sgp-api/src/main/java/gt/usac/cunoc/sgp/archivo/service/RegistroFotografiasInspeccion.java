package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.model.FotografiaRegistrada;
import java.time.Instant;
import java.util.*;

/** Contrato interno: el llamador autoriza y bloquea la inspección antes de retirar. */
public interface RegistroFotografiasInspeccion {
  List<FotografiaRegistrada> consultar(UUID inspeccionId);

  Optional<FotografiaRegistrada> buscar(UUID inspeccionId, UUID fotografiaId);

  void retirar(UUID inspeccionId, UUID fotografiaId, Instant retiradoEn);
}
