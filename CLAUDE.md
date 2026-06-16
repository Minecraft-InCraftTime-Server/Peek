# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
mvn clean package
```

Output JAR: `target/peek-*.jar`. Deploy by copying to a Paper/Folia server's `plugins/` directory.

**Requirements:** JDK 17+ (CI uses 21), Maven 3.6+

There are no unit tests in this project. CI (`.github/workflows/build.yml`) runs `mvn clean package` on Ubuntu with JDK 21.

## What This Is

Peek is a Minecraft Paper/Folia plugin (Java 17, API 1.20+) that lets players observe other players' perspectives by switching to spectator mode. The plugin uses a fantasy/magic theme in its user-facing messages. All code is in the `ict.minesunshineone.peek` package.

## Architecture

**Entry point:** `PeekPlugin` extends `JavaPlugin` — initializes all managers/handlers, registers listeners and commands, validates config.

**Package layout:**

- `command/` — `PeekCommand` handles `/peek` (alias `/p`) with subcommands: `exit`, `stats`, `privacy`, `accept`, `deny`, `self`, `random`, `<player>`
- `handler/` — Core peek mechanics. `PeekStateHandler` orchestrates peek sessions and delegates to `BossBarHandler` (distance visualization), `RangeChecker` (distance monitoring with functional callbacks), and `PlayerStateRestorer` (gamemode/health/location recovery). `PeekTargetHandler` validates targets and checks privacy/cooldown.
- `manager/` — Business logic: `StateManager` (YAML file persistence for disconnect recovery), `PrivacyManager` (privacy mode via `PersistentDataContainer`, request lifecycle with timeouts), `CooldownManager` (concurrent cooldown tracking), `StatisticsManager` (peek counts/duration with async auto-save)
- `listener/` — Event handlers: `PeekListener` (join/quit/death), `PeekInteractionListener` (blocks container access during peek), `PeekPacketListener` (ProtocolLib packet interception to prevent spectate target switching)
- `data/` — `PeekData` immutable DTO storing peek session state (original location, gamemode, health, food, potions, target UUID)
- `util/` — `Messages` (i18n with language files in `lang/`), `PlayerStateUtil` (player state helpers)
- `placeholder/` — `PeekPlaceholderExpansion` for PlaceholderAPI integration

## Key Patterns

- **Folia thread safety:** Uses Paper's entity scheduler (`player.getScheduler().execute/run/runAtFixedRate`) throughout, not Bukkit scheduler. All location-aware operations run on the correct region thread.
- **Async teleport:** Uses `teleportAsync()` with `thenAccept()` callbacks, never blocking teleport.
- **Optional dependencies:** ProtocolLib and PlaceholderAPI are soft dependencies — the plugin detects their presence at runtime and gracefully degrades without them.
- **State recovery:** Player state is serialized to YAML files in `states/` on peek start. If a player disconnects mid-peek, state is restored on rejoin.
- **Concurrency:** `ConcurrentHashMap` in CooldownManager/StatisticsManager, `synchronized` blocks in PrivacyManager, copy-on-write iteration over `activePeeks`.

## Permissions

- `peek.use` — base command access (default: op)
- `peek.self` — self-peek in spectator mode (default: op)
- `peek.bypass` — bypass privacy mode, silent peek (default: op)
- `peek.nocooldown` — bypass cooldown (default: op)
- `peek.stats` — view statistics (default: op)

## Configuration

- `config.yml` — main config (limits, privacy, sounds, bossbar, statistics)
- `lang/zh_CN.yml`, `lang/en_US.yml` — localized messages
- `stats.yml` — persisted statistics
- `states/` directory — player state recovery files

Language defaults to `zh_CN`. Messages use `LegacyComponentSerializer.legacyAmpersand()` for color codes.
