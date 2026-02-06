#!/bin/bash
# Start tradery-forge (main UI app)
cd "$(dirname "$0")/.."
./gradlew :tradery-forge:run &
echo "Starting forge..."
