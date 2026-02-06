#!/bin/bash
# Kill tradery-news (intel app)
pkill -f "tradery-news" 2>/dev/null
pkill -f "IntelFrame" 2>/dev/null
echo "Killed intel"
