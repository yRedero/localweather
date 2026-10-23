# Local Weather

NeoForge 1.21.1 mod that replaces global weather with **storm cells**: discs of rain that form upwind of players, drift with a wandering wind, and fade out. It can pour in one valley while the next one is dry. Built as a companion to [Better Clouds](https://modrinth.com/mod/better-clouds) (optional, client-side, 1.13+).

## How it works

- **Server** (`server/`): `WeatherSimulator` runs in place of vanilla's `advanceWeatherCycle`. Every second it moves and ages cells, spawns new ones upwind of players (weighted by biome downfall; deserts rarely spawn them) and syncs the cells near each player. Cells are saved per dimension in `WeatherSavedData`.
- **Gameplay hooks** (`mixin/`): `isRainingAt`, lightning, snow layers and cauldron filling check the cell over that column. Sleeping clears only the storms over the players. `/weather` spawns or clears cells.
- **Client** (`client/`): `ClientWeather` extrapolates synced cells between syncs and eases rain/thunder at the camera, which drives vanilla's rain, sky, fog and sound. F3 shows the local weather and the nearest cells.
- **Better Clouds** (`compat/`, `client/betterclouds/`): Better Clouds normally turns the rain level into one cloudiness value for the whole sky. The compat mixins snapshot the storm cells for each Better Clouds generator task and give every cloud sample its own cloudiness, so clouds pile up over storms and thin out over clear areas. Clouds are rebuilt when a storm has moved 32 blocks relative to the clouds or changed strength. These mixins only load when Better Clouds is installed (`BetterCloudsMixinPlugin`).
- Only overworld-like dimensions (sky light, no ceiling) are affected. The network channel is optional, so the mod falls back to vanilla weather when only one side has it.

Cell types: `shower` (small, short), `rain` (large fronts), `thunderstorm` (lightning in the core, warm biomes only).

## Commands (op level 2)

```
/localweather list                    cells in this dimension, nearest first
/localweather here                    rain and thunder at your position
/localweather spawn <type> [radius]   spawn a cell centred on you
/localweather clear                   remove all cells
```

## Develop

```
./gradlew build
./gradlew runClient           # with Better Clouds + YACL from Modrinth
./gradlew runClient -PquickPlay="New World"   # straight into a world
./gradlew runGameTestServer   # gametest/LocalWeatherTests
```

Before every client launch (Gradle or IDE), `installClientMods` copies Better Clouds and YACL from Modrinth into `run/mods`. Better Clouds is client-only and crashes servers, so the server and gametest runs use `run-server/` instead. Versions are in `gradle.properties`.

## Next

1. Make Better Clouds' cloud drift follow the storm wind, and darken clouds per storm (needs per-cloud shading data).
2. Distant rain shafts under far-away cells, positional thunder.
3. Config, datapack biome weather profiles, season-mod hooks.
