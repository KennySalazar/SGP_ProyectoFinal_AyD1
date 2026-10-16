package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.ContextoArchivosInspeccion;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccesoFotografiasInspeccionService {

  private final AsignacionPuenteRepository asignaciones;
  private final PoliticaArchivosInspeccion politica;

  public AccesoFotografiasInspeccionService(
      AsignacionPuenteRepository asignaciones, PoliticaArchivosInspeccion politica) {
    this.asignaciones = asignaciones;
    this.politica = politica;
  }

  public void validarLectura(Inspeccion inspeccion, UUID actorId) {
    var contexto =
        inspeccion == null
            ? null
            : new ContextoArchivosInspeccion(
                inspeccion.getId(),
                inspeccion.getAutorId(),
                inspeccion.getEstado(),
                inspeccion.getEliminadoEn());
    boolean asignado =
        contexto != null
            && actorId != null
            && contexto.eliminadoEn() == null
            && !actorId.equals(contexto.autorId())
            && "BORRADOR".equals(contexto.estado())
            && inspeccion.getAsignacionPuenteId() != null
            && asignaciones
                .findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
                    inspeccion.getAsignacionPuenteId(), actorId)
                .isPresent();
    politica.validarLectura(contexto, actorId, asignado);
  }
}
