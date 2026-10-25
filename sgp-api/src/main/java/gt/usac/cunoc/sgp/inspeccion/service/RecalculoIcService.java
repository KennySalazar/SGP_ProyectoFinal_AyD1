package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.dto.ComparacionIcResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ResultadoIcHistorialResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecalculoIcService {
  private final InspeccionRepository inspecciones;
  private final ResultadoIcRepository resultados;
  private final VersionFormularioRepository versiones;
  private final UserAccountRepository usuarios;
  private final PreparadorEntradaIcService preparador;
  private final CalculoIndiceCondicionService calculador;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public RecalculoIcService(
      InspeccionRepository inspecciones,
      ResultadoIcRepository resultados,
      VersionFormularioRepository versiones,
      UserAccountRepository usuarios,
      PreparadorEntradaIcService preparador,
      CalculoIndiceCondicionService calculador,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.resultados = resultados;
    this.versiones = versiones;
    this.usuarios = usuarios;
    this.preparador = preparador;
    this.calculador = calculador;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  public ResultadoIcHistorialResponse recalcular(UUID inspeccionId, UUID usuarioId) {
    if (usuarioId == null)
      throw error(
          HttpStatus.UNAUTHORIZED, "authentication_required", "Se requiere una sesion valida.");
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNullForUpdate(inspeccionId)
            .orElseThrow(() -> noEncontrada());
    validarPublicada(inspeccion);
    resultados.findPublicacionByInspeccionId(inspeccionId).orElseThrow(() -> sinOriginal());
    VersionFormulario vigente =
        versiones
            .findByActivaTrue()
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "configuracion_ic_no_vigente",
                        "No hay una version activa de configuracion del IC."));
    if (vigente.getEstado() != EstadoVersionFormulario.PUBLICADA)
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "configuracion_ic_no_publicada",
          "La configuracion activa debe estar publicada.");
    var entrada = preparador.preparar(vigente.getId(), inspeccion.getDatos());
    var calculo = calculador.calcular(entrada);
    var existente =
        resultados.findRecalculo(
            inspeccionId, vigente.getId(), calculo.hashEntrada(), calculo.versionAlgoritmo());
    if (existente.isPresent()) return respuesta(existente.get());
    ResultadoIc resultado =
        ResultadoIc.recalculo(
            UuidV7Generator.generate(),
            inspeccionId,
            vigente.getId(),
            calculo.indiceCalculado(),
            calculo.estadoPorRango(),
            calculo.estadoCalculado(),
            json.valueToTree(calculo.condicionesAnulacion()),
            json.valueToTree(calculo),
            calculo.hashEntrada(),
            calculo.versionAlgoritmo(),
            usuarioId,
            clock.instant());
    resultados.saveAndFlush(resultado);
    var datosAuditoria = json.createObjectNode();
    datosAuditoria.put("resultadoIcId", resultado.getId().toString());
    datosAuditoria.put("inspeccionId", inspeccionId.toString());
    datosAuditoria.put("versionConfiguracionId", vigente.getId().toString());
    datosAuditoria.put("tipo", resultado.getTipo());
    datosAuditoria.put("indiceCalculado", resultado.getIndiceCalculado());
    datosAuditoria.put("hashEntrada", resultado.getHashEntrada());
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CREAR,
        "resultado_ic",
        resultado.getId(),
        null,
        datosAuditoria,
        null);
    return respuesta(resultado);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public ComparacionIcResponse historial(UUID inspeccionId) {
    Inspeccion inspeccion =
        inspecciones.findByIdAndEliminadoEnIsNull(inspeccionId).orElseThrow(() -> noEncontrada());
    validarPublicada(inspeccion);
    List<ResultadoIc> lista =
        resultados.findAllByInspeccionIdOrderByCalculadoEnDescIdDesc(inspeccionId);
    ResultadoIc original =
        lista.stream()
            .filter(r -> "PUBLICACION".equals(r.getTipo()))
            .findFirst()
            .orElseThrow(() -> sinOriginal());
    Map<UUID, VersionFormulario> versionesPorId =
        versiones
            .findAllById(
                lista.stream().map(ResultadoIc::getVersionConfiguracionId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(VersionFormulario::getId, Function.identity()));
    Map<UUID, UserAccount> usuariosPorId =
        usuarios
            .findAllById(
                lista.stream()
                    .map(ResultadoIc::getSolicitadoPorId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    Function<ResultadoIc, ResultadoIcHistorialResponse> convertir =
        r ->
            respuesta(
                r,
                versionesPorId.get(r.getVersionConfiguracionId()),
                usuariosPorId.get(r.getSolicitadoPorId()));
    return new ComparacionIcResponse(
        inspeccionId,
        convertir.apply(original),
        lista.stream().filter(r -> "RECALCULO".equals(r.getTipo())).map(convertir).toList());
  }

  private ResultadoIcHistorialResponse respuesta(ResultadoIc r) {
    return respuesta(
        r,
        versiones.findById(r.getVersionConfiguracionId()).orElse(null),
        r.getSolicitadoPorId() == null
            ? null
            : usuarios.findById(r.getSolicitadoPorId()).orElse(null));
  }

  private ResultadoIcHistorialResponse respuesta(
      ResultadoIc r, VersionFormulario version, UserAccount usuario) {
    String nombre = usuario == null ? null : usuario.getNombreCompleto();
    if (usuario != null && (nombre == null || nombre.isBlank())) nombre = usuario.getEmail();
    return new ResultadoIcHistorialResponse(
        r.getId(),
        r.getInspeccionId(),
        r.getTipo(),
        r.getVersionConfiguracionId(),
        version == null ? null : version.getCodigo(),
        r.getIndiceCalculado(),
        r.getEstadoPorRango(),
        r.getEstadoCalculado(),
        r.getEstadoConfirmado(),
        r.getJustificacionAjuste(),
        r.getCondicionesAnulacion(),
        r.getDesglose(),
        r.getHashEntrada(),
        r.getVersionAlgoritmo(),
        r.getSolicitadoPorId(),
        nombre,
        r.getCalculadoEn());
  }

  private void validarPublicada(Inspeccion inspeccion) {
    if (!"PUBLICADA".equals(inspeccion.getEstado()) || inspeccion.getPublicadoEn() == null)
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_no_publicada",
          "El recalculo requiere una inspeccion publicada.");
  }

  private ApiException noEncontrada() {
    return error(
        HttpStatus.NOT_FOUND, "inspeccion_no_encontrada", "No existe la inspeccion solicitada.");
  }

  private ApiException sinOriginal() {
    return error(
        HttpStatus.CONFLICT,
        "ic_original_no_disponible",
        "La inspeccion publicada no tiene un IC original registrado.");
  }

  private ApiException error(HttpStatus estado, String codigo, String detalle) {
    return new ApiException(estado, codigo, "Recalculo del indice de condicion", detalle);
  }
}
