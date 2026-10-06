const ESTILOS_MAPLIBRE = 'maplibre.css';

// El build de producción entrega esta librería UMD solo como `default`, sin exports nombrados.
export function resolverMaplibre(
  modulo: typeof import('maplibre-gl'),
): typeof import('maplibre-gl') {
  return typeof modulo.Map === 'function'
    ? modulo
    : (modulo as unknown as { default: typeof modulo }).default;
}

// El CSS se emite como bundle aparte (angular.json) para no engrosar el paquete inicial.
// La versión en la URL invalida la caché de un año que nginx aplica a los .css.
export function cargarEstilosMaplibre(version: string, alCargar?: () => void): void {
  if (document.querySelector('link[data-maplibre-estilos]')) return;

  const enlace = document.createElement('link');
  enlace.rel = 'stylesheet';
  enlace.href = new URL(`${ESTILOS_MAPLIBRE}?v=${version}`, document.baseURI).href;
  enlace.setAttribute('data-maplibre-estilos', '');

  if (alCargar) enlace.addEventListener('load', alCargar);

  document.head.appendChild(enlace);
}
