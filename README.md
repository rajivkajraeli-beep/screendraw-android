# ScreenDraw for Android

Draw over **any app** on your phone — freehand, shapes, colors — same idea
as the Windows version, but Android's overlay system handles the
click-through toggle natively (no hacky workarounds needed there, unlike
Windows).

## Requirements

- Android Studio (free, from developer.android.com/studio)
- An Android phone with USB debugging enabled, or an emulator

## Setup

1. Install Android Studio, open it.
2. **File → Open** → select this `screendraw-android` folder.
3. Let it sync Gradle (first time takes a few minutes — downloads the
   Android SDK/build tools if you don't have them yet).
4. Connect your phone via USB (enable *Developer Options → USB debugging*
   in your phone's Settings first), or start an emulator from Android
   Studio's Device Manager.
5. Click the green **Run ▶** button in Android Studio, pick your device.

## Using the app

1. On first launch, tap **"Grant overlay permission"** — Android will take
   you to a settings screen; enable "Allow display over other apps" for
   ScreenDraw, then go back.
2. Tap **"Start ScreenDraw"**. A small floating toolbar appears.
3. Drag the toolbar anywhere by its `⠿ ⠿ ⠿` handle.
4. Tap **"Draw: OFF"** to turn drawing on — now your touches draw on
   screen instead of passing through to whatever app is open. Tap again
   to turn it off and interact with your phone normally (your drawings
   stay on screen either way, since this is a live overlay, not a freeze
   like the Windows version).
5. Pick a tool (Pen, Highlighter, Eraser, Circle, Square, Line, Arrow),
   color, and thickness from the toolbar.
6. **Save** exports just your annotation layer (transparent PNG) to
   `Pictures/ScreenDraw` in your gallery — Android doesn't allow capturing
   the actual screen behind the overlay without a separate permission flow
   (MediaProjection), so it saves your markings only, not the app behind
   them.
7. **Quit** stops the overlay entirely.

## Known limitations (be upfront about these)

- **I could not build or run this myself** — my environment has no Android
  SDK/emulator. There will likely be small build errors on first import
  (missing resource, a Gradle/AGP version mismatch, etc.) — that's normal
  for a first Android Studio import. Paste me the exact error and I'll fix
  it directly.
- No custom app icon yet — it borrows a generic system icon so the project
  builds without needing image assets from me. Replace it anytime via
  Android Studio's **Image Asset Studio** (right-click `res` → New → Image
  Asset).
- No multi-stage candle tools like the Windows version — this is a clean
  MVP (pen, highlighter, eraser, 4 shapes, colors, thickness, undo, clear,
  whiteboard, save). Tell me if you want those ported over too.
- On some Android versions/OEM skins (Xiaomi, Vivo, Oppo especially),
  overlay permission and background/foreground-service behavior is
  restricted further — if the toolbar doesn't appear after granting
  permission, check your phone's battery-optimization / "autostart"
  settings for the app too.
