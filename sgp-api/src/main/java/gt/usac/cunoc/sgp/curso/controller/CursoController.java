package gt.usac.cunoc.sgp.curso.controller;

import gt.usac.cunoc.sgp.curso.dto.CatedraticoResponse;
import gt.usac.cunoc.sgp.curso.dto.ActualizarCursoRequest;
import gt.usac.cunoc.sgp.curso.dto.CrearCursoRequest;
import gt.usac.cunoc.sgp.curso.dto.CursoResponse;
import gt.usac.cunoc.sgp.curso.service.CursoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cursos")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cursos", description = "Catalogo academico")
public class CursoController {

  private final CursoService cursoService;

  public CursoController(CursoService cursoService) {
    this.cursoService = cursoService;
  }

  @GetMapping
  @Operation(summary = "Listar cursos")
  public Page<CursoResponse> listar(
      @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
    return cursoService.listar(pagina, tamanio);
  }

  @GetMapping("/catedraticos")
  @Operation(summary = "Listar catedraticos activos")
  public List<CatedraticoResponse> listarCatedraticos() {
    return cursoService.listarCatedraticos();
  }

  @PostMapping
  @Operation(summary = "Crear curso")
  public ResponseEntity<CursoResponse> crear(@Valid @RequestBody CrearCursoRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(cursoService.crear(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Editar curso")
  public CursoResponse actualizar(
      @PathVariable UUID id, @Valid @RequestBody ActualizarCursoRequest request) {
    return cursoService.actualizar(id, request);
  }

  @PatchMapping("/{id}/finalizar")
  @Operation(summary = "Finalizar curso vigente")
  public CursoResponse finalizar(@PathVariable UUID id) {
    return cursoService.finalizar(id);
  }
}
