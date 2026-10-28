"""Runs Local Weather in a copy of a real launcher profile (production jars: Sodium, Iris, Voxy ... as installed).

    python tools/live/prodscene.py <profile dir> <world dir> <run dir> [options]

    --no-scene            just open the world (drive it with walktest.sh / mcdrive.ps1); default is the strafe scene
    --debug-occlusion     -PdebugOcclusion equivalent
    --no-shader-patch     -PnoShaderPatch equivalent
    --warmup <ticks>      dev scene warmup (default 900)
    --size <w>x<h>        window size, e.g. 2560x1440 to match a fullscreen monitor
    --bob <true|false>    view bobbing
    --disable <prefix>    leave mod jars starting with this out (repeatable), e.g. better-clouds, localweather, iris

Needs tools/live/prod_cmdline.txt from grab_cmdline.ps1 (the launcher's own command, token blanked). The profile is
copied once into <run dir>/game and never touched; the world is copied without Voxy's lock file. The release jar reads
the same system properties as the dev runs (-Dlocalweather.devscene=strafe ...).
"""
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    args = sys.argv[1:]
    if len(args) < 3:
        sys.exit(__doc__)
    profile, world, out = (os.path.abspath(a) for a in args[:3])
    args = args[3:]
    props = {"localweather.devscene": "strafe", "localweather.warmup": "900"}
    size = None
    bob = None
    disable = []
    while args:
        a = args.pop(0)
        if a == "--no-scene":
            props.pop("localweather.devscene")
        elif a == "--debug-occlusion":
            props["localweather.debugOcclusion"] = "true"
        elif a == "--no-shader-patch":
            props["localweather.noShaderPatch"] = "true"
        elif a == "--warmup":
            props["localweather.warmup"] = args.pop(0)
        elif a == "--size":
            size = args.pop(0).split("x")
        elif a == "--bob":
            bob = args.pop(0)
        elif a == "--disable":
            disable.append(args.pop(0))
        else:
            sys.exit(f"unknown option {a}")

    game = os.path.join(out, "game")
    world_name = os.path.basename(world)
    if not os.path.isdir(game):
        os.makedirs(game)
        for d in ("mods", "config", "resourcepacks", "shaderpacks"):
            if os.path.isdir(os.path.join(profile, d)):
                shutil.copytree(os.path.join(profile, d), os.path.join(game, d))
        shutil.copy(os.path.join(profile, "options.txt"), game)
        # LOCK (Voxy's RocksDB) and session.lock are held while the real game runs; the rest copies fine.
        shutil.copytree(world, os.path.join(game, "saves", world_name), ignore=shutil.ignore_patterns("LOCK", "session.lock"))
    shutil.rmtree(os.path.join(game, "screenshots"), ignore_errors=True)

    mods = os.path.join(game, "mods")
    off = os.path.join(game, "mods-off")
    os.makedirs(off, exist_ok=True)
    for f in os.listdir(off):
        # An interrupted run can leave a jar in both folders.
        if os.path.exists(os.path.join(mods, f)):
            os.remove(os.path.join(off, f))
        else:
            shutil.move(os.path.join(off, f), mods)
    for prefix in disable:
        for f in os.listdir(mods):
            if f.lower().startswith(prefix.lower()):
                shutil.move(os.path.join(mods, f), off)
                print("disabled", f)

    path = os.path.join(game, "options.txt")
    with open(path, encoding="utf-8") as f:
        options = f.read()
    options = re.sub(r"(?m)^fullscreen:.*$", "fullscreen:false", options)
    options = re.sub(r"(?m)^pauseOnLostFocus:.*$", "pauseOnLostFocus:false", options)
    if bob:
        options = re.sub(r"(?m)^bobView:.*$", f"bobView:{bob}", options)
    with open(path, "w", encoding="utf-8") as f:
        f.write(options)

    with open(os.path.join(HERE, "prod_cmdline.txt"), encoding="utf-8-sig") as f:
        cmd = f.read()
    cmd = re.sub(r'--gameDir\s+("[^"]*"|\S+)', lambda m: f'--gameDir "{game}"', cmd)
    if size:
        cmd = re.sub(r"--width\s+\S+", f"--width {size[0]}", cmd)
        cmd = re.sub(r"--height\s+\S+", f"--height {size[1]}", cmd)
    # The Modrinth App wraps the game in its own launcher (an agent plus com.modrinth.theseus.MinecraftLaunch) that
    # talks to the running app; the game itself doesn't need it.
    cmd = re.sub(r"\s-javaagent:\S*theseus\.jar", "", cmd)
    cmd = re.sub(r"\s-Dmodrinth\.internal\.\S+", "", cmd)
    cmd = cmd.replace(" com.modrinth.theseus.MinecraftLaunch ", " ", 1)
    main_class = " net.fabricmc.loader.impl.launch.knot.KnotClient "
    if main_class not in cmd:
        sys.exit("not a Fabric launch command")
    flags = " ".join(f"-D{k}={v}" for k, v in props.items())
    cmd = cmd.replace(main_class, f" {flags}{main_class}", 1) + f' --quickPlaySingleplayer "{world_name}"'

    print("running in", game, "with", props)
    with open(os.path.join(out, "launch.log"), "w", encoding="utf-8") as log:
        code = subprocess.call(cmd, cwd=game, stdout=log, stderr=subprocess.STDOUT)
    print("exit", code)


if __name__ == "__main__":
    main()
