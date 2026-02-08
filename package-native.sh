#!/bin/bash
set -e

GRAALVM_HOME="$HOME/.graalvm/graalvm-jdk-21.0.5+9.1/Contents/Home"
BUILD_DIR="/Users/martinschwaller/Code/Tradery/tradery-forge/build"
APP_NAME="Strategy Forge"
APP_DIR="$BUILD_DIR/$APP_NAME.app"

echo "=== Creating macOS .app bundle ==="

# Clean previous
rm -rf "$APP_DIR"

# Create app structure
mkdir -p "$APP_DIR/Contents/MacOS"
mkdir -p "$APP_DIR/Contents/Resources"
mkdir -p "$APP_DIR/Contents/Frameworks"

# Copy native binary with different name
cp "$BUILD_DIR/tradery-native" "$APP_DIR/Contents/MacOS/tradery-bin"

# Copy required native libraries from GraalVM
echo "Copying AWT native libraries..."
cp "$GRAALVM_HOME/lib/libawt.dylib" "$APP_DIR/Contents/Frameworks/"
cp "$GRAALVM_HOME/lib/libawt_lwawt.dylib" "$APP_DIR/Contents/Frameworks/"
cp "$GRAALVM_HOME/lib/libosxapp.dylib" "$APP_DIR/Contents/Frameworks/"
cp "$GRAALVM_HOME/lib/libfontmanager.dylib" "$APP_DIR/Contents/Frameworks/"
cp "$GRAALVM_HOME/lib/libjawt.dylib" "$APP_DIR/Contents/Frameworks/" 2>/dev/null || true
cp "$GRAALVM_HOME/lib/libfreetype.dylib" "$APP_DIR/Contents/Frameworks/" 2>/dev/null || true
cp "$GRAALVM_HOME/lib/liblcms.dylib" "$APP_DIR/Contents/Frameworks/" 2>/dev/null || true
cp "$GRAALVM_HOME/lib/libjavajpeg.dylib" "$APP_DIR/Contents/Frameworks/" 2>/dev/null || true
cp "$GRAALVM_HOME/lib/libsplashscreen.dylib" "$APP_DIR/Contents/Frameworks/" 2>/dev/null || true

# Create launcher script that sets library path
cat > "$APP_DIR/Contents/MacOS/Strategy Forge" << 'LAUNCHER'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
export DYLD_LIBRARY_PATH="$DIR/../Frameworks:$DYLD_LIBRARY_PATH"
exec "$DIR/tradery-bin" "$@"
LAUNCHER
chmod +x "$APP_DIR/Contents/MacOS/Strategy Forge"

# Create Info.plist
cat > "$APP_DIR/Contents/Info.plist" << PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>Strategy Forge</string>
    <key>CFBundleDisplayName</key>
    <string>Strategy Forge</string>
    <key>CFBundleIdentifier</key>
    <string>com.tradery.forge</string>
    <key>CFBundleVersion</key>
    <string>1.0.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleExecutable</key>
    <string>Strategy Forge</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>LSMinimumSystemVersion</key>
    <string>11.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
</dict>
</plist>
PLIST

# Copy icon if exists
if [ -f "/Users/martinschwaller/Code/Tradery/tradery-forge/src/main/resources/icon.icns" ]; then
    cp "/Users/martinschwaller/Code/Tradery/tradery-forge/src/main/resources/icon.icns" "$APP_DIR/Contents/Resources/AppIcon.icns"
    # Add icon to plist
    sed -i '' 's|</dict>|    <key>CFBundleIconFile</key>\n    <string>AppIcon</string>\n</dict>|' "$APP_DIR/Contents/Info.plist"
fi

echo ""
echo "=== Done ==="
echo "App bundle: $APP_DIR"
du -sh "$APP_DIR"
echo ""
echo "To run: open $APP_DIR"
echo "Or:     $APP_DIR/Contents/MacOS/Strategy Forge"
