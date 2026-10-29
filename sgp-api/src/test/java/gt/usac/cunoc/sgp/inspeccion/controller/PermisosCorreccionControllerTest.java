package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.PermisosCorreccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class PermisosCorreccionControllerTest {
  final InspeccionRepository inspecciones = mock(InspeccionRepository.class);
  final PermisosCorreccionService servicio = mock(PermisosCorreccionService.class);
  final PermisosCorreccionController controller =
      new PermisosCorreccionController(inspecciones, servicio);
  final UUID id = UUID.randomUUID(), autor = UUID.randomUUID();

  UsernamePasswordAuthenticationToken sesion(UUID actor) {
    var auth = new UsernamePasswordAuthenticationToken("estudiante", null, List.of());
    auth.setDetails(new JwtData("estudiante@example.com", actor, RoleName.ESTUDIANTE, 0));
    return auth;
  }

  @Test
  void soloElAutorPuedeConsultarSusPermisos() {
    var inspeccion = mock(Inspeccion.class);
    when(inspeccion.getAutorId()).thenReturn(autor);
    when(inspecciones.findByIdAndEliminadoEnIsNull(id)).thenReturn(Optional.of(inspeccion));
    var esperado = new PermisosCorreccionService.Permisos(true, List.of("/otros"), List.of());
    when(servicio.consultar(inspeccion)).thenReturn(esperado);
    assertThat(controller.consultar(id, sesion(autor))).isEqualTo(esperado);
    assertThatThrownBy(() -> controller.consultar(id, sesion(UUID.randomUUID())))
        .isInstanceOf(ApiException.class);
    verify(servicio, times(1)).consultar(inspeccion);
  }

  @Test
  void rechazaConsultasAnonimas() {
    assertThatThrownBy(() -> controller.consultar(id, null)).isInstanceOf(ApiException.class);
    verifyNoInteractions(inspecciones, servicio);
  }
}
