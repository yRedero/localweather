# Local Weather

NeoForge and Fabric 1.21.1 mod that replaces global weather with **storm cells**: discs of rain that form upwind of players, drift with a wandering wind, and fade out. It can pour in one valley while the next one is dry. Built as a companion to [Better Clouds](https://modrinth.com/mod/better-clouds) (optional, client-side, 1.13+).

Fabric needs Fabric API and [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) (the same config files as on NeoForge); Mod Menu opens the config screen.

## Layout

- `common/`: all the mod's code, mixins and assets, compiled against vanilla Minecraft only. It reaches the loader through `platform/Platform` (mod list, sending the sync payload), found with `ServiceLoader`.
- `neoforge/`, `fabric/`: entry classes that hook the shared code to the loader's events, the loader's metadata and game test registration. Both compile the common sources into their own jar.
- Members NeoForge's access transformer opens up are listed in `common/accesstransformer.cfg` (compile-time only) and opened on Fabric by `fabric/src/main/resources/localweather.accesswidener`.

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

Two config files, both editable in the mod list (Local Weather → Config). Presets fill in the values they cover when you pick them, in the screen or in the file (also while the game is closed); the values can still be tweaked afterwards. `CUSTOM` never changes anything.

- **Client** (`config/localweather-client.toml`), one `preset` (`DEFAULT`, `PERFORMANCE`, `CINEMATIC`, `VOXY_TECTONIC`): cloud resolution, no rain above the clouds, cloud cover, cloud height offset (used without a Local Weather server), far cloud and detail distance, storm towers and their height, storm darkness and its strength, thick rain clouds, the high layer, sky gloom, and how much of Better Clouds' global rain tint to keep.
- **World** (`config/localweather-server.toml`, or per world in `<world>/serverconfig/localweather-server.toml`, which overrides it), two presets:
  - `heightPreset` (`AUTO`, the default; `VANILLA` 192, `TECTONIC` +160, `TALL_TERRAIN` +320): cloud height and dry above the clouds. `AUTO` asks the world generator for the surface height on a 25x25 grid over ±7.7 km around spawn (no chunks generated, on a background thread) and puts the cloud base above most peaks and at least above the highest sample. It's saved per dimension and redone if the build height changes; `/localweather cloudheight` shows it, `/localweather cloudheight recompute` redoes it.
  - `weatherPreset` (`DEFAULT`, `CALM`, `STORMY`, `HUGE_STORMS`): storm size, lifetime, count and frequency, shower and thunderstorm chance, wind speeds.

For Voxy + Tectonic use the client preset `VOXY_TECTONIC` with `heightPreset = AUTO` and `weatherPreset = HUGE_STORMS`: clouds above the real mountains, drawn out to 6 km, 360-block storm towers, and 1.6x larger, longer storms.

**Cloud height is the world's.** The world's `cloudHeightOffset` is synced to every player, who draw their clouds there (unless they turn off `followServerCloudHeight`), and with `dryAboveClouds` the server treats everything above the cloud base as dry: no wetness, snow, cauldron filling or lightning.

## Commands (op level 2)

```
/localweather list                    cells in this dimension, nearest first
/localweather here                    rain and thunder at your position
/localweather spawn <type> [radius]   spawn a cell centred on you
/localweather clear                   remove all cells
```

## Develop

`./gradlew build` builds `neoforge/build/libs/localweather-neoforge-<version>.jar` and `fabric/build/libs/localweather-fabric-<version>.jar`. The NeoForge runs below are `:neoforge:runClient` etc.; the Fabric client takes the same switches as `:fabric:runClient` and plays in `run-fabric/` (servers in `run-fabric-server/`), with Better Clouds, YACL and Mod Menu.

```
./gradlew build
./gradlew :neoforge:runClient           # with Better Clouds + YACL from Modrinth
./gradlew :neoforge:runClient -PquickPlay="New World"   # straight into a world
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene   # storms around you, screenshots to run/screenshots, then quits
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=inside   # same, standing inside a moving storm
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=above    # same storms, seen from high above the clouds
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=ground   # same storms, standing on the ground
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=strafe   # world's own weather; walks left/right, a frame per tick
python tools/flicker.py <frames> <out> [<reference frames>] [--region x0 y0 x1 y1]   # finds flicker in those frames
```

Debugging switches for `runClient` (both loaders): `-PnoShaderPatch` (leave Better Clouds' shaders alone, to compare against), `-PdebugOcclusion` (paint each cloud pixel by how it was tested against terrain: blue no terrain, green near terrain, cyan LOD terrain behind the cloud, red hidden, yellow/magenta no usable LOD depth), `-PstrafeSpeed=0` (strafe scene standing still). The strafe scene also saves Voxy's depth texture to `run/screenshots/lod_depth.png`.

```
./gradlew :neoforge:runGameTestServer   # gametest/LocalWeatherTests
./gradlew :fabric:runGametest            # the same tests on Fabric
```

On NeoForge, before every client launch (Gradle or IDE), `installClientMods` copies Better Clouds and YACL from Modrinth into `run/mods`. Better Clouds is client-only and crashes servers, so the server and gametest runs use `run-server/` instead. On Fabric, Loom puts them on the client's dev classpath and Fabric skips client-only mods on servers by itself. Voxy has no Fabric 1.21.1 release on Modrinth, so its depth hookup only works through Roxy on NeoForge. Versions are in `gradle.properties`.

### Flicker hunting in a real setup

The dev runs can't reproduce everything: other mods' production jars, a real LOD store, and how a player actually moves. `tools/live/` runs the same checks against a real launcher profile without touching it (Windows; Python tools need Pillow and NumPy):

```
powershell tools/live/grab_cmdline.ps1 -ProcessId <pid of the running game>   # its launch command, login token blanked (gitignored)
python tools/live/prodscene.py <profile> <world> <run dir> [--size 2560x1440] [--debug-occlusion] [--disable better-clouds] ...
bash tools/live/walktest.sh <profile> <world> <run dir> <name> --bob true   # walks with real key presses, scores row flicker
powershell tools/live/mcdrive.ps1 -ProcessId <pid> -Steps "tap:F2","down:A","wait:500","up:A"   # drive any running game
python tools/rowscore.py <frames> [x0 y0 x1 y1]     # how much pixel rows jump around between frames (bands switching on and off)
python tools/lodflick.py <frames> [heat.png] [--mask lod_depth.png]   # flicker counted only on Voxy terrain
```

`prodscene.py` copies the profile once into the run folder, starts it with the launcher's own command minus the Modrinth wrapper, and can leave mods out to bisect. The dev scene's system properties work in the release jar too, and `-Pwarmup=<ticks>` / `-PwindowSize=1920x1080` set warmup and window size in the dev runs.

Found with these (0.1.6): the horizontal bands that flicker over far terrain while walking are Voxy's, not the clouds'. They appear with only Sodium + Voxy installed and disappear with view bobbing off (row score 8.7 vs 0.85). 1.21.1 puts view bobbing into the projection matrix, and Voxy builds its own projection by replacing the near/far entries of that matrix, which is only exact without bobbing. Workaround: turn off View Bobbing.

## Next

1. Distant rain shafts under far-away cells, positional thunder.
2. Config, datapack biome weather profiles, season-mod hooks.
