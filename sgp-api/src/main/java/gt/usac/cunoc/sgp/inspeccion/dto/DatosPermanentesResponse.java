package gt.usac.cunoc.sgp.inspeccion.dto;

import java.math.BigDecimal;

public record DatosPermanentesResponse(
    BigDecimal longitudM,
    Integer numeroTramos,
    BigDecimal anchoRodaduraM,
    BigDecimal anchoAceraDerechaM,
    BigDecimal anchoAceraIzquierdaM,
    BigDecimal alturaLibreSuperiorM,
    BigDecimal alturaLibreSobreCauceM,
    String tipologiaPuente,
    String tipoCruce,
    Integer numeroViasPorSentido,
    String materialSuperestructura,
    String materialSubestructura,
    String cargaDiseno,
    Integer anioConstruccion,
    String rutaPavimentada,
    String alineamientoHorizontal,
    Boolean esviaje,
    String tipoCuerpoAgua,
    String nombreRio,
    String encauzamiento,
    Boolean alumbradoExiste,
    Boolean drenajesAledanosExisten) {}
