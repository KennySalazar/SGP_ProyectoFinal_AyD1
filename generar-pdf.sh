#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MANUAL_DIR="$ROOT_DIR/docs/manual-tecnico"
BUILD_DIR="$MANUAL_DIR/build"
OUTPUT_PDF="$MANUAL_DIR/MANUAL_TECNICO_SGP.pdf"

missing=()
for tool in pandoc xelatex mmdc python3; do
  if ! command -v "$tool" >/dev/null 2>&1; then missing+=("$tool"); fi
done
if ((${#missing[@]})); then
  printf 'Error: faltan dependencias requeridas: %s.\n' "${missing[*]}" >&2
  printf '%s\n' \
    'Instala Pandoc, XeLaTeX (TeX Live) y Mermaid CLI (@mermaid-js/mermaid-cli).' \
    'Consulta docs/manual-tecnico/README.md para instrucciones.' >&2
  exit 1
fi

mkdir -p "$BUILD_DIR"
python3 - "$MANUAL_DIR" "$BUILD_DIR" <<'PY'
from pathlib import Path
import re
import subprocess
import sys

manual_dir = Path(sys.argv[1])
build_dir = Path(sys.argv[2])
generales_dir = manual_dir / "generales"
generales = sorted(
    (p for p in generales_dir.glob("[0-9][0-9]_*.md") if p.is_file()),
    key=lambda p: int(p.name[:2]),
)
modules = sorted(
    (p for p in manual_dir.glob("[0-9][0-9]_*.md") if p.is_file()),
    key=lambda p: int(p.name[:2]),
)
if [int(p.name[:2]) for p in generales] != list(range(1, 8)):
    sys.exit(f"Error: se requieren los documentos generales 01–07 en {generales_dir}")
if [int(p.name[:2]) for p in modules] != list(range(1, 15)):
    sys.exit(f"Error: se requieren los módulos 01–14 en {manual_dir}")

combined = []
diagram_number = 0
for document in generales + modules:
    text = document.read_text(encoding="utf-8")
    parts = []
    cursor = 0
    for match in re.finditer(r"(?m)^```mermaid\s*\n(.*?)^```\s*$", text, re.DOTALL):
        parts.append(text[cursor:match.start()])
        diagram_number += 1
        source = build_dir / f"mermaid-{diagram_number:02d}.mmd"
        output = build_dir / f"mermaid-{diagram_number:02d}.pdf"
        source.write_text(match.group(1).rstrip() + "\n", encoding="utf-8")
        try:
            subprocess.run(
                ["mmdc", "-i", str(source), "-o", str(output), "-e", "pdf"],
                check=True,
            )
        except subprocess.CalledProcessError:
            sys.exit(f"Error: Mermaid CLI no pudo renderizar el diagrama {diagram_number} ({document.name}).")
        if not output.is_file() or output.stat().st_size == 0:
            sys.exit(f"Error: Mermaid CLI no produjo el diagrama vectorial {output.name}.")
        alt = f"Diagrama {diagram_number}: {document.stem.replace('_', ' ')}"
        # La plantilla limita cada imagen tanto al ancho disponible como a la altura de página.
        parts.append(f"![{alt}](build/{output.name})")
        cursor = match.end()
    parts.append(text[cursor:])
    combined.append("".join(parts).rstrip())

combined_path = build_dir / "manual-completo.md"
combined_path.write_text("\n\n".join(combined) + "\n", encoding="utf-8")
print(f"Documentos generales incluidos: {len(generales)} ({generales[0].name}–{generales[-1].name})")
print(f"Módulos incluidos: {len(modules)} ({modules[0].name}–{modules[-1].name})")
print(f"Diagramas Mermaid vectoriales: {diagram_number}")
PY

cd "$MANUAL_DIR"
pandoc "$BUILD_DIR/manual-completo.md" \
  --from markdown+raw_tex \
  --standalone \
  --metadata-file metadata.yaml \
  --template plantilla.tex \
  --pdf-engine=xelatex \
  --table-of-contents \
  --resource-path=.:build \
  --output "$OUTPUT_PDF"

printf 'PDF generado: %s\n' "$OUTPUT_PDF"
