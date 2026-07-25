#!/data/data/com.termux/files/usr/bin/bash
# Update Termux APK from latest GitHub Actions build
# Requires: gh CLI (authenticated), termux-open (termux-api)

set -euo pipefail

REPO="Leonisaurov/termux-launcher"
WORKFLOW="Build quick (arm64-only)"
ARTIFACT_NAME="termux-app-split.apk"
DEST_DIR="$HOME/storage/downloads"
TMP_DIR="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/termux-update"

echo "🔍 Buscando última build exitosa..."
LATEST_RUN=$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --status success --json databaseId --jq '.[0].databaseId' 2>/dev/null)

if [ -z "$LATEST_RUN" ]; then
    echo "❌ No se encontró ninguna build exitosa reciente"
    exit 1
fi

echo "📦 Descargando artifact #$LATEST_RUN..."
mkdir -p "$TMP_DIR"
cd "$TMP_DIR"
gh run download --repo "$REPO" "$LATEST_RUN" --name "$ARTIFACT_NAME" 2>/dev/null

# Extract if it's a zip (GitHub siempre comprime artifacts)
if [ -f "$ARTIFACT_NAME.zip" ]; then
    echo "📂 Extrayendo APK del zip..."
    unzip -o "$ARTIFACT_NAME.zip" -d "$TMP_DIR"
    rm "$ARTIFACT_NAME.zip"
fi

# Find the APK file
APK_FILE=$(find "$TMP_DIR" -name "*.apk" -type f 2>/dev/null | head -1)

if [ -z "$APK_FILE" ]; then
    echo "❌ No se encontró APK en el artifact"
    exit 1
fi

APK_SIZE=$(ls -lh "$APK_FILE" | awk '{print $5}')
echo "✅ APK descargado: $(basename "$APK_FILE") ($APK_SIZE)"

# Copy to shared Downloads for accessibility
if [ -d "$DEST_DIR" ]; then
    cp "$APK_FILE" "$DEST_DIR/termux-split-update.apk"
    echo "📋 Copiado a $DEST_DIR/termux-split-update.apk"
fi

# Open with Android package installer
echo "📲 Abriendo instalador..."
termux-open "$APK_FILE"

echo "✨ Listo. Sigue las instrucciones en pantalla para instalar."
