package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.model.RoleName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambiarRolRequest(
    @NotNull(message = "El rol es obligatorio") RoleName rol,
    /** Obligatorio cuando el nuevo rol es Profesional Externo (RN-USR-04). */
    @Size(max = 50, message = "El numero de colegiado no debe superar 50 caracteres")
        @Pattern(
            regexp = "^\\s*[A-Za-z0-9-]*\\s*$",
            message = "El numero de colegiado solo admite letras, digitos y guiones")
        String numeroColegiado) {}
