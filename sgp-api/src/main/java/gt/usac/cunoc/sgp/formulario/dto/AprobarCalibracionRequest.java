package gt.usac.cunoc.sgp.formulario.dto;

import jakarta.validation.constraints.Size;

public record AprobarCalibracionRequest(
    @Size(max = 2000, message = "Las observaciones no deben superar 2000 caracteres")
        String observaciones) {}
