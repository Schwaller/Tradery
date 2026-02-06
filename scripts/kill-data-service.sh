#!/bin/bash
# Kill tradery-data-service
pkill -f "tradery-data-service" 2>/dev/null
pkill -f "DataServiceApp" 2>/dev/null
echo "Killed data-service"
