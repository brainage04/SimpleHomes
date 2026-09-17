# SimpleHomes icon

## What this is

The mod's icon: `icon.png` — 340x340 RGB PNG, 2 187 bytes,
sha256 `9f7e449e2a02e084db179ff16cb9a733f35da1639321b58087c8e4005ae5aa35`.

It is a real in-game screenshot of the mod's `/home` tab-completion, cropped to a square at
native GUI scale 4. No resize, no resampling, no interpolation, no compositing, no padding.

## How it was made

**Method: real Minecraft capture.**

| | |
|---|---|
| Client | Minecraft 26.2, Fabric Loader 0.19.3, OpenJDK 25.0.4.1+1 |
| Mods loaded | fabric-api 0.156.0+26.2, simplehomes 1.0.0, simpletpa 1.2.0, spawncommands 1.0.0, brainagehud 1.0.3, hudrendererlib 1.0.7, cloth-config-fabric 26.2.155, brainagelib 1.0.1 |
| Display | own Xvfb `:197`, 5120x2160x24; the primary desktop was never used |
| Audio | own PulseAudio null sink `round3_crops2` (`soundDevice=Round3Crops2`); the sink route was verified with `pactl` before every interaction |
| Renderer | software OpenGL (Mesa llvmpipe), `-XX:ActiveProcessorCount=8`, `LP_NUM_THREADS=8` |
| Shader pack | **none** |
| Resource pack | **none** |
| World | `UI Void` — a byte-copy of the BrainageHUD capture world (flat generator, `layers=[]`, biome `minecraft:the_void`, seed `20260910`), prepared by `prepare.py` |
| Options | GUI scale 4, `renderClouds=false`, `maxFps=20`, `renderDistance=2` (the client enforced its minimum of 12 and logged that), `soundCategory_master=0.01` |
| Scene | `/gamemode creative` → `/gamemode spectator`, `/time set noon`, `/weather clear`, `/tick freeze`, `/tp @s 0.5 -60 0.5 0 -90` — the camera looks straight up into the void sky |
| Screenshot | chat history cleared with F3+D, then `t` and the literal text `/home ` typed at 80 ms/key, then the game's own native F2 PNG (`capture_scene.py capture simplehomes-home '/home '`) |

The three homes `base`, `mine` and `village` existed in that world, so the client draws its
native completion `/home base` in the input line plus the three completion entries above it.

The delivered image is the exact integer crop `(0, 1820, 340, 2160)` of
`frames/simplehomes-home-wide-full.png`. Verified while creating this provenance: cropping the
shipped frame to that box reproduces `icon.png` byte for byte.

Layout inside the delivered image (all measured, native pixels): the completion panel occupies
`x 148..279, y 136..279` (132x144 px, three 48 px rows); the panel's own dark background is
included in the crop, so the sky margins are 148 px left and 60 px right of the panel. The
native chat input line below the panel is included; its full-width background bar runs to the
right edge of the crop, exactly as in the source frame.

## Provenance files

| Path | What it is |
|---|---|
| `manifest.json` | Round-3 delivery record for the three crops2 icons, including this one's `fixedFrom` note and measured panel box |
| `frames/simplehomes-home-wide-full.png` | **The native 5120x2160 F2 screenshot this icon is cropped from** |
| `crop.py` | **The script that writes the three 340x340 crops** (`make_crop`; for SimpleHomes the delivered box is the bottom-left anchored one, see Notes) |
| `capture_scene.py` | The capture driver: scene verification, chat clearing, typing, F2, screenshot copy |
| `prepare.py` | Builds this session's runtime from the BrainageHUD capture world (world, configs, options, argfile, launcher) |
| `measure.py` | Ink/panel measurement of the native autocomplete band, with the per-entry glyph-line boxes |
| `verify.py` | Re-opens the written PNGs and asserts they are exact source crops with the measured margins |
| `verification.json`, `crop-report.json` | The measured results for all three icons (see Notes on which run each record belongs to) |
| `evidence/scene-commands.json` | The scene commands as verified through the client's own chat feedback |
| `evidence/audio-before-interaction.json` | Proof that the client's audio stream was on this session's own sink |
| `evidence/geometry-probe.json` | Measured panel/glyph geometry of the source frames |
| `evidence/simplehomes-home-wider-capture.json` | The capture record of this frame: tag, typed text, frame path, 5120x2160, GUI scale 4, sky conditions |
| `launch-crops2.sh`, `client-crops2.args` | The exact launcher and Java argfile of the session |
| `cleanup.json`, `blockers.json` | Resource teardown record and the session's known limitations |

Excluded on purpose: the game directory `runtime/` (21 MB — its F2 screenshots are duplicates
of the two frames kept here), session logs, `__pycache__`, the superseded centred crop
`evidence/simplehomes-autocomplete-square-previous.png`, and the evidence for the other two
icons of the same session where it is not shared context (they are in their own mods'
provenance).

## How to regenerate

The session ran with working directory `<round3>/captures-crops2`:

```sh
cd captures-crops2
python3 prepare.py                                     # builds runtime/ from the captures-ui world
Xvfb :197 -screen 0 5120x2160x24 -nolisten tcp &
pactl load-module module-null-sink sink_name=round3_crops2 \
      sink_properties=device.description=Round3Crops2
sh launch-crops2.sh                                    # Minecraft 26.2 client, GUI scale 4
python3 capture_scene.py setup                         # creative -> spectator, noon, clear, freeze, camera up
python3 capture_scene.py capture simplehomes-home '/home '
python3 crop.py && python3 verify.py                   # 340x340 crops + verification
```

For this icon specifically, `crop.py`'s `make_crop("simplehomes", …)` reproduces the input to
`frames/`; the delivered file is the bottom-left anchored crop of that frame (Notes):

```sh
python3 - <<'PY'
from PIL import Image
Image.open('frames/simplehomes-home-wide-full.png').convert('RGB') \
     .crop((0, 1820, 340, 2160)).save('simplehomes-autocomplete-square.png')
PY
```

Prerequisites not shipped: the Minecraft 26.2 client, Fabric Loader and the mod jars named in
`client-crops2.args`; the `UI Void` world is rebuilt by `prepare.py` from the BrainageHUD
session's world, which is not part of this provenance.

## Notes

* **`verification.json` and `crop-report.json` describe the superseded crop, not the delivered
  file.** Both record the first attempt: box `[44, 1764, 384, 2104]`, equal 104/104 px sky
  margins, output sha256 `c16a24fc…`. That framing clipped the native input command text at
  `x` 16, so the image was re-cropped anchored to the bottom-left of the frame — box
  `[0, 1820, 340, 2160]`, sha256 `9f7e449e…`, which is what is delivered and what
  `manifest.json` records (with `fixedFrom: evidence/simplehomes-autocomplete-square-previous.png`).
  The superseded PNG itself is **not** copied into this provenance.
* Consequence of that re-crop: the sky margins of the delivered image are 148 px left and
  60 px right of the panel — *not* equal. The three completion entries and the typed input
  line are complete and unclipped, which was the deciding requirement.
* The SimpleHomes completion list is drawn at the caret after the typed `/home `, so its panel
  starts at frame `x` 148 while SimpleTPA's and SpawnCommands' start at `x` 36; that is why the
  same 340x340 box produces different left margins for the three icons.
* Minecraft 26.2 renders the chat autocomplete as an inline completion plus this suggestion
  panel; it does not draw a dropdown list. `blockers.json` documents that, and that the
  client rejected `simulationDistance=2` and kept its minimum of 12.
* The `verify.py` assertion `spawncommands`/`simpletpa`/`simplehomes` `exactSourceCrop` holds
  for the earlier SimpleHomes box; for the delivered box the same property was re-checked
  when this provenance was created (byte-identical crop of the shipped frame).

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
