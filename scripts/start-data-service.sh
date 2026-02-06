#!/bin/bash
# Start tradery-data-service
cd "$(dirname "$0")/.."
./gradlew :tradery-data-service:run &
echo "Starting data-service..."
