# Local Weather

NeoForge 1.21.1 mod that replaces global weather with **storm cells**: discs of rain that form upwind of players, drift with a wandering wind, and fade out. It can pour in one valley while the next one is dry. Built as a companion to [Better Clouds](https://modrinth.com/mod/better-clouds) (optional, client-side, 1.13+).

## How it works

- **Server** (`server/`): `WeatherSimulator` runs in place of vanilla's `advanceWeatherCycle`. Every second it moves and ages cells, spawns new ones upwind of players (weighted by biome downfall; deserts rarely spawn them) and syncs the cells near each player. Cells are saved per dimension in `WeatherSavedData`.
- **Gameplay hooks** (`mixin/`): `isRainingAt`, lightning, snow layers and cauldron filling check the cell over that column. Sleeping clears only the storms over the players. `/weather` spawns or clears cells.
- **Client** (`client/`): `ClientWeather` extrapolates synced cells between syncs and eases rain/thunder at the camera, which drives vanilla's rain, sky, fog and sound. F3 shows the local weather and the nearest cells.
- **Better Clouds** (`compat/`, `client/betterclouds/`), all optional and tunable in the mod's config screen:
  - *Local coverage*: every cloud sample gets its own cloudiness from the storm cells instead of one value for the whole sky.
  - *Far clouds*: draws clouds out to 2560 blocks (Better Clouds stops at 4x render distance). Beyond `detailDistance` (1536) only every 2nd cloud is generated at twice the size, beyond twice that every 4th at four times; the boundary is dithered so there's no visible ring. Clouds write their own depth with a 16 km range so far clouds sort correctly instead of flickering through each other.
  - *Storm look*: storms are built from normal cloud puffs, each nudged sideways so no faces line up. Thunderstorms grow puff columns up to ~240 blocks out of separate convective cells, with a flat anvil of puffs above; rain adds one puff and lowers the deck; storm clouds are darker and denser. Storm outlines are bent by noise so they aren't circles.
  - *High layer*: a second, flat cloud layer above the normal one that thickens around storms.
  - How: the generator task's sampler and point creation are wrapped (`ChunkedGeneratorTaskMixin`, logic in `CloudGenContext`). Each cloud carries a size, darkness and flat flag packed into its y coordinate (`CloudCode`), which a text patch to Better Clouds' coverage and shading shaders decodes (`BetterCloudsShaderPatch`; if Better Clouds changes those shaders, the patch backs off and only coverage stays). Chunk frustum culling is skipped for far clouds because vanilla's frustum ends at the far plane.
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
./gradlew runClient -PquickPlay="New World" -PdevScene   # storms around you, screenshots to run/screenshots, then quits
./gradlew runGameTestServer   # gametest/LocalWeatherTests
```

Before every client launch (Gradle or IDE), `installClientMods` copies Better Clouds and YACL from Modrinth into `run/mods`. Better Clouds is client-only and crashes servers, so the server and gametest runs use `run-server/` instead. Versions are in `gradle.properties`.

## Next

1. Make Better Clouds' cloud drift follow the storm wind.
2. Distant rain shafts under far-away cells, positional thunder.
3. Config, datapack biome weather profiles, season-mod hooks.
