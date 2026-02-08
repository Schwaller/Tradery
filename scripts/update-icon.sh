#!/bin/bash
# Regenerates icon.icns from application-icon.png for macOS app bundle

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ICONS_DIR="$PROJECT_DIR/src/main/resources/icons"
SOURCE_PNG="$ICONS_DIR/application-icon.png"
ICONSET_DIR="$ICONS_DIR/Plaiiin.iconset"
OUTPUT_ICNS="$PROJECT_DIR/src/main/resources/icon.icns"

if [ ! -f "$SOURCE_PNG" ]; then
    echo "Error: Source icon not found at $SOURCE_PNG"
    exit 1
fi

echo "Creating iconset from $SOURCE_PNG..."

mkdir -p "$ICONSET_DIR"

sips -z 16 16 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_16x16.png" > /dev/null
sips -z 32 32 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_16x16@2x.png" > /dev/null
sips -z 32 32 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_32x32.png" > /dev/null
sips -z 64 64 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_32x32@2x.png" > /dev/null
sips -z 128 128 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_128x128.png" > /dev/null
sips -z 256 256 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_128x128@2x.png" > /dev/null
sips -z 256 256 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_256x256.png" > /dev/null
sips -z 512 512 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_256x256@2x.png" > /dev/null
sips -z 512 512 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_512x512.png" > /dev/null
sips -z 1024 1024 "$SOURCE_PNG" --out "$ICONSET_DIR/icon_512x512@2x.png" > /dev/null

echo "Converting to ICNS..."
iconutil -c icns "$ICONSET_DIR" -o "$OUTPUT_ICNS"

rm -rf "$ICONSET_DIR"

echo "Done: $OUTPUT_ICNS"
