# Branding & Naming Convention

## Internal vs Public Naming

| Internal (Code) | Public (Product) | Description |
|-----------------|------------------|-------------|
| `tradery-*` | **Plaiiin** | Company/platform name |
| `tradery-forge` | **Strategy Forge** | Strategy creation, research & backtesting |
| `tradery-runner` | **Strategy Runner** | Live strategy execution |
| `tradery-desk` | **Trading Desk** | Manual trading interface |
| `tradery-data-service` | (internal) | Data fetching daemon (not user-facing) |
| `tradery-core` | (internal) | Shared models, DSL, indicators |
| `tradery-engine` | (internal) | Backtest engine |
| `tradery-data-client` | (internal) | Data service client library |

## Package Structure

All Java packages use the internal `com.tradery.*` namespace:

```
com.tradery.core        - Models, DSL, indicators
com.tradery.engine      - Backtest engine
com.tradery.dataclient  - Data service client
com.tradery.dataservice - Data service daemon
com.tradery.forge       - Strategy Forge app
com.tradery.desk        - Trading Desk app
```

## Build Artifacts

| Module | JAR Name | App Name |
|--------|----------|----------|
| tradery-forge | `tradery-forge-*.jar` | `Strategy Forge.app` |
| tradery-desk | `tradery-desk-*.jar` | `Trading Desk.app` |
| tradery-data-service | `tradery-data-service-*-all.jar` | (daemon, no app) |

## jpackage Configuration

In `build.gradle`, use public names for user-facing artifacts:

```groovy
jpackage {
    imageName = 'Strategy Forge'      // App bundle name
    installerName = 'Strategy Forge'  // DMG/installer name
    // ...
}
```

## File Locations

User data directory remains internal:
- `~/.tradery/` - User data, strategies, cached data
- `~/.tradery/dataservice.port` - Data service discovery
- `~/.tradery/api.port` - App API discovery

## Why This Convention?

1. **Code stability** - Internal names don't change with marketing
2. **Flexibility** - Public branding can evolve independently
3. **Consistency** - All code uses same `tradery` namespace
4. **Clarity** - Developers know internal names, users see polished branding
