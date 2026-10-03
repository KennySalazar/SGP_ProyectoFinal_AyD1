package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.MunicipioResponse;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogos")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Catálogos territoriales", description = "Departamentos y municipios del INE")
@SecurityRequirement(name = "bearerAuth")
public class CatalogoTerritorialController {

  private final CatalogoTerritorialService catalogoTerritorialService;

  public CatalogoTerritorialController(CatalogoTerritorialService catalogoTerritorialService) {
    this.catalogoTerritorialService = catalogoTerritorialService;
  }

  @GetMapping("/departamentos")
  @Operation(summary = "Listar departamentos activos")
  public Page<DepartamentoResponse> listarDepartamentos(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "100") int tamanio) {
    return catalogoTerritorialService.listarDepartamentos(pagina, tamanio);
  }

  @GetMapping("/departamentos/{departamentoId}/municipios")
  @Operation(summary = "Listar municipios activos de un departamento")
  public Page<MunicipioResponse> listarMunicipios(
      @PathVariable UUID departamentoId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "100") int tamanio) {
    return catalogoTerritorialService.listarMunicipios(departamentoId, pagina, tamanio);
  }
}
