#!/bin/bash
set -e

export GRAALVM_HOME="$HOME/.graalvm/graalvm-jdk-21.0.5+9.1/Contents/Home"
export PATH="$GRAALVM_HOME/bin:$PATH"

cd /Users/martinschwaller/Code/Tradery/tradery-forge

# Step 1: Prepare JAR
echo "=== Step 1: Preparing JAR ==="
rm -rf build/native-prep
mkdir -p build/native-prep
cd build/native-prep
unzip -q ../libs/tradery-forge-1.0.0-all.jar
rm -rf META-INF/native-image/org.xerial
rm -rf META-INF/native-image/com.fasterxml.jackson*
rm -f ../tradery-native-prep.jar
zip -qr ../tradery-native-prep.jar .
cd ..
rm -rf native-prep
echo "Done"

# Step 2: Build native image with embedded java.library.path
echo ""
echo "=== Step 2: Building native image (5-10 min) ==="

# Set library path to a location we'll create in the app bundle
FRAMEWORKS_PATH='$ORIGIN/../Frameworks'

native-image \
  -jar tradery-native-prep.jar \
  -o tradery-native \
  -H:+UnlockExperimentalVMOptions \
  -H:ConfigurationFileDirectories=../src/main/resources/META-INF/native-image \
  --no-fallback \
  -H:+ReportExceptionStackTraces \
  --initialize-at-run-time=sun.awt,java.awt,javax.swing,sun.java2d,sun.font \
  --initialize-at-run-time=com.formdev.flatlaf \
  --initialize-at-run-time=org.sqlite \
  --initialize-at-run-time=com.sun.jna \
  --initialize-at-run-time=com.fasterxml.jackson \
  --initialize-at-run-time=org.slf4j \
  --initialize-at-run-time=org.apache.logging.log4j \
  --initialize-at-run-time=com.jediterm,com.pty4j \
  -H:+AddAllCharsets \
  -Djava.library.path="$GRAALVM_HOME/lib" \
  -H:CLibraryPath="$GRAALVM_HOME/lib" \
  -J-Xmx8g

echo ""
echo "=== Done ==="
ls -lh tradery-native
