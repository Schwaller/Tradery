#!/bin/bash
# Kill tradery-guide (trading guide app)
pkill -f "tradery-guide" 2>/dev/null
pkill -f "TradingGuideApp" 2>/dev/null
echo "Killed guide"
