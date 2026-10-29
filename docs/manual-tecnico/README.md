# Generación del PDF

## Dependencias

- **Pandoc** para ensamblar y convertir los módulos Markdown.
- **XeLaTeX**, incluido en TeX Live o MacTeX, como motor PDF.
- **Node.js/npm** y **Mermaid CLI**. Instala la CLI con `npm install --global @mermaid-js/mermaid-cli`.
- **Python 3** para procesar los bloques Mermaid sin editar los originales.

En Debian/Ubuntu, una instalación típica de los componentes del sistema es:

```bash
sudo apt install pandoc texlive-xetex texlive-latex-extra texlive-fonts-recommended
npm install --global @mermaid-js/mermaid-cli
```

Mermaid CLI necesita Chromium y sus dependencias de sistema; sigue la guía de instalación de `@mermaid-js/mermaid-cli` si la primera ejecución informa que no puede iniciar Chromium.

## Compilación

Desde la raíz del repositorio:

```bash
./generar-pdf.sh
```

El script ordena automáticamente los archivos `NN_*.md` por su prefijo numérico, renderiza los bloques Mermaid como PDF vectorial y escribe `docs/manual-tecnico/MANUAL_TECNICO_SGP.pdf`. Los archivos intermedios quedan en `docs/manual-tecnico/build/`, excluido de Git por la regla existente para directorios `build/`.

Edita `metadata.yaml` para cambiar título, autores, institución o fecha. La apariencia de impresión se define en `plantilla.tex`; los Markdown permanecen independientes de esos estilos.
