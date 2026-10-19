package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.*;
import gt.usac.cunoc.sgp.inspeccion.service.FotografiaService;
import java.util.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inspecciones/{id}/fotografias")
@PreAuthorize("isAuthenticated()")
public class FotografiaController {
  private final FotografiaService servicio;

  public FotografiaController(FotografiaService servicio) {
    this.servicio = servicio;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<FotografiaResponse> adjuntar(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String clave,
      @RequestPart("metadatos") AdjuntarFotografiaRequest metadatos,
      @RequestPart("archivo") MultipartFile archivo,
      Authentication auth) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(servicio.adjuntar(id, actor(auth), metadatos, archivo, clave));
  }

  @GetMapping
  public List<FotografiaResponse> listar(@PathVariable UUID id, Authentication auth) {
    var identidad = identidad(auth);
    return servicio.listar(id, identidad.userId(), identidad.role());
  }

  @DeleteMapping("/{fotoId}")
  public ResponseEntity<Void> retirar(
      @PathVariable UUID id, @PathVariable UUID fotoId, Authentication auth) {
    servicio.retirar(id, fotoId, actor(auth));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{fotoId}/contenido")
  public ResponseEntity<InputStreamResource> contenido(
      @PathVariable UUID id, @PathVariable UUID fotoId, Authentication auth) {
    return leer(id, fotoId, auth, false);
  }

  @GetMapping("/{fotoId}/miniatura")
  public ResponseEntity<InputStreamResource> miniatura(
      @PathVariable UUID id, @PathVariable UUID fotoId, Authentication auth) {
    return leer(id, fotoId, auth, true);
  }

  private ResponseEntity<InputStreamResource> leer(
      UUID id, UUID foto, Authentication auth, boolean mini) {
    var identidad = identidad(auth);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(foto + (mini ? "-mini" : "") + ".jpg")
                .build()
                .toString())
        .body(
            new InputStreamResource(
                servicio.contenido(id, foto, identidad.userId(), identidad.role(), mini)));
  }

  private UUID actor(Authentication auth) {
    return identidad(auth).userId();
  }

  private JwtData identidad(Authentication auth) {
    if (auth == null || !(auth.getDetails() instanceof JwtData jwt))
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    return jwt;
  }
}
