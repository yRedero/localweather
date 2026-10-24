# Local Weather

NeoForge 1.21.1 mod that replaces global weather with **storm cells**: discs of rain that form upwind of players, drift with a wandering wind, and fade out. It can pour in one valley while the next one is dry. Built as a companion to [Better Clouds](https://modrinth.com/mod/better-clouds) (optional, client-side, 1.13+).

## How it works

- **Server** (`server/`): `WeatherSimulator` runs in place of vanilla's `advanceWeatherCycle`. Every second it moves and ages cells, spawns new ones upwind of players (weighted by biome downfall; deserts rarely spawn them) and syncs the cells near each player. Cells are saved per dimension in `WeatherSavedData`.
- **Gameplay hooks** (`mixin/`): `isRainingAt`, lightning, snow layers and cauldron filling check the cell over that column. Sleeping clears only the storms over the players. `/weather` spawns or clears cells.
- **Client** (`client/`): `ClientWeather` extrapolates synced cells between syncs and eases rain/thunder at the camera, which drives vanilla's rain, sky, fog and sound. F3 shows the local weather and the nearest cells.
- **Better Clouds** (`compat/`, `client/betterclouds/`), all optional and tunable in the mod's config screen:
  - *Local coverage*: every cloud sample gets its own cloudiness: the fair-weather cover (`cloudCover`, 0.62; Better Clouds uses 0.5) raised to full overcast by storms. A storm's overcast reaches about a third past its rain, and clouds only turn dark where it actually rains. Rain, lightning and clouds all use the same noise-bent storm outline (`StormCell`), so a dark cloud overhead means rain.
  - *Far clouds*: draws clouds out to 2560 blocks (Better Clouds stops at 4x render distance). Beyond `detailDistance` (1536) only every 2nd cloud is generated at twice the size, beyond twice that every 4th at four times; the boundary is dithered so there's no visible ring. Clouds write their own depth with a 16 km range so far clouds sort correctly instead of flickering through each other.
  - *Storm look*: storms are built from normal cloud puffs, each nudged sideways so no faces line up. Thunderstorms grow puff columns up to ~240 blocks out of separate convective cells, with a flat anvil of puffs above; rain adds one puff and lowers the deck; storm clouds are darker and denser. Storm outlines are bent by noise so they aren't circles.
  - *Weather systems*: a storm's clouds start building about 2.6 radii out, so the sky fills in gradually long before the rain; the sky and fog grey over with it (`ClientLevelMixin`, `FogRendererMixin`). Better Clouds' global darkening by the rain at the camera is toned down, so from inside a storm only the storm clouds are dark.
  - *Cloud drift*: with local weather, Better Clouds' clouds drift with the storm wind instead of their own random path, so storms move smoothly with the clouds instead of being rebuilt as they travel.
  - *High layer*: a second, flat cloud layer above the normal one that thickens around storms.
  - How: the generator task's sampler and point creation are wrapped (`ChunkedGeneratorTaskMixin`, logic in `CloudGenContext`). Each cloud carries a size, darkness and flat flag packed into its y coordinate (`CloudCode`), which a text patch to Better Clouds' coverage and shading shaders decodes (`BetterCloudsShaderPatch`; if Better Clouds changes those shaders, the patch backs off and only coverage stays). Chunk culling uses vanilla's frustum with far boxes scaled towards the camera, since its far plane would otherwise hide far clouds. Better Clouds' chunk cache stays on and is cleared when storms change or the camera moves 512 blocks.
- Only overworld-like dimensions (sky light, no ceiling) are affected. The network channel is optional, so the mod falls back to vanilla weather when only one side has it.

Cell types: `shower` (0.9-1.6 km radius), `rain` (2.6-4.8 km fronts), `thunderstorm` (1.4-2.4 km, lightning in the core, warm biomes only). At most two storms gather around a player, and each forms upwind at a distance scaled to its size.

## Config and presets

Two config files, both editable in the mod list (Local Weather → Config). Each has a `preset` that fills in every value below it when you pick it, in the screen or in the file (also while the game is closed); the values can still be tweaked afterwards. `CUSTOM` never changes anything.

- **Client** (`config/localweather-client.toml`): no rain above the clouds (on by default), cloud cover, cloud height offset, far cloud and detail distance, storm towers and their height, storm darkness and its strength, thick rain clouds, the high layer, sky gloom, and how much of Better Clouds' global rain tint to keep. Presets: `DEFAULT`, `PERFORMANCE`, `CINEMATIC`, `VOXY_TECTONIC`.
- **World** (`config/localweather-server.toml`, or per world in `<world>/serverconfig/localweather-server.toml`, which overrides it): cloud height (sent to every player) and dry above the clouds, storm size and lifetime, max storms near a player, storm frequency, shower and thunderstorm chance, wind speed range. Presets: `DEFAULT`, `CALM`, `STORMY`, `VOXY_TECTONIC`.

`VOXY_TECTONIC` is for very tall terrain and extreme render distances: clouds 160 blocks higher (above Tectonic's mountains), drawn out to 6 km, 360-block storm towers, and 1.6x larger, longer storms. Set it in both files. If you use Tectonic's increased build height, raise `cloudHeightOffset` further.

**Cloud height is the world's.** The world config's `cloudHeightOffset` is synced to every player, who draw their clouds there (unless they turn off `followServerCloudHeight`), and with `dryAboveClouds` the server treats everything above the cloud base as dry: no wetness, snow, cauldron filling or lightning. The client's own `cloudHeightOffset` only applies without a Local Weather server or with following turned off.

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
./gradlew runClient -PquickPlay="New World" -PdevScene=inside   # same, standing inside a moving storm
./gradlew runClient -PquickPlay="New World" -PdevScene=above    # same storms, seen from high above the clouds
./gradlew runGameTestServer   # gametest/LocalWeatherTests
```

Before every client launch (Gradle or IDE), `installClientMods` copies Better Clouds and YACL from Modrinth into `run/mods`. Better Clouds is client-only and crashes servers, so the server and gametest runs use `run-server/` instead. Versions are in `gradle.properties`.

## Next

1. Distant rain shafts under far-away cells, positional thunder.
2. Config, datapack biome weather profiles, season-mod hooks.
