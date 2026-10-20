package gt.usac.cunoc.sgp.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PrepararSalidaRequest(
    @NotBlank @Size(max = 100) String dispositivoId, List<UUID> puentesIds) {}
