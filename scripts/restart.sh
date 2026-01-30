#!/bin/bash
# Restart Tradery - kill any running instance and start fresh
#
# Usage:
#   ./scripts/restart.sh              # Start tradery-forge (default)
#   ./scripts/restart.sh forge        # Start tradery-forge
#   ./scripts/restart.sh data-service # Start tradery-data-service
#   ./scripts/restart.sh desk         # Start tradery-desk
#   ./scripts/restart.sh runner       # Start tradery-runner

set -e

PROJECT_DIR="/Users/martinschwaller/Code/Tradery"
MODULE="${1:-forge}"

echo "Stopping Tradery..."
pkill -f "GradleWorkerMain" 2>/dev/null || true
pkill -f "com.tradery" 2>/dev/null || true
sleep 1

echo "Starting tradery-${MODULE}..."
cd "$PROJECT_DIR" && ./gradlew ":tradery-${MODULE}:run" &

echo "tradery-${MODULE} starting in background (PID: $!)"
