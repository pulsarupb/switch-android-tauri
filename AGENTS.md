# AGENTS.md — runbook for agents working in this repo

This file is the operating manual for autonomous/agent work in `switch-android-tauri`.
Keep it up to date whenever you learn something about the environment, the device, or
the build. It is intentionally specific to this machine and this Switch Lite.

## Goal

A reusable **Svelte 5 + Tauri 2** input stack for a **Nintendo Switch Lite running
LineageOS (Android)**:

- Capture **all** hardware inputs natively: face buttons, L/R/ZL/ZR, Minus/Plus/Home,
  stick clicks, D-pad, both analog sticks + triggers, **multi-touch**, **IMU**
  (accelerometer/gyroscope) and **rumble**.
- Package the capture as clean, reusable code:
  - `crates/tauri-plugin-switch-input` — reusable Tauri 2 mobile plugin (Rust + Kotlin).
  - `packages/svelte-switch-input` — reusable Svelte 5 bindings (runes) + framework-agnostic core.
  - `apps/demo` — a Svelte 5 + Vite SPA visualizer used to verify everything on-device.

## Environment (this machine)

| Thing | Value |
| --- | --- |
| Device serial | `NXVo33D3FDB6z081E07` |
| Device | Switch Lite, `product:vali`, `device:nx`, model `Switch Lite` |
| OS | LineageOS, **Android 15 (SDK 35)**, `arm64-v8a` |
| WebView | `com.android.webview` **154.0.8037.57** |
| Android SDK | `C:\Users\lazar\AppData\Local\Android\Sdk` (platforms 25–36, build-tools 33–36) |
| NDK | `...\Sdk\ndk\29.0.14206865` (25.1 also installed) |
| JDK | `C:\Program Files\Android\Android Studio\jbr` (set `JAVA_HOME`) |
| Gradle/AGP/Kotlin | Gradle 9.6.1, AGP 9.3.1, Kotlin 2.2.10 (generated) |
| bun | 1.3.1 |
| Rust | toolchain pinned to **1.95.0** via `rust-toolchain.toml` |

### Toolchain gotcha (important)

`rustup default stable` is **broken** on this machine: the stable toolchain's `bin/`
only contains `rustfmt`/`cargo-fmt`, so the `cargo.exe`/`rustc.exe` proxies fail with
"not applicable to the 'stable-x86_64-pc-windows-msvc' toolchain".

Fix used here: `rust-toolchain.toml` pins `channel = "1.95.0"`. Working toolchains:
`1.95.0`, `1.93`, `1.75`. If a command fails with that error, you are outside the repo
dir or the pin was removed.

## Repo layout

```
rust-toolchain.toml            # pins Rust 1.95.0 + android targets
Cargo.toml                     # cargo workspace
package.json                   # bun workspaces
scripts/android-env.ps1        # sets JAVA_HOME / ANDROID_HOME / NDK_HOME
crates/tauri-plugin-switch-input/
  src/                         # Rust plugin (commands + models)
  android/                     # Kotlin capture (build.gradle.kts, SwitchInputPlugin.kt, KeyMap.kt, SwitchInputBridge.kt)
  permissions/default.toml     # ACL permissions (allow-set-enabled, allow-vibrate, ...)
packages/svelte-switch-input/  # Svelte 5 lib (runes class + types + tauri transport)
apps/demo/                     # Svelte 5 + Vite SPA
  src/App.svelte               # visualizer
  src-tauri/                   # Tauri app
    gen/android/               # generated Android project (committed; MainActivity is customized)
```

## Build & run

Always source the Android env first (PowerShell):

```powershell
. C:\Users\lazar\Documents\GitHub\switch-android-tauri\scripts\android-env.ps1
```

Desktop (fast UI iteration; native capture is a no-op, `available=false`):

```powershell
bun install
bun run dev            # vite dev server
bunx tauri dev         # desktop window (from apps/demo)
```

Android (the real thing):

```powershell
cd apps\demo
bunx tauri android build --debug --apk --target aarch64   # -> gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk
adb install -r src-tauri\gen\android\app\build\outputs\apk\universal\debug\app-universal-debug.apk
adb shell am start -n dev.pulsarupb.switchinput/.MainActivity
```

`bunx tauri android dev` also works (starts Vite + installs + hot reloads) but is slower
to iterate here; the build/install/start cycle above is preferred.

Typecheck / lint:

```powershell
bun run typecheck          # svelte-check for all workspaces
cargo fmt --all
cargo clippy --workspace --all-targets -- -D warnings
```

## Debug loop (adb)

```powershell
adb devices -l
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard   # device sleeps fast
adb shell svc power stayon true                                          # keep screen on
adb logcat -c
adb logcat -s SwitchInput:*            # native capture logs
adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png .           # screenshot
adb shell top -b -n 1 -o %CPU,ARGS | Select-String switchinput           # check CPU/ANR risk
```

Inject test input (no hardware needed):

```powershell
adb shell input gamepad keyevent KEYCODE_BUTTON_A      # source=GAMEPAD, captured
adb shell input touchscreen tap 400 300
adb shell input touchscreen swipe 200 200 1000 600 1500
```

The device sleeps/locks aggressively. If `dumpsys window | Select-String mCurrentFocus`
shows the launcher, re-wake and re-`am start`. Run `svc power stayon true`.

## Architecture

- **Native-only capture** (per user decision). The WebView Gamepad API is not used.
- `MainActivity` (in `gen/android`, committed) overrides `dispatchKeyEvent`,
  `dispatchGenericMotionEvent` and `dispatchTouchEvent`, forwarding to
  `SwitchInputBridge`. This is the only app-side glue (~15 lines); all logic lives in
  the plugin.
- `SwitchInputPlugin` (`@TauriPlugin`) normalizes events to JSON and pushes them to JS
  as the `input` event via `trigger(...)` (channel-based; JS uses `addPluginListener`).
- `KeyMap` maps raw Linux evdev scan codes (primary) with Android key codes as fallback.
- IMU uses `SensorManager`; rumble uses `InputDevice.vibrator` (API 31+) falling back to
  the default `VibratorManager`.
- Commands: `set_enabled`, `get_state`, `vibrate`, `list_devices`.
- `packages/svelte-switch-input` exposes a Svelte 5 runes class `SwitchInput` plus a
  `SwitchInputTransport` interface (`tauriTransport()` default) so it is testable and
  portable.

### Switch button mapping (evdev → logical)

The controller is exposed as **"Nintendo Switch Virtual Pro Controller"**
(`joycond`). Physical labels:

| Linux code | Name | Switch button |
| --- | --- | --- |
| 304 `BTN_SOUTH` | B | B |
| 305 `BTN_EAST` | A | A |
| 307 `BTN_NORTH` | X | X |
| 308 `BTN_WEST` | Y | Y |
| 310/311 `BTN_TL/TR` | L / R | L / R |
| 312/313 `BTN_TL2/TR2` | ZL / ZR | ZL / ZR |
| 314/315 `BTN_SELECT/START` | Minus / Plus | MINUS / PLUS |
| 316 `BTN_MODE` | Home | HOME |
| 317/318 `BTN_THUMBL/R` | stick click | LSTICK / RSTICK |
| 544–547 `BTN_DPAD_*` | D-pad | UP/DOWN/LEFT/RIGHT |

Android's `KEYCODE_BUTTON_A` is the **bottom** button, so the fallback map swaps
A↔B and X↔Y to keep Nintendo labels correct.

## Rumble on the Switch Lite (hardware limitation)

The Switch Lite has **no rumble / HD-rumble hardware** (unlike the standard Switch and
OLED, whose Joy-Cons have it). `joycond` still advertises `FF_RUMBLE` on the virtual pro
controller and Android accepts the effect, so the code path is correct and verified:

- The plugin selects the device vibrator: `vibrator: input device 'Nintendo Switch Virtual Pro Controller'`.
- The command runs: `vibrate duration=1000 amplitude=255`.
- While it runs, `adb shell dumpsys input` reports
  `Vibrator Input Mapper: Vibrating: true` — i.e. the FF effect is actually being fed to
  `/dev/input/event8`.

There is simply no motor to move, so nothing is felt. `VibratorManagerService.Vibrators`
is empty on this build and `adb shell cmd vibrator` reports "Can't find service:
vibrator". The `vibrate` command works on any rumble-capable controller/device.
`Settings.System.vibrate_input_devices` is `false` by default here; it can be set to `1`
but does not create hardware.

## Gotchas learned the hard way

- **Source bitmask:** use `(sources & SOURCE_GAMEPAD) == SOURCE_GAMEPAD` (equality), not
  `!= 0`. `SOURCE_GAMEPAD = 0x401` shares bits with `SOURCE_KEYBOARD`/`SOURCE_DPAD`, so
  `!= 0` false-positives (e.g. `gpio-keys`).
- **Plugin lifecycle overrides** must use `androidx.appcompat.app.AppCompatActivity`
  (`onResume(activity: AppCompatActivity)`), not `android.app.Activity`.
- **Event flood → ANR.** Raw IMU (100 Hz) + axes overwhelmed the WebView JS bridge
  (114% CPU, "Input dispatching timed out"). Throttle: IMU 50 ms + `SENSOR_DELAY_UI`,
  axes 100 ms and only on change (>0.005). Keep CPU ~30%.
- **Gradle lock:** `org.gradle.configuration-cache=true` + buildSrc caused
  "Timeout waiting to lock build logic queue". It is disabled in
  `gen/android/gradle.properties`; if it recurs: `gradlew --stop`, delete
  `gen/android/.gradle/noVersion`.
- **compileSdk:** generated project asks for 37; only 36 is installed here, so
  `app/build.gradle.kts` uses `compileSdk = 36` / `targetSdk = 36` /
  `buildToolsVersion = "36.0.0"`. Re-check if the SDK is updated.
- **Vite 8 / rolldown:** do not set `build.minify = "esbuild"` (esbuild is not installed);
  Vite 8 defaults are used.
- **Landscape lock:** `android:screenOrientation="sensorLandscape"` in the app manifest.
- **Workspace lib + runes:** the demo's `vite.config.ts` sets
  `optimizeDeps.exclude: ["@pulsarupb/svelte-switch-input"]` and `resolve.dedupe: ["svelte"]`
  so the `.svelte.ts` runes module is compiled, not pre-bundled.

## Commits

Conventional commits, one per meaningful, verified milestone. Examples already used:
`chore: scaffold ...`, `feat(android): ...`, `docs: ...`, `fix: ...`.
Do not commit generated build outputs (`build/`, `.gradle/`, `jniLibs/*.so`,
`tauri.settings.gradle`, `tauri.properties`, `.tauri/`).

## Reusing the pieces in another project

1. Rust: add `tauri-plugin-switch-input = { path = ".../crates/tauri-plugin-switch-input" }`
   and `.plugin(tauri_plugin_switch_input::init())` in your Tauri builder.
2. Add `"switch-input:default"` to your app capability permissions.
3. Copy the three `dispatch*` overrides from `apps/demo/.../MainActivity.kt` into your
   app's `MainActivity` (imports `dev.pulsarupb.switchinput.plugin.SwitchInputBridge`).
4. JS: depend on `@pulsarupb/svelte-switch-input` and use `new SwitchInput()`.

## Open ideas / next steps

- Optional IMU on/off command to reduce load further.
- Expose a raw-event stream vs. state snapshot toggle.
- HD rumble waveforms (currently one-shot amplitude/duration).
- iOS is not supported (Android-only capture).
