#!/bin/bash
set -e

# Build fat jar, package with Dockerfile, SCP to server, build image there.
# No Docker Hub needed.

cd "$(dirname "$0")"
SCRIPT_DIR="$(pwd)"
SERVER="root@plaiiin.com"
REMOTE_DIR="/plaiiin-page/rendezvous"

echo "Building fat jar..."
cd ..
./gradlew :tradery-rendezvous:fatJar

cd "$SCRIPT_DIR"
JAR=$(ls build/libs/tradery-rendezvous-*-all.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: fat jar not found in build/libs/"
    exit 1
fi

echo "Packaging..."
TMPDIR=$(mktemp -d)
cp "$JAR" "$TMPDIR/rendezvous.jar"
cp Dockerfile "$TMPDIR/"
(cd "$TMPDIR" && tar czf rendezvous-deploy.tar.gz rendezvous.jar Dockerfile)

echo "Uploading to $SERVER..."
ssh "$SERVER" "mkdir -p $REMOTE_DIR"
scp "$TMPDIR/rendezvous-deploy.tar.gz" "$SERVER:$REMOTE_DIR/"

echo "Building image on server..."
ssh "$SERVER" "cd $REMOTE_DIR && tar xzf rendezvous-deploy.tar.gz && docker build -t plaiiin/private:plaiiin-rendezvous . && rm -f rendezvous-deploy.tar.gz"

echo "Restarting service..."
ssh "$SERVER" "cd /plaiiin-page && docker compose up -d rendezvous"

rm -rf "$TMPDIR"

echo "Done. Check:"
echo "  curl https://plaiiin.com/rendezvous/health"
