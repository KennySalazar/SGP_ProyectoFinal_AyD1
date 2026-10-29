package gt.usac.cunoc.sgp.curso.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CursoMapperNombreTest {
  final CursoMapper mapper = Mappers.getMapper(CursoMapper.class);

  @Test
  void devuelveElNombreCompletoSinSustituirloPorElCorreo() {
    var usuario = mock(UserAccount.class);
    when(usuario.getNombreCompleto()).thenReturn("Kenny Salazar");
    when(usuario.getEmail()).thenReturn("kenny@example.com");
    assertThat(mapper.toCatedraticoResponse(usuario).nombre()).isEqualTo("Kenny Salazar");
  }

  @Test
  void usaElNombreDeUsuarioEnCuentasAnterioresSinPerfilCompleto() {
    var usuario = mock(UserAccount.class);
    when(usuario.getNombreUsuario()).thenReturn("Kenny");
    assertThat(mapper.toCatedraticoResponse(usuario).nombre()).isEqualTo("Kenny");
  }
}
