#!/bin/bash
# ═══════════════════════════════════════════════════════
#  CS443 Lab 2 — Build & Run Script
#  Builds the Maven project and runs the Soot analysis
# ═══════════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/soot-analysis"

echo "╔══════════════════════════════════════════════════════╗"
echo "║   CS443 Lab 2 — Build & Run                         ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ── Step 1: Check prerequisites ──────────────────────────
echo "[1] Checking prerequisites..."

if ! command -v java &>/dev/null; then
    echo "[!] Java not found. Install: brew install --cask temurin@21"
    exit 1
fi

if ! command -v mvn &>/dev/null; then
    echo "[!] Maven not found. Install: brew install maven"
    exit 1
fi

ANDROID_PLATFORMS="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platforms"
if [ ! -d "$ANDROID_PLATFORMS" ]; then
    echo "[!] Android SDK platforms not found at: $ANDROID_PLATFORMS"
    echo "    Install: sdkmanager 'platforms;android-34'"
    exit 1
fi

echo "    Java    : $(java -version 2>&1 | head -1)"
echo "    Maven   : $(mvn -version 2>&1 | head -1)"
echo "    Android : $ANDROID_PLATFORMS"
echo ""

# ── Step 2: Build ─────────────────────────────────────────
echo "[2] Building Maven project..."
cd "$PROJECT_DIR"
mvn clean package -q -DskipTests

if [ $? -ne 0 ]; then
    echo "[!] Build failed. Check errors above."
    exit 1
fi

JAR_FILE=$(ls target/soot-analysis-*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "[!] Built JAR not found."
    exit 1
fi

echo "    Built: $JAR_FILE"
echo ""

# ── Step 3: Run ───────────────────────────────────────────
echo "[3] Running analysis..."
cd "$SCRIPT_DIR"
java -jar "$PROJECT_DIR/$JAR_FILE" \
    --apk "$SCRIPT_DIR/demo.apk" \
    --android-platforms "$ANDROID_PLATFORMS" \
    --sensitive-apis "$SCRIPT_DIR/sensitive_apis.csv" \
    --cfg-output "$SCRIPT_DIR/cfg_output" \
    --png-output "$SCRIPT_DIR/cfg_png" \
    --sensitive-output "$SCRIPT_DIR/sensitive_apis.txt"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  Done! Check:"
echo "    • cfg_output/        — CFG .dot files"
echo "    • cfg_png/           — Rendered CFG images"
echo "    • sensitive_apis.txt — Sensitive API report"
echo "═══════════════════════════════════════════════════════"
