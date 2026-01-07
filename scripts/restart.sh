#!/bin/bash
# Restart Tradery - kill any running instance and start fresh

set -e

PROJECT_DIR="/Users/martinschwaller/Code/Tradery"

echo "Stopping Tradery..."
pkill -f "GradleWorkerMain" 2>/dev/null || true
pkill -f "com.tradery" 2>/dev/null || true
sleep 1

echo "Starting Tradery..."
cd "$PROJECT_DIR" && ./gradlew run &

echo "Tradery starting in background (PID: $!)"
