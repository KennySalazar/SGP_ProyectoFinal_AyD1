package gt.usac.cunoc.sgp.puente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.dto.DarBajaPuenteRequest;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository.CoordenadaUtmProjection;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

class PuenteBajaReactivacionServiceTest {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  private final PuenteRepository repository = mock(PuenteRepository.class);
  private final PuenteMapper mapper = mock(PuenteMapper.class);
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final Instant fija = Instant.parse("2026-10-06T12:00:00Z");
  private final Clock clock = Clock.fixed(fija, ZoneOffset.UTC);

  private PuenteService service;

  @BeforeEach
  void setUp() {
    service =
        new PuenteService(
            mock(DepartamentoRepository.class),
            mock(MunicipioRepository.class),
            repository,
            mapper,
            validator,
            clock);
  }

  private Puente crearPuenteActivo(UUID id) {
    return new Puente(
        id,
        "GT-01-0114-0001",
        1,
        null,
        "Puente Las Flores",
        "CA-1",
        BigDecimal.TEN,
        GEOMETRY_FACTORY.createPoint(new Coordinate(-90.5, 14.5)),
        UUID.randomUUID(),
        fija);
  }

  @Test
  void darDeBajaExitosoConMotivo() {
    UUID puenteId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Puente puente = crearPuenteActivo(puenteId);

    when(repository.findPuenteConRelacionesById(puenteId)).thenReturn(Optional.of(puente));
    when(repository.saveAndFlush(any(Puente.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.calcularUtm(anyDouble(), anyDouble()))
        .thenReturn(mock(CoordenadaUtmProjection.class));

    service.darDeBaja(puenteId, new DarBajaPuenteRequest("demolido"), adminId);

    assertThat(puente.isActivo()).isFalse();
    assertThat(puente.getMotivoInactivacion()).isEqualTo("demolido");
    assertThat(puente.getInactivadoPorId()).isEqualTo(adminId);
    assertThat(puente.getInactivadoEn()).isEqualTo(fija);
    verify(repository).saveAndFlush(puente);
  }

  @Test
  void darDeBajaLanzaNotFoundSiNoExiste() {
    UUID puenteId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    when(repository.findPuenteConRelacionesById(puenteId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.darDeBaja(puenteId, new DarBajaPuenteRequest("demolido"), adminId))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("puente_no_encontrado"));
  }

  @Test
  void darDeBajaLanzaConflictSiYaEstaInactivo() {
    UUID puenteId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Puente puente = crearPuenteActivo(puenteId);
    puente.darDeBaja("previo", adminId, fija);

    when(repository.findPuenteConRelacionesById(puenteId)).thenReturn(Optional.of(puente));

    assertThatThrownBy(
            () -> service.darDeBaja(puenteId, new DarBajaPuenteRequest("demolido"), adminId))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("puente_ya_inactivo"));
  }

  @Test
  void reactivarExitoso() {
    UUID puenteId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Puente puente = crearPuenteActivo(puenteId);
    puente.darDeBaja("demolido", adminId, fija);

    when(repository.findPuenteConRelacionesById(puenteId)).thenReturn(Optional.of(puente));
    when(repository.saveAndFlush(any(Puente.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.calcularUtm(anyDouble(), anyDouble()))
        .thenReturn(mock(CoordenadaUtmProjection.class));

    service.reactivar(puenteId, adminId);

    assertThat(puente.isActivo()).isTrue();
    assertThat(puente.getMotivoInactivacion()).isNull();
    assertThat(puente.getInactivadoPorId()).isNull();
    assertThat(puente.getInactivadoEn()).isNull();
    verify(repository).saveAndFlush(puente);
  }

  @Test
  void reactivarLanzaConflictSiYaEstaActivo() {
    UUID puenteId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Puente puente = crearPuenteActivo(puenteId);

    when(repository.findPuenteConRelacionesById(puenteId)).thenReturn(Optional.of(puente));

    assertThatThrownBy(() -> service.reactivar(puenteId, adminId))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("puente_ya_activo"));
  }
}
