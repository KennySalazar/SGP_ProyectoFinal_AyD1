# Guía de interfaz

Esta guía reúne las reglas visuales y de experiencia que ya están implementadas en `sgp-client`. Las nuevas funcionalidades deben conservar estos patrones y mantener coherencia con ellos al introducir nuevas necesidades de interfaz.

## Tecnologías de interfaz

- Angular 21 con componentes standalone, carga diferida por ruta y estrategia `OnPush`.
- PrimeNG 21 para controles de interfaz, PrimeIcons 7 para iconografía y PrimeUIX Themes con el preset Aura como base.
- SCSS para estilos globales, estilos por componente y reglas responsive.
- Angular Reactive Forms para los formularios de autenticación y seguridad.
- Signals para estado local y transversal visible en la interfaz.
- Transloco con español como idioma único configurado; los textos visibles deben mantenerse en `public/i18n/es.json`.

## Principios de interfaz

- Mantener una jerarquía clara: kicker en mayúsculas, título principal, descripción y contenido agrupado en superficies.
- Reutilizar estructuras y componentes existentes antes de crear variantes visuales nuevas.
- Mostrar siempre texto junto a colores, iconos y estados; el color no debe ser el único indicador.
- Dar retroalimentación dentro del contexto de la acción y reservar el aviso global para errores de API.
- Conservar áreas de interacción cómodas: botones con altura mínima de `44px` e inputs de `46px`.
- Adaptar la composición mediante las transformaciones responsive ya establecidas, sin reducir únicamente el tamaño del contenido.

## Tema y estilos globales

- El tema de PrimeNG extiende Aura y redefine la escala semántica `primary` con azules entre `#eff6ff` y `#0b1739`; el tono principal `500` es `#2563eb`.
- PrimeNG tiene ripple habilitado. Los botones e inputs reciben peso, dimensiones y foco comunes desde `styles.scss`.
- La aplicación utiliza fondo `#eef3f8` con una cuadrícula azul muy tenue y superficies blancas elevadas.
- Los tokens globales disponibles son `--sgp-navy-950`, `--sgp-navy-900`, `--sgp-navy-800`, `--sgp-blue-600`, `--sgp-cyan-500`, `--sgp-steel-700`, `--sgp-steel-500`, `--sgp-surface`, `--sgp-surface-soft`, `--sgp-border` y `--sgp-shadow`.
- Deben preferirse esos tokens cuando representen el valor requerido. El código actual aún combina tokens con valores hexadecimales repetidos; esto no constituye una escala completa de espaciado, bordes o sombras.
- La clase global `.surface-card` define la superficie reutilizable: fondo blanco casi opaco, borde `--sgp-border`, radio de `18px` y sombra ligera.
- Existe un selector técnico `.sgp-dark` en la configuración de PrimeNG, pero no hay activación, estilos ni experiencia de modo oscuro implementados. No debe tratarse como una funcionalidad existente.

## Colores

- Azul primario `#2563eb`: acciones principales, kickers, iconos destacados, foco y navegación activa.
- Navy `#0b1f33` y `#071426`: títulos, marca y fondos del sidebar o del panel visual de autenticación.
- Superficie `#ffffff` y fondo `#eef3f8`: tarjetas, áreas de trabajo y fondo general.
- Borde `#d8e2ec`: delimitación principal de tarjetas, cabecera e inputs tematizados.
- Grises azulados `#40546b` y `#708399`: texto secundario, metadatos y descripciones.
- Verde: estados correctos, disponibilidad y confirmaciones; ámbar: advertencias o estados regulares; rojo: errores, desconexión y condición negativa.
- Los estados deben conservar una etiqueta textual o un mensaje además del color, como ocurre en conectividad, 2FA y condición del puente.

## Tipografía

- La familia global es `Inter` con fallback a `ui-sans-serif`, fuentes del sistema y `Segoe UI`; el proyecto no carga una fuente web propia.
- Los títulos usan navy, peso alto y espaciado negativo. El título de página utiliza `clamp(1.8rem, 3vw, 2.45rem)`.
- Kickers, etiquetas de sección y metadatos usan tamaños pequeños, peso alto, mayúsculas y espaciado amplio entre letras.
- El texto descriptivo usa gris azulado, altura de línea aproximada entre `1.45` y `1.65` y ancho contenido cuando corresponde.
- No existe una escala tipográfica formal adicional; deben reutilizarse las clases globales y proporciones observadas antes de introducir tamaños nuevos.

## Layout y composición

- Las rutas públicas de autenticación usan `AuthCardComponent`: panel visual navy a la izquierda y tarjeta de formulario de hasta `31rem` a la derecha.
- Las rutas autenticadas usan `AppShellComponent`: sidebar sticky de `292px`, barra superior sticky de `76px` y contenido de hasta `1280px`.
- El catálogo público `/puentes` reutiliza `AppShellComponent` mediante contenido
  proyectado cuando existe sesión; sin sesión conserva una presentación pública.
  El shell mantiene su `router-outlet` para las demás rutas, sin exigir autenticación al catálogo.
- Cada pantalla autenticada inicia con `.page-heading`, `.page-kicker`, `.page-title` y `.page-description`, salvo composiciones equivalentes del dashboard.
- El contenido se organiza en grids y tarjetas blancas con bordes sutiles, radios entre `16px` y `20px` y sombras de baja opacidad.
- Las tarjetas agrupan información relacionada; sus cabeceras suelen separar título, contexto y estado mediante borde inferior.
- La navegación muestra icono PrimeIcons y texto, resalta la ruta activa y presenta únicamente las opciones permitidas por el rol.
- El shell mantiene visibles el estado de conectividad, la sesión actual y la acción de cerrar sesión.

## Formularios

- Usar Reactive Forms con formularios no anulables y validadores declarados en TypeScript.
- Mantener cada control dentro de un `label`; el texto visible precede al input y la asociación se resuelve por anidamiento.
- Usar `pInputText` para entradas de texto, correo, contraseña y OTP.
- Conservar `type`, `autocomplete`, `inputmode` y `maxlength` apropiados. Los OTP actuales son numéricos, de seis dígitos, centrados y con espaciado entre caracteres.
- Los formularios se presentan en una columna con separación de `1rem`; la acción primaria ocupa todo el ancho.
- Los botones de envío permanecen deshabilitados mientras el formulario sea inválido y el handler vuelve a comprobar la validez.
- Cuando un campo tenga requisitos adicionales, deben mostrarse mediante una nota breve y visible junto al formulario.
- Existe la clase compartida `.validation`, pero las pantallas actuales no renderizan mensajes de validación por campo de forma consistente. No debe asumirse todavía un patrón visual completo para esos mensajes.

## Botones y acciones

- La acción principal utiliza `pButton`, texto explícito e icono PrimeIcons; en formularios se centra y ocupa todo el ancho.
- Los iconos complementan la etiqueta y no sustituyen el texto, excepto el cierre de aviso y el logout compacto en viewport reducido, ambos con contexto accesible o visual.
- El estado deshabilitado deriva de la validez del formulario.
- Las acciones irreversibles o que restringen el acceso (verificar un colegiado, desactivar o cambiar el rol de un usuario) se confirman en un `p-dialog` modal que muestra los datos afectados y explica la consecuencia. Sus botones son Cancelar (secundario, contorneado) y la acción explícita; los errores del servidor se muestran dentro del diálogo y no en el aviso global.
- La acción que retira el acceso a una cuenta usa `severity="danger"` con contorno en la tabla y relleno en el diálogo de confirmación. Las acciones reversibles, como reactivar, se ejecutan sin diálogo.
- Mientras una acción está en curso, su botón se deshabilita y su texto cambia a una forma en gerundio (por ejemplo, "Desactivando..."). No existe todavía un patrón de spinner ni skeleton.

## Mensajes y retroalimentación

- Los errores de API aparecen como un aviso global fijo con texto descriptivo y cierre manual.
- Las confirmaciones de una operación se muestran dentro de la tarjeta relacionada, en una superficie verde con icono y texto; seguridad usa `role="status"`.
- Las instrucciones y avisos informativos usan bloques suaves con borde, icono y texto breve.
- La conectividad se muestra persistentemente en la barra superior mediante punto de color y etiqueta `En linea` o `Sin conexion`.
- Las acciones que cambian de etapa reemplazan o complementan el formulario, por ejemplo solicitud y confirmación de OTP.
- No hay indicadores de carga implementados; la guía no prescribe spinner, skeleton ni bloqueo durante solicitudes.

## Componentes PrimeNG

- Los componentes usados actualmente son Button, InputText, Select, Textarea, Table, Paginator, Tag y Dialog.
- Deben preferirse componentes PrimeNG cuando exista un componente adecuado, aplicando el preset y los estilos globales del proyecto antes de crear controles personalizados.
- PrimeIcons es la fuente de iconos de acciones, navegación, estados e información.
- `p-tag` se usa para estados compactos con severidad semántica, actualmente en el estado de 2FA.

## Componentes compartidos

- `AuthCardComponent` concentra la composición, marca y superficie de todas las rutas públicas de autenticación.
- `form-controls.scss` concentra el patrón repetido de formularios de autenticación: campos, acción primaria, enlaces, notas y validación.
- Un elemento debe moverse a `shared/` cuando sea reutilizable entre varias features y no contenga lógica específica de un dominio.
- Los componentes exclusivos de una pantalla o feature deben permanecer dentro de ella, de acuerdo con `docs/ARCHITECTURE.md`.
- Los servicios visuales transversales, como normalización de errores y estado de sesión, permanecen en `core/`.

## Responsive

- A `980px`, el sidebar se convierte en navegación inferior fija; se ocultan marca, usuario y metadatos secundarios, y el logout queda como icono.
- A `900px`, autenticación elimina el panel visual y centra la tarjeta; seguridad pasa de dos columnas a una.
- Dashboard reorganiza sus grids a `1180px` y los apila a `700px`.
- Administración y diagnóstico apilan tarjetas a `760px`; seguridad reorganiza su cabecera y panel informativo a `650px`.
- Los enlaces de autenticación se apilan a `520px`.
- Los nuevos módulos deben seguir el comportamiento del shell y definir breakpoints adicionales solo cuando su contenido lo requiera.

## Accesibilidad

- Mantener un único título principal por pantalla y la jerarquía semántica de encabezados existente.
- Asociar cada input con una etiqueta visible y conservar atributos de autocompletado y teclado virtual adecuados.
- Incluir texto accesible en controles que puedan mostrarse solo como icono y usar `aria-label` en regiones de navegación cuando corresponda.
- Marcar ilustraciones decorativas como ocultas para tecnologías de asistencia.
- Usar `role="alert"` para errores que requieren atención y `role="status"` para confirmaciones no críticas.
- Mantener foco visible; los inputs usan un halo azul global. No eliminar los estados de foco provistos por el navegador o PrimeNG.
- No comunicar estados únicamente mediante color: acompañarlos con texto y, cuando ayude, icono.

## Reglas generales

- Reutilizar `AppShellComponent`, `AuthCardComponent`, clases globales y estilos compartidos según el tipo de pantalla.
- Mantener PrimeNG, PrimeIcons y el preset SGP para controles ya cubiertos por esas herramientas.
- Usar los tokens existentes antes de repetir un valor equivalente; no crear una paleta paralela dentro de una feature.
- Mantener estilos específicos encapsulados en el SCSS del componente y promoverlos a `shared/` solo cuando exista reutilización real.
- Mantener formularios con Reactive Forms, validación declarativa y acciones deshabilitadas cuando el formulario sea inválido.
- Mantener los textos visibles en Transloco y el español como idioma configurado actualmente.
- Preservar el encabezado de página, las superficies blancas y la composición responsive del shell en los nuevos módulos autenticados.
- Cuando una nueva funcionalidad requiera un patrón todavía no definido, debe construirse utilizando los componentes, tokens y convenciones existentes, y documentarse en esta guía cuando se convierta en un patrón reutilizable.
