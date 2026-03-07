#!/bin/bash
# Start tradery-guide (trading guide app)
cd "$(dirname "$0")/.."
./gradlew :tradery-guide:run &
echo "Starting guide..."
