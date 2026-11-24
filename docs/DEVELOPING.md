# Developing Local Weather

## Layout

- `common/`: all the mod's code, mixins and assets, compiled against vanilla Minecraft only. It reaches the loader
  through `platform/Platform` (mod list, sending the sync payload, config folder), found with `ServiceLoader`.
- `neoforge/`, `fabric/`: entry classes that hook the shared code to the loader's events, the loader's metadata and game
  test registration. Both compile the common sources into their own jar.
- Members NeoForge's access transformer opens up are listed in `common/accesstransformer.cfg` (compile-time only) and
  opened on Fabric by `fabric/src/main/resources/localweather.accesswidener`.
- Configs use NeoForge's `ModConfigSpec` on both loaders (Forge Config API Port on Fabric). The code reads client
  settings only through `client/ClientSettings`, an immutable snapshot rebuilt when the file loads or changes. Presets
  are listed as their differences from the defaults. `client/ClientConfigMigration` moves settings of older files into
  their current sections before the config is registered.
- Dev and test switches are all in `DevFlags` (system properties, see below).

## How it works

- **Server** (`server/`): `WeatherSimulator` runs in place of vanilla's `advanceWeatherCycle`. Every second it moves and
  ages cells, spawns new ones upwind of players (weighted by biome downfall and the weather regions) and syncs the cells
  near each player. Cells are saved per dimension in `WeatherSavedData`.
- **Gameplay hooks** (`mixin/`): `isRainingAt`, lightning, snow layers and cauldron filling check the cell over that
  column. Sleeping clears only the storms over the players. `/weather` spawns or clears cells.
- **Weather regions** (`weather/WeatherRegions`): between the storms the sky varies from clear (0) to overcast (1) in
  areas several km wide, a slow noise field that drifts with the wind (`WeatherSavedData.regionX/Z`) and changes over
  about 1.5 days. Server and clients compute it the same way from the dimension's seed, the drift and the game time
  (synced in the payload, protocol 3). It sets the fair-weather cloud cover, where storms form, a slight dimming of sky
  and daylight under overcast, and how much the sun, moon and stars are hidden (`LevelRendererMixin`).
- **Client** (`client/`): `ClientWeather` extrapolates synced cells between syncs and eases rain/thunder at the camera,
  which drives vanilla's rain, sky, fog and sound. F3 shows the local weather and the nearest cells.
  - `FarWeather` draws the weather past vanilla's rain: the haze (`RainHaze`, a screen pass, `shaders/core/rain_haze`:
    each view ray is stepped through a 128x128 map of rain and thunder around the camera, rewritten every half second),
    then the rain shafts (`RainShafts`: veils on a world-fixed grid under heavy rain, facing the camera, leaning with
    the wind) and the lightning bolts (`LightningBolts`: midpoint-displacement paths ending exactly at the strike) in
    one mesh with the `rain_shaft` shader. That shader compares distances from a copy of the game's depth and from
    Voxy's own (`VoxyDepth`), since much of it lies past the game's far plane; it is drawn with depth clamping and
    skips geometry behind the camera.
  - `LightningFlashes`: flashes light the storm clouds (up to 8 at once, in the cloud shader), far storms flash on their
    own, some with a bolt, followed by thunder from their direction, delayed by distance.
  - Rain fog (`FogRendererMixin`, priority 1500 so it runs after Sodium Extra); Roxy's own fog for Voxy replaces it there.
- **Better Clouds** (`compat/`, `client/betterclouds/`):
  - *Local coverage*: every cloud sample gets its own cloudiness: the regions' fair-weather cover raised to overcast by
    storms. Rain, lightning and clouds all use the same noise-bent storm outline (`StormCell`), so a dark cloud
    overhead means rain.
  - *Far clouds*: beyond `detailDistance` only every 2nd cloud is generated at twice the size, beyond twice that every
    4th at four times; the boundary is dithered. Clouds write their own depth with a 16 km range.
  - *Storm look*: thunderstorms grow puff columns out of separate convective cells with an anvil of puffs above; rain
    adds lumps underneath and lowers the deck.
  - *Cumulus heaps* (`CumulusHeaps`): on a 1.6 km grid about one cell in six holds a heap under clear to fair regions,
    growing and falling apart over about half a day.
  - How: the generator task's sampler and point creation are wrapped (`ChunkedGeneratorTaskMixin`, logic in
    `CloudGenContext`). Each cloud carries a size, darkness and flags packed into its y coordinate (`CloudCode`), which
    a text patch to Better Clouds' shaders decodes (`BetterCloudsShaderPatch`: darkness, height shading, sunset glow,
    silver lining, flashes; if Better Clouds changes those shaders, the patch backs off and only coverage stays).
    Better Clouds' clouds drift with the storm wind, and its chunk cache is cleared when storms change.
- Only overworld-like dimensions (sky light, no ceiling) are affected. The network channel is optional, so the mod
  falls back to vanilla weather when only one side has it.

Cell types: `shower` (0.9-1.6 km radius), `rain` (2.6-4.8 km fronts), `thunderstorm` (1.4-2.4 km, lightning in the
core, warm biomes only).

## Build and run

`./gradlew build` builds `neoforge/build/libs/localweather-neoforge-<version>.jar` and
`fabric/build/libs/localweather-fabric-<version>.jar`. The NeoForge runs are `:neoforge:runClient` etc.; the Fabric
client takes the same switches as `:fabric:runClient` and plays in `run-fabric/` (servers in `run-fabric-server/`).

```
./gradlew build
./gradlew :neoforge:runClient           # with Better Clouds + YACL from Modrinth
./gradlew :neoforge:runClient -PquickPlay="New World"   # straight into a world
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene   # storms around you, screenshots to run/screenshots, then quits
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=inside   # standing inside a moving storm
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=above    # seen from high above the clouds
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=ground   # standing on the ground
./gradlew :neoforge:runClient -PquickPlay="New World" -PdevScene=strafe   # world's own weather; walks left/right, a frame per tick
./gradlew :neoforge:runGameTestServer   # gametest/LocalWeatherTests
./gradlew :fabric:runGametest            # the same tests on Fabric
```

On NeoForge, before every client launch, `installClientMods` copies Better Clouds and YACL from Modrinth into
`run/mods`. Better Clouds is client-only and crashes servers, so the server and gametest runs use `run-server/`. On
Fabric, Loom puts them on the client's dev classpath. Voxy has no Fabric 1.21.1 release on Modrinth, so its depth
hookup is tested through Roxy on NeoForge. Versions are in `gradle.properties`.

### Dev switches

System properties, all in `DevFlags`; they work in the release jar too (pass them through `JAVA_TOOL_OPTIONS` to a
launcher or to autoshot). The Gradle runs set some from `-P` switches.

| Property | Gradle | Does |
|---|---|---|
| `localweather.devscene` | `-PdevScene[=mode]` | the dev scene (`true`, `inside`, `above`, `ground`, `strafe`) |
| `localweather.warmup` | `-Pwarmup=<ticks>` | ticks before the scene's first shot (300) |
| `localweather.strafeSpeed` | `-PstrafeSpeed=<b/t>` | strafe speed, 0 stands still (0.2) |
| `localweather.debugOcclusion` | `-PdebugOcclusion` | paint each cloud pixel by how it was tested against terrain: blue none, green near terrain, cyan LOD terrain behind, red hidden, yellow/magenta no usable LOD depth |
| `localweather.noShaderPatch` | `-PnoShaderPatch` | leave Better Clouds' shaders alone, to compare against |
| `localweather.lightningTest` | | thunderstorms flash 8x as often, with a bolt every time |
| `localweather.cumulusTest` | | a cumulus heap in nearly every grid cell |

`-PwindowSize=1920x1080` sets the dev window size. The strafe scene also saves Voxy's depth texture to
`run/screenshots/lod_depth.png`.

### Checking in a real setup

Visual checks run in a copy of a real launcher profile with this build swapped in, taking scripted screenshots
(the author uses a test tool of their own, autoshot, for this).

`tools/live/` runs the dev scene against a real profile directly (Windows; Python tools need Pillow and NumPy):

```
powershell tools/live/grab_cmdline.ps1 -ProcessId <pid of the running game>   # its launch command, login token blanked (gitignored)
python tools/live/prodscene.py <profile> <world> <run dir> [--size 2560x1440] [--debug-occlusion] [--disable better-clouds] ...
python tools/flicker.py <frames> <out> [<reference frames>] [--region x0 y0 x1 y1]   # finds flicker in frames
python tools/rowscore.py <frames> [x0 y0 x1 y1]     # how much pixel rows jump around between frames
python tools/lodflick.py <frames> [heat.png] [--mask lod_depth.png]   # flicker counted only on Voxy terrain
```

`prodscene.py` copies the profile once into the run folder, starts it with the launcher's own command minus the
Modrinth wrapper, and can leave mods out to bisect.

Found with these (0.1.6): the horizontal bands that flicker over far terrain while walking are Voxy's, not the clouds'.
They appear with only Sodium + Voxy and disappear with view bobbing off (row score 8.7 vs 0.85): 1.21.1 puts view
bobbing into the projection matrix, and Voxy builds its own projection by replacing the near/far entries of that
matrix, which is only exact without bobbing.

## Next

1. Performance pass (measure first): the lumps doubling cloud count, the 12-tap darkness blur, the depth copy every
   frame, cloud rebuilds, the haze, the flash loop; a leaner `PERFORMANCE` preset.
2. Datapack biome weather profiles, season-mod hooks.
