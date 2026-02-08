#!/bin/bash
# Kill previous toolbar test window instances and restart
pkill -f "com.tradery.ui.ToolbarTestWindow" 2>/dev/null
sleep 0.5
cd "$(dirname "$0")/.."
./gradlew :tradery-ui-common:run &
