package gt.usac.cunoc.sgp.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VerifyRegistrationRequest(
    @NotBlank(message = "El correo electronico es obligatorio") String email,
    @NotNull(message = "El identificador del desafio es obligatorio") UUID challengeId,
    @NotBlank(message = "El codigo OTP es obligatorio") String otp) {}
