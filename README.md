# Local Weather

NeoForge 1.21.1 mod that replaces global weather with **storm cells**: discs of rain that form upwind of players, drift with a wandering wind, and fade out. It can pour in one valley while the next one is dry. Meant as a companion to Better Clouds (optional; integration not written yet).

## How it works

- **Server** (`server/`): `WeatherSimulator` runs in place of vanilla's `advanceWeatherCycle`. Every second it moves and ages cells, spawns new ones upwind of players (weighted by biome downfall; deserts rarely spawn them) and syncs the cells near each player. Cells are saved per dimension in `WeatherSavedData`.
- **Gameplay hooks** (`mixin/`): `isRainingAt`, lightning, snow layers and cauldron filling check the cell over that column. Sleeping clears only the storms over the players. `/weather` spawns or clears cells.
- **Client** (`client/`): `ClientWeather` extrapolates synced cells between syncs and eases rain/thunder at the camera, which drives vanilla's rain, sky, fog and sound. F3 shows the local weather and the nearest cells.
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
./gradlew runClient
./gradlew runGameTestServer   # gametest/LocalWeatherTests
```

## Next

1. Better Clouds integration: feed local coverage and darkness into its cloud rendering.
2. Distant rain shafts under far-away cells, positional thunder.
3. Config, datapack biome weather profiles, season-mod hooks.
