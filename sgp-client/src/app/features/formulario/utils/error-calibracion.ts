export function mensajeCalibracion(error: unknown, traducir: (clave: string) => string): string {
  const fallo = error as {
    status?: number;
    error?: {
      code?: string;
      detail?: string;
      errores?: { campo?: string; mensaje?: string }[];
    };
  } | null;
  const problema = fallo?.error;
  if (problema?.code === 'concurrencia_conflicto' || fallo?.status === 428) {
    return traducir('formulario.hu053.conflicto');
  }
  if (problema?.code === 'version_inmutable') return traducir('formulario.hu053.inmutable');
  const campos: Record<string, string> = {
    peso: 'Peso',
    rangos_estado: 'Rangos de estado',
    escala_severidad: 'Escala de severidad',
    reglas_evaluacion_ic: 'Calculo del IC',
    conversiones: 'Equivalencias',
    indicadores: 'Indicadores',
    umbrales: 'Umbrales',
    umbrales_m: 'Umbrales en metros',
    reglas_anulacion: 'Reglas de anulacion',
    NO_PRESENTA: 'No presenta',
    LEVE: 'Leve',
    MODERADO: 'Moderado',
    SEVERO: 'Severo',
    BUENO: 'Bueno',
    REGULAR: 'Regular',
    MALO: 'Malo',
  };
  const detalles = problema?.errores
    ?.map((e) => {
      const nombre = (e.campo ?? '')
        .split(/[./]/)
        .filter(Boolean)
        .map((p) => campos[p] ?? p.replace(/_/g, ' '))
        .join(' > ');
      return [nombre, e.mensaje].filter(Boolean).join(': ');
    })
    .filter(Boolean);
  if (detalles?.length) return detalles.join(' · ');
  if (fallo?.status === 0) return traducir('formulario.hu053.sinConexion');
  return problema?.detail ?? traducir('common.operationFailed');
}
