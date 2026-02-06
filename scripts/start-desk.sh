#!/bin/bash
# Start tradery-desk
cd "$(dirname "$0")/.."
./gradlew :tradery-desk:run &
echo "Starting desk..."
