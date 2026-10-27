package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.dto.IndiceCondicionPreliminarResponse;
import gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.PreparadorEntradaIcService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevisionIndicePreliminarService {
  private final PreparadorEntradaIcService preparador;
  private final CalculoIndiceCondicionService calculador;

  public RevisionIndicePreliminarService(
      PreparadorEntradaIcService preparador, CalculoIndiceCondicionService calculador) {
    this.preparador = preparador;
    this.calculador = calculador;
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public IndiceCondicionPreliminarResponse calcular(UUID versionId, JsonNode datos) {
    var resultado = calculador.calcular(preparador.preparar(versionId, datos));
    return new IndiceCondicionPreliminarResponse(
        resultado.indiceCalculado(), resultado.estadoCalculado().name(), "PRELIMINAR");
  }
}
