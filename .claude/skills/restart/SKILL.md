---
name: restart
description: Restart the Tradery application. Use when asked to restart, relaunch, or reboot the app, or after making code changes that need testing.
---

# Restart Tradery

Kill any running Tradery instance and start fresh.

## Modules

The project has multiple runnable modules. Unless the user specifies otherwise, restart **tradery-forge** (the main UI app).

| Module | Gradle Task | Description |
|--------|-------------|-------------|
| **tradery-forge** | `./gradlew :tradery-forge:run` | Main UI app (default) |
| **tradery-data-service** | `./gradlew :tradery-data-service:run` | Background data service |
| **tradery-desk** | `./gradlew :tradery-desk:run` | Real-time signal desk |
| **tradery-runner** | `./gradlew :tradery-runner:run` | Strategy runner |

**IMPORTANT:** Do NOT use bare `./gradlew run` — it runs the data service, not the main app.

## Instructions

1. Kill existing processes:
   ```bash
   pkill -f "GradleWorkerMain" 2>/dev/null || true
   pkill -f "com.tradery" 2>/dev/null || true
   sleep 1
   ```

2. Start **both** the data service and forge in background (data service first, forge depends on it):
   ```bash
   cd /Users/martinschwaller/Code/Tradery && ./gradlew :tradery-data-service:run &
   sleep 3
   cd /Users/martinschwaller/Code/Tradery && ./gradlew :tradery-forge:run &
   ```

## Data Service Log

The data service logs to `~/.tradery/logs/dataservice.log` and exposes a `/logs` endpoint:
```
curl -s "http://localhost:$(cat ~/.tradery/dataservice.port)/logs?lines=50"
```

## Notes

- Always run in background (`&`) so Claude Code isn't blocked
- The `|| true` ensures no failure if nothing is running
- Start data service before forge — forge connects to it for aggTrades, funding, etc.
- App window appears within a few seconds
- Strategies auto-reload from `~/.tradery/strategies/` on file changes
