#!/bin/bash
# Fetch screenshot from the toolbar test window's HTTP endpoint
# Saves to ~/.tradery/toolbar-test.png
PORT=$(cat ~/.tradery/toolbar-test.port 2>/dev/null)
if [ -z "$PORT" ]; then
    echo "ERROR: No port file found. Is the test window running?"
    exit 1
fi
curl -s "http://127.0.0.1:$PORT/screenshot" -o ~/.tradery/toolbar-test.png
echo "OK port=$PORT"
