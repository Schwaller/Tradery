#!/bin/bash
# Start tradery-trader
cd "$(dirname "$0")/.."
./gradlew :tradery-trader:run &
echo "Starting trader..."
