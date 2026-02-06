#!/bin/bash
# Start tradery-news (intel app)
cd "$(dirname "$0")/.."
./gradlew :tradery-news:run &
echo "Starting intel..."
