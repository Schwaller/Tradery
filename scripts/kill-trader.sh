#!/bin/bash
# Kill tradery-trader
pkill -f "tradery-trader" 2>/dev/null
pkill -f "TraderApp" 2>/dev/null
echo "Killed trader"
