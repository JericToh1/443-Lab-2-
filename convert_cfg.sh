#!/bin/bash
# ═══════════════════════════════════════════════════════
#  CS443 Lab 2 — DOT to PNG Converter
#  Converts one or all .dot CFG files to PNG images
# ═══════════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CFG_DIR="${SCRIPT_DIR}/cfg_output"
PNG_DIR="${SCRIPT_DIR}/cfg_png"

if [ ! -d "$CFG_DIR" ]; then
    echo "[!] No cfg_output/ directory found. Run the analysis first."
    exit 1
fi

mkdir -p "$PNG_DIR"

# If a specific file is given as argument, convert only that
if [ -n "$1" ]; then
    DOT_FILE="$1"
    if [ ! -f "$DOT_FILE" ]; then
        DOT_FILE="$CFG_DIR/$1"
    fi
    if [ ! -f "$DOT_FILE" ]; then
        echo "[!] File not found: $1"
        exit 1
    fi
    BASENAME=$(basename "$DOT_FILE" .dot)
    echo "[*] Converting: $DOT_FILE"
    dot -Tpng "$DOT_FILE" -o "$PNG_DIR/${BASENAME}.png"
    dot -Tpdf "$DOT_FILE" -o "$PNG_DIR/${BASENAME}.pdf"
    echo "[✓] Output: $PNG_DIR/${BASENAME}.png"
    echo "[✓] Output: $PNG_DIR/${BASENAME}.pdf"
    exit 0
fi

# Convert all .dot files
COUNT=0
for DOT_FILE in "$CFG_DIR"/*.dot; do
    [ -f "$DOT_FILE" ] || continue
    BASENAME=$(basename "$DOT_FILE" .dot)
    echo "[*] Converting: $(basename "$DOT_FILE")"
    dot -Tpng "$DOT_FILE" -o "$PNG_DIR/${BASENAME}.png"
    dot -Tpdf "$DOT_FILE" -o "$PNG_DIR/${BASENAME}.pdf"
    COUNT=$((COUNT + 1))
done

echo ""
echo "[✓] Converted $COUNT CFGs to PNG + PDF"
echo "[✓] Output directory: $PNG_DIR/"
