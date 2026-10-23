package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.DocumentoInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.service.DocumentoInspeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Documentos PDF de una inspección (HU-070); el contenido se entrega por un extremo autorizado. */
@RestController
@RequestMapping("/api/v1/inspecciones/{id}/documentos")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Documentos de inspección", description = "Adjuntos PDF de una inspección")
public class DocumentoInspeccionController {

  private final DocumentoInspeccionService documentos;

  public DocumentoInspeccionController(DocumentoInspeccionService documentos) {
    this.documentos = documentos;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Adjuntar un documento PDF",
      description =
          "Solo el autor y en borrador (403/409). Valida la firma binaria del PDF (422), hasta 20"
              + " MB (413) y hasta 10 documentos por inspección (422). 503 si el almacenamiento no"
              + " responde; en ese caso no queda ningún registro.")
  public ResponseEntity<DocumentoInspeccionResponse> adjuntar(
      @PathVariable UUID id,
      @RequestPart("archivo") MultipartFile archivo,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(documentos.adjuntar(id, identidad(authentication).userId(), archivo));
  }

  @GetMapping
  @Operation(summary = "Listar los documentos PDF disponibles de una inspección")
  public List<DocumentoInspeccionResponse> listar(
      @PathVariable UUID id, Authentication authentication) {
    JwtData jwt = identidad(authentication);
    return documentos.listar(id, jwt.userId(), jwt.role());
  }

  @GetMapping("/{documentoId}/contenido")
  @Operation(summary = "Descargar un documento PDF con su nombre original")
  public ResponseEntity<InputStreamResource> descargar(
      @PathVariable UUID id, @PathVariable UUID documentoId, Authentication authentication) {
    JwtData jwt = identidad(authentication);
    var descarga = documentos.descargar(id, documentoId, jwt.userId(), jwt.role());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(descarga.nombre(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new InputStreamResource(descarga.contenido()));
  }

  @DeleteMapping("/{documentoId}")
  @Operation(
      summary = "Retirar un documento de un borrador propio",
      description = "Después del envío los documentos quedan fijos (409).")
  public ResponseEntity<Void> retirar(
      @PathVariable UUID id, @PathVariable UUID documentoId, Authentication authentication) {
    documentos.retirar(id, documentoId, identidad(authentication).userId());
    return ResponseEntity.noContent().build();
  }

  private static JwtData identidad(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    return jwt;
  }
}
