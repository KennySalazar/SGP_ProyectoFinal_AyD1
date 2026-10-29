/** Deriva un identificador técnico (a-z, 0-9 y guión bajo) a partir de un título legible. */
export function claveDesdeTitulo(titulo: string): string {
  return titulo
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/^\d+[.\-\s:]+/, '')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 60);
}

/** Propone el código consecutivo siguiente a partir de los códigos existentes (p. ej. SIECA-v6 → SIECA-v7). */
export function siguienteCodigoVersion(codigos: readonly string[]): string {
  const prefijos = new Map<string, { cantidad: number; maximo: number }>();
  let primero: string | null = null;
  for (const codigo of codigos) {
    const coincidencia = /^(.*?)(\d+)$/.exec(codigo.trim());
    if (!coincidencia) continue;
    const [, prefijo, numero] = coincidencia;
    primero ??= prefijo;
    const actual = prefijos.get(prefijo) ?? { cantidad: 0, maximo: 0 };
    prefijos.set(prefijo, {
      cantidad: actual.cantidad + 1,
      maximo: Math.max(actual.maximo, Number(numero)),
    });
  }
  if (primero === null) return 'SIECA-v1';
  let elegido = primero;
  for (const [prefijo, datos] of prefijos) {
    if (datos.cantidad > (prefijos.get(elegido)?.cantidad ?? 0)) elegido = prefijo;
  }
  return `${elegido}${(prefijos.get(elegido)?.maximo ?? 0) + 1}`;
}
