
package gt.usac.cunoc.sgp.usuario.dto;

import java.time.Instant;
import java.util.UUID;

public record ChallengeResponse(UUID challengeId, Instant expiresAt, String message) {}
