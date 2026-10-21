package gt.usac.cunoc.sgp.foro.dto;

import java.util.List;

/** El hilo de una inspección publicada y el resumen de sus comentarios anclados. */
public record HiloInspeccionResponse(HiloResponse hilo, List<AnclaResponse> anclas) {}
