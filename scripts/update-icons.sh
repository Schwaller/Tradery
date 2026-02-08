#!/bin/bash
# Update app icons from artwork PNGs
# Usage: ./scripts/update-icons.sh

set -e

ARTWORK_DIR="$(dirname "$0")/../artwork"
SCRIPT_DIR="$(dirname "$0")"

# Convert a PNG to icns
# Usage: png_to_icns <source.png> <dest.icns>
png_to_icns() {
    local src="$1"
    local dest="$2"
    local iconset="/tmp/icon_$$.iconset"

    if [ ! -f "$src" ]; then
        echo "ERROR: Source not found: $src"
        return 1
    fi

    echo "Converting: $(basename "$src") -> $(basename "$dest")"

    rm -rf "$iconset"
    mkdir -p "$iconset"
    mkdir -p "$(dirname "$dest")"

    sips -z 16 16     "$src" --out "${iconset}/icon_16x16.png" > /dev/null
    sips -z 32 32     "$src" --out "${iconset}/icon_16x16@2x.png" > /dev/null
    sips -z 32 32     "$src" --out "${iconset}/icon_32x32.png" > /dev/null
    sips -z 64 64     "$src" --out "${iconset}/icon_32x32@2x.png" > /dev/null
    sips -z 128 128   "$src" --out "${iconset}/icon_128x128.png" > /dev/null
    sips -z 256 256   "$src" --out "${iconset}/icon_128x128@2x.png" > /dev/null
    sips -z 256 256   "$src" --out "${iconset}/icon_256x256.png" > /dev/null
    sips -z 512 512   "$src" --out "${iconset}/icon_256x256@2x.png" > /dev/null
    sips -z 512 512   "$src" --out "${iconset}/icon_512x512.png" > /dev/null
    sips -z 1024 1024 "$src" --out "${iconset}/icon_512x512@2x.png" > /dev/null

    iconutil -c icns "$iconset" -o "$dest"
    rm -rf "$iconset"

    echo "  Created: $dest ($(du -h "$dest" | cut -f1))"
}

# Find latest version of an icon (V2, V1, etc)
find_latest_icon() {
    local base_name="$1"
    for v in V3 V2 V1; do
        local path="${ARTWORK_DIR}/${base_name} ${v}.png"
        if [ -f "$path" ]; then
            echo "$path"
            return 0
        fi
    done
    echo ""
    return 1
}

echo "=== Updating App Icons ==="
echo ""

# Strategy Forge
FORGE_SRC=$(find_latest_icon "Forge Icon")
if [ -n "$FORGE_SRC" ]; then
    png_to_icns "$FORGE_SRC" "$(dirname "$0")/../tradery-forge/src/main/resources/icon.icns"
fi

# Strategy Runner
RUNNER_SRC=$(find_latest_icon "Runner Icon")
if [ -n "$RUNNER_SRC" ]; then
    png_to_icns "$RUNNER_SRC" "$(dirname "$0")/../tradery-runner/src/main/resources/icon.icns"
fi

# Trading Desk
DESK_SRC=$(find_latest_icon "Trading Desk Icon")
if [ -n "$DESK_SRC" ]; then
    png_to_icns "$DESK_SRC" "$(dirname "$0")/../tradery-desk/src/main/resources/icon.icns"
fi

# Intelligence
INTEL_SRC=$(find_latest_icon "Intelligence Icon")
if [ -n "$INTEL_SRC" ]; then
    png_to_icns "$INTEL_SRC" "$(dirname "$0")/../tradery-news/src/main/resources/icon.icns"
fi

echo ""
echo "=== Done ==="
echo ""
echo "To rebuild apps with new icons:"
echo "  ./gradlew :tradery-forge:jpackage"
echo "  ./gradlew :tradery-runner:jpackage"
echo "  ./gradlew :tradery-desk:jpackage"
echo "  ./gradlew :tradery-news:jpackage"
