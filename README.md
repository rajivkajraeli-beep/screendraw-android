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
5. Pick a tool from the toolbar: Pen, Highlighter, Eraser, a collapsible
   **Shapes** section (Circle, Square, Line, Arrow), and a collapsible
   candlestick section (bullish/bearish, 4 body-size stages, no-wick, and
   doji).
6. Pick a color from the swatches, or tap **+** to open the full HSV
   **color wheel picker** (drag on the wheel for hue/saturation, use the
   brightness slider, then **Use color**).
7. Tap the small orientation icon next to the minimize handle to flip the
   toolbar between vertical (scrolls up/down) and horizontal (scrolls
   left/right) layouts — handy in landscape or on smaller screens.
8. Long-press the minimized `⠿` dot for a **quick-bar** (Pen, Eraser,
   Clear, Expand) without opening the full panel; a normal tap on the dot
   toggles draw mode directly.
9. Drawing is **palm-rejection aware**: only one touch drives a stroke at
   a time, so a resting palm or a second finger can't interrupt it. A
   stylus always takes priority over a finger, and stylus strokes are
   pressure-sensitive (press harder for a thicker line). A touch/hover
   indicator bubble shows where the tip will draw.
10. **Save** exports just your annotation layer (transparent PNG) to
    `Pictures/ScreenDraw` in your gallery — Android doesn't allow capturing
    the actual screen behind the overlay without a separate permission flow
    (MediaProjection), so it saves your markings only, not the app behind
    them.
11. **Quit** stops the overlay entirely.

## Known limitations (be upfront about these)

- No custom app icon yet — it borrows a generic system icon so the project
  builds without needing image assets from me. Replace it anytime via
  Android Studio's **Image Asset Studio** (right-click `res` → New → Image
  Asset).
- On some Android versions/OEM skins (Xiaomi, Vivo, Oppo especially),
  overlay permission and background/foreground-service behavior is
  restricted further — if the toolbar doesn't appear after granting
  permission, check your phone's battery-optimization / "autostart"
  settings for the app too.

## Build status

Every push to `main` is built automatically by the `Build APK` GitHub
Actions workflow — check the **Actions** tab on GitHub for the latest run
and download the APK artifact from there. All current features (palm
rejection, stylus pressure, quick-bar, touch indicator, shapes section,
color wheel picker, orientation toggle) have been built and manually
verified on-device.
