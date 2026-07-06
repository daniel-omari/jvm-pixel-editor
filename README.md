# Pixel-Based Graphics Editor

A desktop raster / pixel-art editor built in **Java 17 (Swing) with a Kotlin
layer engine**. Full layer support, a rich drawing toolset with per-tool
settings, command-pattern undo/redo, zoom-to-cursor navigation, and a native
layered project format — all in one self-contained desktop app with a FlatLaf
dark UI.

<!-- TODO media: hero demo GIF here (assets/demo.gif) -->
<!-- TODO media: 3-4 screenshots (assets/*.png) — layers panel, brush settings, HSV picker, selection -->

## Features

**Layers** (Kotlin engine)
- Add, delete, duplicate, merge down, reorder (buttons or drag-and-drop)
- Per-layer opacity and visibility, inline rename, right-click context menu
- Tools always draw on the active layer; structural operations are undoable
- Text lives on its own re-editable layer

**Drawing tools**, each with its own settings panel
- Brush: four styles (natural, spray, dotted, oil), continuous size up to 300 px,
  stroke opacity, and a live cursor size-ring. Strokes are composited once at the
  chosen opacity so overlapping segments don't darken at the joints
- Pencil: hard-edged, size up to 200 px, with opacity
- Eraser: erases to transparency, size up to 400 px
- Shape: rectangle, circle, line, triangle, pentagon, hexagon, with stroke width
- Text: any installed font, adjustable size and style
- Eyedropper with a loupe magnifier; flood fill

**Colour**
- Photoshop-style HSV picker (saturation/value square + hue bar) docked in the UI
- Current swatch, recent colours, and a full system chooser for custom colours

**Selection** (Kotlin)
- Marquee select, move, copy/cut/paste/delete, select-all, recolour region

**Transform**
- Rotate / flip the whole document — every layer together, adopting swapped
  dimensions on quarter-turns — or just the current selection

**Navigation**
- Mouse-wheel and Ctrl+wheel zoom-to-cursor; spacebar-drag or middle-drag panning
- Status bar with live zoom %, Fit, and 100% buttons

**Files**
- Native **`.pxe` project format**: a ZIP of one PNG per layer plus a manifest,
  round-tripping the full layer stack (order, names, opacity, visibility)
- Import PNG/JPEG/BMP; export flattened PNG/JPEG/BMP
- Custom canvas sizes with presets, autosave, safe-exit prompt

**Workspace**
- Resizable, persisted panel layout with Reset Layout; in-app help for every tool

## Built with

Java 17, Kotlin, Swing, FlatLaf, Gradle. The layer engine, selection tool, and
safe-exit flow are Kotlin; the UI, tools, and command system are Java — the two
interoperate directly in one Gradle build.

## Getting started

Requires JDK 17.

```bash
./gradlew run     # build and launch
./gradlew test    # run the headless unit tests
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The in-app help (Ctrl+H)
documents every tool. User settings (panel layout, first-run flags) are stored
in `~/.pixeleditor`.

### Packaging

```bash
./gradlew jpackage    # native installer in build/dist
```

Uses the JDK's `jpackage`; on Windows the `.msi` type requires the
[WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases). Change
`--type` to `app-image` in `build.gradle` for a plain runnable folder
with no extra tooling.

## Keyboard shortcuts

| Action | Shortcut |
| ------ | -------- |
| Brush / Pencil / Eraser / Eyedropper | B / P / E / I |
| Shape / Select / Zoom / Text | S / C / Z / T |
| Select all / Deselect | Ctrl+A / Esc |
| Undo / Redo | Ctrl+Z / Ctrl+Y |
| Save / Save As | Ctrl+S / Ctrl+Shift+S |
| New / Open | Ctrl+N / Ctrl+O |
| Help / Exit | Ctrl+H / Ctrl+Q |

## Architecture notes

- **Layer stack:** `CanvasPanel` owns a `LayerStack` (Kotlin); the active layer
  is the drawing surface, so every tool and command operates on it unchanged.
  Rendering composites visible layers bottom-to-top over a checkerboard.
- **Undo/redo:** command pattern (`commands/`), covering pixel edits and
  structural layer operations (delete, duplicate, merge, resize, rotate).
- **Coordinates:** one source of truth (`screenToImage` / `imageToScreen`)
  folds zoom, pan, and centering, shared by the renderer and all tools.
- **Tests:** a headless JUnit 5 suite covers the layer engine (stack
  operations, compositing, resize) without needing a display.

## What I'd do next

- Extend test coverage to the undo/redo command classes and the `.pxe`
  round-trip (needs the file dialogs factored out of `Project`)
- An application icon for the packaged build
- Per-layer blend modes (multiply, screen, overlay)
- Selection transforms beyond rotate (free scale, skew)

## Licence

MIT — see [LICENSE](LICENSE).
