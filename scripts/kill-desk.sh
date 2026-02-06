#!/bin/bash
# Kill tradery-desk
pkill -f "tradery-desk" 2>/dev/null
pkill -f "DeskApp" 2>/dev/null
echo "Killed desk"
