#!/bin/bash
# Kill tradery-forge (main UI app)
pkill -f "tradery-forge" 2>/dev/null
pkill -f "TraderyApp" 2>/dev/null
echo "Killed forge"
