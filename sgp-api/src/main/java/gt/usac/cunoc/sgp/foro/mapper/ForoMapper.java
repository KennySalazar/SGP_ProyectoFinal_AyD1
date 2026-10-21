package gt.usac.cunoc.sgp.foro.mapper;

import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.entity.ForoComentario;
import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import gt.usac.cunoc.sgp.foro.repository.ForoAutorRepository.Autor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ForoMapper {

  @Mapping(target = "id", source = "hilo.id")
  @Mapping(target = "autorId", source = "hilo.autorId")
  @Mapping(target = "autorUsuario", source = "autor.usuario")
  @Mapping(target = "autorRol", source = "autor.rol")
  @Mapping(target = "cerrado", expression = "java(hilo.getCerradoEn() != null)")
  @Mapping(target = "totalComentarios", source = "totalComentarios")
  HiloResponse toHiloResponse(ForoHilo hilo, Autor autor, long totalComentarios);

  @Mapping(target = "id", source = "comentario.id")
  @Mapping(target = "autorId", source = "comentario.autorId")
  @Mapping(target = "autorUsuario", source = "autor.usuario")
  @Mapping(target = "autorRol", source = "autor.rol")
  ComentarioResponse toComentarioResponse(ForoComentario comentario, Autor autor);

  default OffsetDateTime aHoraGuatemala(Instant instante) {
    return instante == null
        ? null
        : instante.atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();
  }
}
