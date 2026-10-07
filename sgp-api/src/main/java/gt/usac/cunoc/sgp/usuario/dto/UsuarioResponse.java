package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cuenta vista por el Administrador. Los datos de colegiado solo aplican al Profesional Externo.
 */
public record UsuarioResponse(
    UUID id,
    String email,
    RoleName rol,
    EstadoUsuario estado,
    boolean verificado,
    boolean activado,
    boolean activo,
    String numeroColegiado,
    Boolean colegiadoVerificado,
    OffsetDateTime desactivadoEn,
    String motivoDesactivacion,
    OffsetDateTime creadoEn) {}
