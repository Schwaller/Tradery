#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { readFileSync, writeFileSync, existsSync, readdirSync, mkdirSync } from "fs";
import { homedir } from "os";
import { join } from "path";

const TRADERY_DIR = join(homedir(), ".tradery");

function getApiPort() {
  const portFile = join(TRADERY_DIR, "api.port");
  if (!existsSync(portFile)) {
    return { error: "Tradery is not running. Start the app first." };
  }
  try {
    const port = parseInt(readFileSync(portFile, "utf-8").trim(), 10);
    if (isNaN(port)) {
      return { error: "Invalid port file. Restart Tradery." };
    }
    return { port };
  } catch (e) {
    return { error: `Failed to read port file: ${e.message}` };
  }
}

async function apiCall(method, path, body = null) {
  const portResult = getApiPort();
  if (portResult.error) {
    return { error: portResult.error };
  }

  const url = `http://localhost:${portResult.port}${path}`;
  const options = {
    method,
    headers: { "Content-Type": "application/json" },
  };

  if (body) {
    options.body = JSON.stringify(body);
  }

  try {
    const response = await fetch(url, options);
    const text = await response.text();

    if (!response.ok) {
      return { error: `API error (${response.status}): ${text}` };
    }

    try {
      return JSON.parse(text);
    } catch {
      return { raw: text };
    }
  } catch (e) {
    if (e.code === 'ECONNREFUSED') {
      return { error: `Cannot connect to Tradery on port ${portResult.port}. Make sure the app is running.` };
    }
    return { error: `API call failed: ${e.message}` };
  }
}

// DSL syntax help for descriptions
const DSL_EXAMPLES = `
DSL Syntax Examples:
- Indicators: RSI(14), SMA(200), EMA(50), ATR(14), ADX(14)
- MACD: MACD(12,26,9).line, MACD(12,26,9).signal, MACD(12,26,9).histogram
- Bollinger: BBANDS(20,2).upper, BBANDS(20,2).lower, BBANDS(20,2).middle
- Stochastic: STOCHASTIC(14).k, STOCHASTIC(14).d
- Price: price, close, open, high, low, volume
- Range: HIGH_OF(20), LOW_OF(20), RANGE_POSITION(20)
- Operators: AND, OR, >, <, >=, <=, ==, crosses_above, crosses_below
- Time: HOUR, DAYOFWEEK (1=Mon), DAY, MONTH
- Example: RSI(14) < 30 AND price > SMA(200) AND ADX(14) > 25
`.trim();

// Define available tools with improved descriptions
const TOOLS = [
  {
    name: "tradery_list_strategies",
    description: "List all trading strategies with their IDs, names, symbols, and timeframes. Use this first to discover available strategies.",
    inputSchema: {
      type: "object",
      properties: {},
      required: [],
    },
  },
  {
    name: "tradery_get_strategy",
    description: "Get the full configuration of a strategy including entry/exit conditions, position sizing, and backtest settings. Returns the complete strategy JSON.",
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID (e.g., 'rsi-reversal', 'ema-crossover')",
        },
      },
      required: ["strategyId"],
    },
  },
  {
    name: "tradery_create_strategy",
    description: `Create a new trading strategy. Provide a unique ID and full strategy configuration.

${DSL_EXAMPLES}

Required fields: id, name, entrySettings.condition, backtestSettings (symbol, timeframe, duration)`,
    inputSchema: {
      type: "object",
      properties: {
        strategy: {
          type: "object",
          description: `Full strategy object. Example:
{
  "id": "my-rsi-strategy",
  "name": "My RSI Strategy",
  "entrySettings": {
    "condition": "RSI(14) < 30 AND price > SMA(200)",
    "maxOpenTrades": 1
  },
  "exitSettings": {
    "zones": [{
      "name": "Default",
      "stopLossType": "trailing_percent",
      "stopLossValue": 2.0,
      "takeProfitType": "fixed_percent",
      "takeProfitValue": 5.0
    }]
  },
  "backtestSettings": {
    "symbol": "BTCUSDT",
    "timeframe": "1h",
    "duration": "6m",
    "initialCapital": 10000,
    "positionSizingType": "fixed_percent",
    "positionSizingValue": 10
  }
}`,
        },
      },
      required: ["strategy"],
    },
  },
  {
    name: "tradery_validate_strategy",
    description: `Validate strategy updates WITHOUT saving. ALWAYS use this before tradery_update_strategy to catch DSL syntax errors and invalid settings.

${DSL_EXAMPLES}`,
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID to validate against",
        },
        updates: {
          type: "object",
          description: `Partial updates to validate. Example:
{
  "entrySettings": { "condition": "RSI(14) < 25" },
  "exitSettings": { "zones": [{ "stopLossValue": 1.5 }] }
}`,
        },
      },
      required: ["strategyId", "updates"],
    },
  },
  {
    name: "tradery_update_strategy",
    description: "Update a strategy configuration. Supports partial updates - only provide fields you want to change. IMPORTANT: Use tradery_validate_strategy first!",
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID to update",
        },
        updates: {
          type: "object",
          description: "Partial updates to apply (merged with existing config)",
        },
      },
      required: ["strategyId", "updates"],
    },
  },
  {
    name: "tradery_run_backtest",
    description: "Run a backtest for a strategy. Blocks until complete and returns performance metrics including win rate, profit factor, Sharpe ratio, max drawdown, and trade count.",
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID to backtest",
        },
      },
      required: ["strategyId"],
    },
  },
  {
    name: "tradery_delete_strategy",
    description: "Delete a strategy and all its backtest results. This cannot be undone.",
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID to delete",
        },
      },
      required: ["strategyId"],
    },
  },
  {
    name: "tradery_get_summary",
    description: "Get AI-friendly backtest summary with: metrics, win/loss analysis by phase/hour/day, improvement suggestions, and history trends comparing to previous runs. Also returns trade filenames for selective deep-dives.",
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID",
        },
      },
      required: ["strategyId"],
    },
  },
  {
    name: "tradery_get_trade",
    description: "Get detailed data for a specific trade including entry/exit prices, P&L, MFE/MAE (max favorable/adverse excursion), active phases, and indicator values at entry/exit. Use trade filenames from tradery_get_summary.",
    inputSchema: {
      type: "object",
      properties: {
        strategyId: {
          type: "string",
          description: "The strategy ID",
        },
        tradeFile: {
          type: "string",
          description: "Trade filename from tradeFiles array (e.g., '0001_WIN_+2p5pct_uptrend.json')",
        },
      },
      required: ["strategyId", "tradeFile"],
    },
  },
  {
    name: "tradery_list_phases",
    description: "List all available market phases for strategy filtering. Phases include: trend detection (uptrend, downtrend, ranging), sessions (asian, european, us-market), calendar (fomc, holidays), time (weekdays, specific days), funding rates, and moon phases.",
    inputSchema: {
      type: "object",
      properties: {},
      required: [],
    },
  },
  {
    name: "tradery_get_candles",
    description: "Get OHLCV candle data for a symbol/timeframe. Returns open, high, low, close, volume for each bar. Useful for custom analysis.",
    inputSchema: {
      type: "object",
      properties: {
        symbol: {
          type: "string",
          description: "Trading symbol (default: BTCUSDT)",
          default: "BTCUSDT",
        },
        timeframe: {
          type: "string",
          description: "Timeframe: 1m, 5m, 15m, 1h, 4h, 1d (default: 1h)",
          default: "1h",
        },
        bars: {
          type: "number",
          description: "Number of bars to return (default: 100, max: 1000)",
          default: 100,
        },
      },
      required: [],
    },
  },
  {
    name: "tradery_get_indicator",
    description: `Calculate indicator values for analysis. Automatically adds warmup bars for accurate calculation.

Supported indicators:
- RSI(period): Relative Strength Index, 0-100
- SMA(period), EMA(period): Moving averages
- ATR(period): Average True Range (volatility)
- ADX(period), PLUS_DI(period), MINUS_DI(period): Trend strength
- MACD(fast,slow,signal).line/signal/histogram
- BBANDS(period,stddev).upper/middle/lower
- STOCHASTIC(k,d).k/.d
- RANGE_POSITION(period): Position within range (-1 to +1)`,
    inputSchema: {
      type: "object",
      properties: {
        name: {
          type: "string",
          description: "Indicator (e.g., 'RSI(14)', 'SMA(200)', 'MACD(12,26,9).histogram')",
        },
        symbol: {
          type: "string",
          description: "Trading symbol (default: BTCUSDT)",
          default: "BTCUSDT",
        },
        timeframe: {
          type: "string",
          description: "Timeframe (default: 1h)",
          default: "1h",
        },
        bars: {
          type: "number",
          description: "Number of result bars (default: 50). Extra warmup bars added automatically.",
          default: 50,
        },
      },
      required: ["name"],
    },
  },
  {
    name: "tradery_eval_condition",
    description: `Find bars where a DSL condition evaluates to true. Great for testing entry/exit conditions before adding to strategy.

${DSL_EXAMPLES}`,
    inputSchema: {
      type: "object",
      properties: {
        condition: {
          type: "string",
          description: "DSL condition (e.g., 'RSI(14) < 30 AND price > SMA(200)')",
        },
        symbol: {
          type: "string",
          description: "Trading symbol (default: BTCUSDT)",
          default: "BTCUSDT",
        },
        timeframe: {
          type: "string",
          description: "Timeframe (default: 1h)",
          default: "1h",
        },
        bars: {
          type: "number",
          description: "Number of bars to scan (default: 500)",
          default: 500,
        },
      },
      required: ["condition"],
    },
  },

  // ========== Phase Tools ==========
  {
    name: "tradery_get_phase",
    description: "Get details of a specific market phase including its DSL condition, timeframe, and category.",
    inputSchema: {
      type: "object",
      properties: {
        phaseId: {
          type: "string",
          description: "Phase ID (e.g., 'uptrend', 'us-market-hours', 'fomc-meeting-day')",
        },
      },
      required: ["phaseId"],
    },
  },
  {
    name: "tradery_create_phase",
    description: `Create a custom market phase for filtering strategy entries.

${DSL_EXAMPLES}

Categories: Trend, Session, Time, Calendar, Technical, Funding, Moon, Custom`,
    inputSchema: {
      type: "object",
      properties: {
        phase: {
          type: "object",
          description: `Phase object. Example:
{
  "id": "my-trend-filter",
  "name": "My Trend Filter",
  "description": "Custom trend detection",
  "category": "Trend",
  "condition": "ADX(14) > 30 AND PLUS_DI(14) > MINUS_DI(14)",
  "timeframe": "4h",
  "symbol": "BTCUSDT"
}`,
        },
      },
      required: ["phase"],
    },
  },
  {
    name: "tradery_update_phase",
    description: "Update a custom phase. Built-in phases cannot be modified.",
    inputSchema: {
      type: "object",
      properties: {
        phaseId: {
          type: "string",
          description: "Phase ID to update",
        },
        updates: {
          type: "object",
          description: "Partial updates (e.g., { condition: 'ADX(14) > 35' })",
        },
      },
      required: ["phaseId", "updates"],
    },
  },
  {
    name: "tradery_delete_phase",
    description: "Delete a custom phase. Built-in phases cannot be deleted.",
    inputSchema: {
      type: "object",
      properties: {
        phaseId: {
          type: "string",
          description: "Phase ID to delete",
        },
      },
      required: ["phaseId"],
    },
  },

  // ========== Hoop Pattern Tools ==========
  {
    name: "tradery_list_hoops",
    description: "List all hoop patterns. Hoops are sequential price checkpoints for detecting chart patterns like double bottoms, head & shoulders, etc.",
    inputSchema: {
      type: "object",
      properties: {},
      required: [],
    },
  },
  {
    name: "tradery_get_hoop",
    description: "Get details of a specific hoop pattern including all checkpoint definitions.",
    inputSchema: {
      type: "object",
      properties: {
        hoopId: {
          type: "string",
          description: "Hoop pattern ID",
        },
      },
      required: ["hoopId"],
    },
  },
  {
    name: "tradery_create_hoop",
    description: `Create a hoop pattern for detecting price formations. Each hoop is a price checkpoint with a target range and timing.

Hoop fields:
- name: Checkpoint name (e.g., 'first-low', 'breakout')
- minPricePercent/maxPricePercent: Price range relative to anchor (-5.0 to +5.0)
- distance: Expected bars from previous hoop
- tolerance: Allowed variance in bars
- anchorMode: 'actual_hit' or 'expected_position'`,
    inputSchema: {
      type: "object",
      properties: {
        hoop: {
          type: "object",
          description: `Hoop pattern object. Example:
{
  "id": "double-bottom",
  "name": "Double Bottom",
  "description": "Classic reversal pattern",
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "hoops": [
    { "name": "first-low", "minPricePercent": -3.0, "maxPricePercent": -1.0, "distance": 5, "tolerance": 2, "anchorMode": "actual_hit" },
    { "name": "middle-peak", "minPricePercent": 1.0, "maxPricePercent": 4.0, "distance": 7, "tolerance": 3, "anchorMode": "actual_hit" },
    { "name": "second-low", "minPricePercent": -3.0, "maxPricePercent": 0.5, "distance": 7, "tolerance": 3, "anchorMode": "actual_hit" },
    { "name": "breakout", "minPricePercent": 2.0, "maxPricePercent": null, "distance": 5, "tolerance": 3, "anchorMode": "actual_hit" }
  ],
  "cooldownBars": 20,
  "allowOverlap": false
}`,
        },
      },
      required: ["hoop"],
    },
  },
  {
    name: "tradery_update_hoop",
    description: "Update an existing hoop pattern.",
    inputSchema: {
      type: "object",
      properties: {
        hoopId: {
          type: "string",
          description: "Hoop pattern ID to update",
        },
        updates: {
          type: "object",
          description: "Partial updates to apply",
        },
      },
      required: ["hoopId", "updates"],
    },
  },
  {
    name: "tradery_delete_hoop",
    description: "Delete a hoop pattern.",
    inputSchema: {
      type: "object",
      properties: {
        hoopId: {
          type: "string",
          description: "Hoop pattern ID to delete",
        },
      },
      required: ["hoopId"],
    },
  },
];

// Tool handlers
async function handleTool(name, args) {
  switch (name) {
    case "tradery_list_strategies":
      return apiCall("GET", "/strategies");

    case "tradery_get_strategy": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }
      return apiCall("GET", `/strategy/${encodeURIComponent(args.strategyId)}`);
    }

    case "tradery_create_strategy": {
      if (!args.strategy) {
        return { error: "Missing required parameter: strategy object" };
      }
      const strategy = args.strategy;

      // Validate required fields
      if (!strategy.id) {
        return { error: "Strategy must have an 'id' field (e.g., 'my-rsi-strategy')" };
      }
      if (!strategy.name) {
        return { error: "Strategy must have a 'name' field" };
      }
      if (!strategy.entrySettings?.condition) {
        return { error: "Strategy must have entrySettings.condition (DSL entry condition)" };
      }
      if (!strategy.backtestSettings?.symbol) {
        return { error: "Strategy must have backtestSettings.symbol (e.g., 'BTCUSDT')" };
      }

      // Check if strategy already exists
      const strategyDir = join(TRADERY_DIR, "strategies", strategy.id);
      const strategyPath = join(strategyDir, "strategy.json");
      if (existsSync(strategyPath)) {
        return { error: `Strategy '${strategy.id}' already exists. Use tradery_update_strategy to modify it.` };
      }

      // Set defaults
      const fullStrategy = {
        id: strategy.id,
        name: strategy.name,
        description: strategy.description || "",
        enabled: true,
        entrySettings: {
          condition: strategy.entrySettings.condition,
          maxOpenTrades: strategy.entrySettings?.maxOpenTrades || 1,
          minCandlesBetween: strategy.entrySettings?.minCandlesBetween || 0,
          dca: strategy.entrySettings?.dca || { enabled: false, maxEntries: 3, barsBetween: 1 },
        },
        exitSettings: strategy.exitSettings || {
          zones: [{
            name: "Default",
            stopLossType: "trailing_percent",
            stopLossValue: 2.0,
            takeProfitType: "none",
            takeProfitValue: null,
          }],
          evaluation: "candle_close",
        },
        backtestSettings: {
          symbol: strategy.backtestSettings.symbol,
          timeframe: strategy.backtestSettings?.timeframe || "1h",
          duration: strategy.backtestSettings?.duration || "6m",
          initialCapital: strategy.backtestSettings?.initialCapital || 10000,
          positionSizingType: strategy.backtestSettings?.positionSizingType || "fixed_percent",
          positionSizingValue: strategy.backtestSettings?.positionSizingValue || 10,
          feePercent: strategy.backtestSettings?.feePercent || 0.1,
          slippagePercent: strategy.backtestSettings?.slippagePercent || 0.05,
        },
        phaseSettings: strategy.phaseSettings || { requiredPhaseIds: [], excludedPhaseIds: [] },
        orderflowSettings: strategy.orderflowSettings || { mode: "disabled" },
      };

      // Create directory and write file
      try {
        mkdirSync(strategyDir, { recursive: true });
        writeFileSync(strategyPath, JSON.stringify(fullStrategy, null, 2));
        return {
          success: true,
          message: `Strategy '${strategy.id}' created successfully. Use tradery_run_backtest to test it.`,
          strategyId: strategy.id,
        };
      } catch (e) {
        return { error: `Failed to create strategy: ${e.message}` };
      }
    }

    case "tradery_validate_strategy": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }
      if (!args.updates) {
        return { error: "Missing required parameter: updates object" };
      }
      return apiCall("POST", `/strategy/${encodeURIComponent(args.strategyId)}/validate`, args.updates);
    }

    case "tradery_update_strategy": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }
      if (!args.updates) {
        return { error: "Missing required parameter: updates object" };
      }
      return apiCall("POST", `/strategy/${encodeURIComponent(args.strategyId)}`, args.updates);
    }

    case "tradery_run_backtest": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }
      return apiCall("POST", `/strategy/${encodeURIComponent(args.strategyId)}/backtest`);
    }

    case "tradery_delete_strategy": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }

      const strategyDir = join(TRADERY_DIR, "strategies", args.strategyId);
      if (!existsSync(strategyDir)) {
        return { error: `Strategy '${args.strategyId}' not found.` };
      }

      try {
        const { rmSync } = await import("fs");
        rmSync(strategyDir, { recursive: true });
        return { success: true, message: `Strategy '${args.strategyId}' and all results deleted.` };
      } catch (e) {
        return { error: `Failed to delete strategy: ${e.message}` };
      }
    }

    case "tradery_get_summary": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }
      const strategyDir = join(TRADERY_DIR, "strategies", args.strategyId);
      const summaryPath = join(strategyDir, "summary.json");

      if (!existsSync(strategyDir)) {
        return { error: `Strategy '${args.strategyId}' not found. Use tradery_list_strategies to see available strategies.` };
      }
      if (!existsSync(summaryPath)) {
        return { error: `No backtest results for '${args.strategyId}'. Run tradery_run_backtest first.` };
      }

      try {
        const summary = JSON.parse(readFileSync(summaryPath, "utf-8"));

        // Remove large objects, keep metrics and analysis
        delete summary.trades;
        delete summary.strategy;
        delete summary.config;

        // List trade files
        const tradesDir = join(strategyDir, "trades");
        if (existsSync(tradesDir)) {
          summary.tradeFiles = readdirSync(tradesDir)
            .filter(f => f.endsWith(".json"))
            .sort();
        } else {
          summary.tradeFiles = [];
        }

        return summary;
      } catch (e) {
        return { error: `Failed to read summary: ${e.message}` };
      }
    }

    case "tradery_get_trade": {
      if (!args.strategyId) {
        return { error: "Missing required parameter: strategyId" };
      }
      if (!args.tradeFile) {
        return { error: "Missing required parameter: tradeFile (get from tradery_get_summary tradeFiles)" };
      }

      const tradePath = join(TRADERY_DIR, "strategies", args.strategyId, "trades", args.tradeFile);

      if (!existsSync(tradePath)) {
        return { error: `Trade file not found: ${args.tradeFile}. Check tradeFiles from tradery_get_summary.` };
      }

      try {
        return JSON.parse(readFileSync(tradePath, "utf-8"));
      } catch (e) {
        return { error: `Failed to read trade: ${e.message}` };
      }
    }

    case "tradery_list_phases":
      return apiCall("GET", "/phases");

    case "tradery_get_candles": {
      const symbol = args.symbol || "BTCUSDT";
      const timeframe = args.timeframe || "1h";
      const bars = Math.min(args.bars || 100, 1000);
      return apiCall("GET", `/candles?symbol=${encodeURIComponent(symbol)}&timeframe=${encodeURIComponent(timeframe)}&bars=${bars}`);
    }

    case "tradery_get_indicator": {
      if (!args.name) {
        return { error: "Missing required parameter: name (e.g., 'RSI(14)')" };
      }
      const symbol = args.symbol || "BTCUSDT";
      const timeframe = args.timeframe || "1h";
      const requestedBars = args.bars || 50;

      // Add warmup bars for indicator calculation (200 extra should cover most indicators)
      const warmupBars = 200;
      const totalBars = requestedBars + warmupBars;

      const result = await apiCall(
        "GET",
        `/indicator?name=${encodeURIComponent(args.name)}&symbol=${encodeURIComponent(symbol)}&timeframe=${encodeURIComponent(timeframe)}&bars=${totalBars}`
      );

      // If successful, trim to requested bars (from end)
      if (result.values && Array.isArray(result.values) && result.values.length > requestedBars) {
        result.values = result.values.slice(-requestedBars);
        result.count = result.values.length;
        result.note = `Showing last ${requestedBars} bars (${warmupBars} warmup bars used for calculation)`;
      }

      return result;
    }

    case "tradery_eval_condition": {
      if (!args.condition) {
        return { error: "Missing required parameter: condition (DSL expression)" };
      }
      const symbol = args.symbol || "BTCUSDT";
      const timeframe = args.timeframe || "1h";
      const bars = args.bars || 500;
      return apiCall(
        "GET",
        `/eval?condition=${encodeURIComponent(args.condition)}&symbol=${encodeURIComponent(symbol)}&timeframe=${encodeURIComponent(timeframe)}&bars=${bars}`
      );
    }

    // ========== Phase Handlers ==========

    case "tradery_get_phase": {
      if (!args.phaseId) {
        return { error: "Missing required parameter: phaseId" };
      }
      return apiCall("GET", `/phase/${encodeURIComponent(args.phaseId)}`);
    }

    case "tradery_create_phase": {
      if (!args.phase) {
        return { error: "Missing required parameter: phase object" };
      }
      const phase = args.phase;

      if (!phase.id) {
        return { error: "Phase must have an 'id' field" };
      }
      if (!phase.name) {
        return { error: "Phase must have a 'name' field" };
      }
      if (!phase.condition) {
        return { error: "Phase must have a 'condition' field (DSL expression)" };
      }

      const phaseDir = join(TRADERY_DIR, "phases", phase.id);
      const phasePath = join(phaseDir, "phase.json");

      if (existsSync(phasePath)) {
        return { error: `Phase '${phase.id}' already exists. Use tradery_update_phase to modify it.` };
      }

      const fullPhase = {
        id: phase.id,
        name: phase.name,
        description: phase.description || "",
        category: phase.category || "Custom",
        condition: phase.condition,
        timeframe: phase.timeframe || "1h",
        symbol: phase.symbol || "BTCUSDT",
        builtIn: false,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
      };

      try {
        mkdirSync(phaseDir, { recursive: true });
        writeFileSync(phasePath, JSON.stringify(fullPhase, null, 2));
        return {
          success: true,
          message: `Phase '${phase.id}' created. Add it to strategy's phaseSettings.requiredPhaseIds to use.`,
          phaseId: phase.id,
        };
      } catch (e) {
        return { error: `Failed to create phase: ${e.message}` };
      }
    }

    case "tradery_update_phase": {
      if (!args.phaseId) {
        return { error: "Missing required parameter: phaseId" };
      }
      if (!args.updates) {
        return { error: "Missing required parameter: updates object" };
      }

      const phaseDir = join(TRADERY_DIR, "phases", args.phaseId);
      const phasePath = join(phaseDir, "phase.json");

      if (!existsSync(phasePath)) {
        return { error: `Phase '${args.phaseId}' not found.` };
      }

      try {
        const existing = JSON.parse(readFileSync(phasePath, "utf-8"));

        if (existing.builtIn) {
          return { error: `Cannot modify built-in phase '${args.phaseId}'. Create a custom phase instead.` };
        }

        const updated = { ...existing, ...args.updates, updated: new Date().toISOString() };
        // Preserve immutable fields
        updated.id = existing.id;
        updated.builtIn = false;
        updated.created = existing.created;

        writeFileSync(phasePath, JSON.stringify(updated, null, 2));
        return { success: true, message: `Phase '${args.phaseId}' updated.`, phase: updated };
      } catch (e) {
        return { error: `Failed to update phase: ${e.message}` };
      }
    }

    case "tradery_delete_phase": {
      if (!args.phaseId) {
        return { error: "Missing required parameter: phaseId" };
      }

      const phaseDir = join(TRADERY_DIR, "phases", args.phaseId);
      const phasePath = join(phaseDir, "phase.json");

      if (!existsSync(phasePath)) {
        return { error: `Phase '${args.phaseId}' not found.` };
      }

      try {
        const existing = JSON.parse(readFileSync(phasePath, "utf-8"));

        if (existing.builtIn) {
          return { error: `Cannot delete built-in phase '${args.phaseId}'.` };
        }

        // Remove the directory
        const { rmSync } = await import("fs");
        rmSync(phaseDir, { recursive: true });
        return { success: true, message: `Phase '${args.phaseId}' deleted.` };
      } catch (e) {
        return { error: `Failed to delete phase: ${e.message}` };
      }
    }

    // ========== Hoop Pattern Handlers ==========

    case "tradery_list_hoops": {
      const hoopsDir = join(TRADERY_DIR, "hoops");
      if (!existsSync(hoopsDir)) {
        return { hoops: [] };
      }

      try {
        const dirs = readdirSync(hoopsDir, { withFileTypes: true })
          .filter(d => d.isDirectory())
          .map(d => d.name);

        const hoops = [];
        for (const dir of dirs) {
          const hoopPath = join(hoopsDir, dir, "hoop.json");
          if (existsSync(hoopPath)) {
            try {
              const hoop = JSON.parse(readFileSync(hoopPath, "utf-8"));
              hoops.push({
                id: hoop.id,
                name: hoop.name,
                description: hoop.description,
                symbol: hoop.symbol,
                timeframe: hoop.timeframe,
                hoopCount: hoop.hoops?.length || 0,
              });
            } catch {}
          }
        }
        return { hoops };
      } catch (e) {
        return { error: `Failed to list hoops: ${e.message}` };
      }
    }

    case "tradery_get_hoop": {
      if (!args.hoopId) {
        return { error: "Missing required parameter: hoopId" };
      }

      const hoopPath = join(TRADERY_DIR, "hoops", args.hoopId, "hoop.json");
      if (!existsSync(hoopPath)) {
        return { error: `Hoop pattern '${args.hoopId}' not found.` };
      }

      try {
        return JSON.parse(readFileSync(hoopPath, "utf-8"));
      } catch (e) {
        return { error: `Failed to read hoop: ${e.message}` };
      }
    }

    case "tradery_create_hoop": {
      if (!args.hoop) {
        return { error: "Missing required parameter: hoop object" };
      }
      const hoop = args.hoop;

      if (!hoop.id) {
        return { error: "Hoop must have an 'id' field" };
      }
      if (!hoop.name) {
        return { error: "Hoop must have a 'name' field" };
      }
      if (!hoop.hoops || !Array.isArray(hoop.hoops) || hoop.hoops.length === 0) {
        return { error: "Hoop must have a 'hoops' array with at least one checkpoint" };
      }

      const hoopDir = join(TRADERY_DIR, "hoops", hoop.id);
      const hoopPath = join(hoopDir, "hoop.json");

      if (existsSync(hoopPath)) {
        return { error: `Hoop '${hoop.id}' already exists. Use tradery_update_hoop to modify it.` };
      }

      // Calculate pattern bars
      let totalBars = 0;
      let minBars = 0;
      let maxBars = 0;
      for (const h of hoop.hoops) {
        totalBars += h.distance || 0;
        minBars += (h.distance || 0) - (h.tolerance || 0);
        maxBars += (h.distance || 0) + (h.tolerance || 0);
      }

      const fullHoop = {
        id: hoop.id,
        name: hoop.name,
        description: hoop.description || null,
        hoops: hoop.hoops.map(h => ({
          name: h.name || "checkpoint",
          minPricePercent: h.minPricePercent ?? -2.0,
          maxPricePercent: h.maxPricePercent ?? 2.0,
          distance: h.distance || 5,
          tolerance: h.tolerance || 2,
          anchorMode: h.anchorMode || "actual_hit",
        })),
        symbol: hoop.symbol || "BTCUSDT",
        timeframe: hoop.timeframe || "1h",
        cooldownBars: hoop.cooldownBars || 0,
        allowOverlap: hoop.allowOverlap || false,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        totalExpectedBars: totalBars,
        minPatternBars: Math.max(0, minBars),
        maxPatternBars: maxBars,
      };

      try {
        mkdirSync(hoopDir, { recursive: true });
        writeFileSync(hoopPath, JSON.stringify(fullHoop, null, 2));
        return {
          success: true,
          message: `Hoop pattern '${hoop.id}' created with ${fullHoop.hoops.length} checkpoints.`,
          hoopId: hoop.id,
        };
      } catch (e) {
        return { error: `Failed to create hoop: ${e.message}` };
      }
    }

    case "tradery_update_hoop": {
      if (!args.hoopId) {
        return { error: "Missing required parameter: hoopId" };
      }
      if (!args.updates) {
        return { error: "Missing required parameter: updates object" };
      }

      const hoopDir = join(TRADERY_DIR, "hoops", args.hoopId);
      const hoopPath = join(hoopDir, "hoop.json");

      if (!existsSync(hoopPath)) {
        return { error: `Hoop pattern '${args.hoopId}' not found.` };
      }

      try {
        const existing = JSON.parse(readFileSync(hoopPath, "utf-8"));
        const updated = { ...existing, ...args.updates, updated: new Date().toISOString() };

        // Preserve immutable fields
        updated.id = existing.id;
        updated.created = existing.created;

        // Recalculate pattern bars if hoops changed
        if (updated.hoops && Array.isArray(updated.hoops)) {
          let totalBars = 0, minBars = 0, maxBars = 0;
          for (const h of updated.hoops) {
            totalBars += h.distance || 0;
            minBars += (h.distance || 0) - (h.tolerance || 0);
            maxBars += (h.distance || 0) + (h.tolerance || 0);
          }
          updated.totalExpectedBars = totalBars;
          updated.minPatternBars = Math.max(0, minBars);
          updated.maxPatternBars = maxBars;
        }

        writeFileSync(hoopPath, JSON.stringify(updated, null, 2));
        return { success: true, message: `Hoop pattern '${args.hoopId}' updated.`, hoop: updated };
      } catch (e) {
        return { error: `Failed to update hoop: ${e.message}` };
      }
    }

    case "tradery_delete_hoop": {
      if (!args.hoopId) {
        return { error: "Missing required parameter: hoopId" };
      }

      const hoopDir = join(TRADERY_DIR, "hoops", args.hoopId);
      if (!existsSync(hoopDir)) {
        return { error: `Hoop pattern '${args.hoopId}' not found.` };
      }

      try {
        const { rmSync } = await import("fs");
        rmSync(hoopDir, { recursive: true });
        return { success: true, message: `Hoop pattern '${args.hoopId}' deleted.` };
      } catch (e) {
        return { error: `Failed to delete hoop: ${e.message}` };
      }
    }

    default:
      return { error: `Unknown tool: ${name}. Available tools: ${TOOLS.map(t => t.name).join(', ')}` };
  }
}

// Create and run server
const server = new Server(
  {
    name: "tradery-mcp-server",
    version: "1.2.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// Handle tool listing
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return { tools: TOOLS };
});

// Handle tool calls
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    const result = await handleTool(name, args || {});
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(result, null, 2),
        },
      ],
    };
  } catch (error) {
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({
            error: error.message,
            hint: "Check if Tradery is running and try again."
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
});

// Start server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Tradery MCP server v1.2.0 running");
}

main().catch(console.error);
