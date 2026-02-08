#!/bin/bash
# Publish Plaiiin apps to plaiiin.com
# Usage:
#   scripts/publish.sh                    # Publish all 3 apps (unsigned)
#   scripts/publish.sh forge              # Publish specific app(s)
#   scripts/publish.sh --signed forge     # Signed + notarized build
#   scripts/publish.sh desk intelligence

set -euo pipefail

cd "$(dirname "$0")/.."

# ── Config ──────────────────────────────────────────────────────────────────────

SERVER="root@plaiiin.com"
SERVER_BASE="/plaiiin-page/AppData"
BASE_URL="https://plaiiin.com/api/app"

VERSION=$(grep "version = " build.gradle | head -1 | sed "s/.*version = '//;s/'.*//")
DATE=$(date +%Y-%m-%d)
SIGNED=false

# ── App lookup ──────────────────────────────────────────────────────────────────

app_id() {
    case "$1" in
        forge)        echo "strategy-forge" ;;
        desk)         echo "trading-desk" ;;
        intelligence) echo "intelligence" ;;
        *) echo "UNKNOWN"; return 1 ;;
    esac
}

app_module() {
    case "$1" in
        forge)        echo "tradery-forge" ;;
        desk)         echo "tradery-desk" ;;
        intelligence) echo "tradery-news" ;;
    esac
}

app_dmg_name() {
    case "$1" in
        forge)        echo "Strategy Forge" ;;
        desk)         echo "Trading Desk" ;;
        intelligence) echo "Intelligence" ;;
    esac
}

# ── Parse args ──────────────────────────────────────────────────────────────────

APPS=""
for arg in "$@"; do
    if [ "$arg" = "--signed" ]; then
        SIGNED=true
    else
        APPS="$APPS $arg"
    fi
done
APPS=$(echo "$APPS" | xargs)  # trim whitespace

if [ -z "$APPS" ]; then
    APPS="forge desk intelligence"
fi

# Validate app names
for app in $APPS; do
    if ! app_id "$app" > /dev/null 2>&1; then
        echo "Unknown app: $app"
        echo "Valid apps: forge, desk, intelligence"
        exit 1
    fi
done

if [ "$SIGNED" = true ]; then
    BUILD_MODE="signed + notarized"
else
    BUILD_MODE="unsigned"
fi

echo "═══════════════════════════════════════════════════════════"
echo "  Plaiiin Publish v${VERSION}"
echo "  Apps: ${APPS}"
echo "  Build: ${BUILD_MODE}"
echo "  Date: ${DATE}"
echo "═══════════════════════════════════════════════════════════"
echo

# ── Server setup (idempotent) ───────────────────────────────────────────────────

setup_server() {
    local id
    id=$(app_id "$1")
    local display
    display=$(app_dmg_name "$1")

    echo "  Ensuring server directory: ${SERVER_BASE}/${id}/"
    ssh "$SERVER" "mkdir -p ${SERVER_BASE}/${id}/releases/mac-os"

    # Create app.json if it doesn't exist
    ssh "$SERVER" "test -f ${SERVER_BASE}/${id}/app.json || cat > ${SERVER_BASE}/${id}/app.json << APPJSON
{
  \"name\": \"${display}\",
  \"description\": \"${display} - early access\",
  \"category\": \"trading\"
}
APPJSON"
}

# ── Build ───────────────────────────────────────────────────────────────────────

build_app() {
    local module
    module=$(app_module "$1")
    local dmg_name
    dmg_name=$(app_dmg_name "$1")
    local dmg_file="${dmg_name}-${VERSION}.dmg"
    local dmg_path="${module}/build/jpackage/${dmg_file}"

    # Remove old build artifacts to avoid conflicts
    rm -f "$dmg_path"
    rm -rf "${module}/build/jpackage/${dmg_name}.app"

    echo "▸ Building ${dmg_name}..."
    if [ "$SIGNED" = true ]; then
        echo "  Running: ./gradlew :${module}:packageRelease"
        ./gradlew ":${module}:packageRelease" --no-daemon
    else
        echo "  Running: ./gradlew :${module}:jpackageDmg"
        ./gradlew ":${module}:jpackageDmg" --no-daemon
    fi

    if [ ! -f "$dmg_path" ]; then
        echo "  ERROR: DMG not found at ${dmg_path}"
        exit 1
    fi

    echo "  Built: ${dmg_path} ($(du -h "$dmg_path" | cut -f1))"
}

# ── Upload ──────────────────────────────────────────────────────────────────────

upload_app() {
    local id
    id=$(app_id "$1")
    local dmg_name
    dmg_name=$(app_dmg_name "$1")
    local module
    module=$(app_module "$1")
    local dmg_file="${dmg_name}-${VERSION}.dmg"
    local dmg_path="${module}/build/jpackage/${dmg_file}"
    local remote_dir="${SERVER_BASE}/${id}/releases/mac-os"

    echo "  Uploading ${dmg_file}..."
    scp "$dmg_path" "${SERVER}:${remote_dir}/${dmg_file}"

    # Generate and upload latest.json
    echo "  Updating latest.json..."
    local latest_json="{
  \"latestVersion\": \"${VERSION}\",
  \"downloadUrl\": \"${BASE_URL}/${id}/download/mac-os/${dmg_file}\",
  \"releaseNotes\": \"\",
  \"releaseDate\": \"${DATE}\"
}"
    echo "$latest_json" | ssh "$SERVER" "cat > ${SERVER_BASE}/${id}/latest.json"
}

# ── Main ────────────────────────────────────────────────────────────────────────

echo "Setting up server directories..."
for app in $APPS; do
    setup_server "$app"
done
echo

for app in $APPS; do
    dmg_name=$(app_dmg_name "$app")
    echo "───────────────────────────────────────────────────────────"
    echo "  ${dmg_name}"
    echo "───────────────────────────────────────────────────────────"
    build_app "$app"
    upload_app "$app"
    echo
done

echo "═══════════════════════════════════════════════════════════"
echo "  Done! Published v${VERSION}"
echo
for app in $APPS; do
    id=$(app_id "$app")
    dmg_name=$(app_dmg_name "$app")
    echo "  ${dmg_name}"
    echo "    Latest: ${BASE_URL}/${id}/latest.json"
    echo "    DMG:    ${BASE_URL}/${id}/download/mac-os/${dmg_name}-${VERSION}.dmg"
done
echo "═══════════════════════════════════════════════════════════"
