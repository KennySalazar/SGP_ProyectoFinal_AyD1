export interface EstadoActa {
  versionFormularioId: string;
  estado: 'SIN_ACTA' | 'CON_ACTA';
  mensaje: string;
  acta: {
    archivoId: string;
    nombreOriginal: string;
    mimeReal: string;
    tamanoBytes: number;
    checksumSha256: string;
    subidoPorId: string;
    subidoEn: string;
  } | null;
}
