package gt.usac.cunoc.sgp.usuario.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfesionalResponse(
    UUID usuarioId,
    String email,
    String numeroColegiado,
    boolean colegiadoVerificado,
    OffsetDateTime colegiadoVerificadoEn,
    UUID colegiadoVerificadoPorId,
    boolean cuentaActivada,
    OffsetDateTime creadoEn) {}
