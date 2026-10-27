package gt.usac.cunoc.sgp.formulario.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.EstadoActaCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.service.ActaCalibracionService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/formularios/{id}/acta-calibracion")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
public class ActaCalibracionController {
  private final ActaCalibracionService service;

  public ActaCalibracionController(ActaCalibracionService service) {
    this.service = service;
  }

  @GetMapping
  public EstadoActaCalibracionResponse consultar(@PathVariable UUID id) {
    return service.consultar(id);
  }

  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public EstadoActaCalibracionResponse adjuntar(
      @PathVariable UUID id,
      @RequestPart("archivo") MultipartFile archivo,
      @RequestHeader(value = "If-Match", required = false) String versionEsperada,
      Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData datos)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticacion requerida",
          "Se requiere una sesion valida.");
    }
    return service.adjuntar(
        id,
        archivo,
        datos.userId(),
        gt.usac.cunoc.sgp.formulario.validation.VersionCalibracionHttp.exigir(versionEsperada));
  }

  @GetMapping("/contenido")
  public ResponseEntity<byte[]> descargar(@PathVariable UUID id) {
    byte[] contenido = service.descargar(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(contenido.length)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            ContentDisposition.attachment().filename("acta-" + id + ".pdf").build().toString())
        .body(contenido);
  }
}
