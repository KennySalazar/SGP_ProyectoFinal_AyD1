package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.puente.dto.UbicacionTerritorialResponse;
import gt.usac.cunoc.sgp.puente.service.UbicacionTerritorialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogos")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
@Tag(name = "Catálogos territoriales")
@SecurityRequirement(name = "bearerAuth")
public class UbicacionTerritorialController {

  private final UbicacionTerritorialService service;

  public UbicacionTerritorialController(UbicacionTerritorialService service) {
    this.service = service;
  }

  @GetMapping("/ubicacion")
  @Operation(
      summary = "Resolver departamento y municipio por coordenadas",
      description =
          "Devuelve los municipios activos cuyos polígonos cubren el punto. "
              + "Si existen varios candidatos, requiere selección entre ellos. "
              + "Incluye la zona UTM correspondiente.")
  public UbicacionTerritorialResponse resolver(
      @RequestParam(name = "latitud") double latitud,
      @RequestParam(name = "longitud") double longitud) {
    return service.resolver(latitud, longitud);
  }
}
