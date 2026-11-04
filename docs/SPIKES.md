# Spike results

One section per spike from `PLAN.md §8`. Each spike is throwaway code with a
written answer here: what was tried, on which phones, and the decision it forces.

## S1 — Fork builds and runs low

**Code review done (2026-11-04), not built yet.** Read through upstream 94.1.5
with no Android SDK available (the build environment couldn't reach
`dl.google.com`), so this is from reading the code, not from lint or a device.
Android Studio's lint (`NewApi`) is the next check.

What stands between upstream and older Android versions:

| Blocker | Needs | Where | What it takes |
|---|---|---|---|
| **Calls go through a self-managed `ConnectionService`.** Incoming calls ring only via `TelecomManager.addNewIncomingCall` → `ConnectionService.onCreateIncomingConnection` → `BaresipService.handleIncomingCall`; outgoing calls are created in `onCreateOutgoingConnection`. Self-managed calls don't exist before Android 8. | **API 26** | `BaresipService.kt` (`call incoming`, `registerPhoneAccount`), `ConnectionService.kt` | For Android 7: a second call path that skips Telecom (ring, answer and hang up straight from `BaresipService`, `AudioManager` + audio focus). A real backend change, its own milestone. |
| **Audio uses AAudio.** `libbaresip-android` builds with `API_LEVEL := 28` and the `aaudio` module; the app's `cpp/CMakeLists.txt` links `aaudio`, and `cpp/baresip.c` calls AAudio directly (`AAudio_open_stream`, used by `Utils.aecAgcNsCheck` to probe echo cancelling). Without `libaaudio.so` the app's native library can't even load. | **API 26** | `libbaresip-android/Makefile`, `app/src/main/cpp/`, `assets/config.static` | For Android 7: build with `opensles` instead of `aaudio` (baresip 4.12 still has it), and load AAudio with `dlopen` only where it exists. baresip's `opensles` recorder doesn't ask for the voice-communication preset, so echo cancelling must be checked (S4). |
| Smaller calls: `setShowWhenLocked`/`setTurnScreenOn` (27), `KeyguardManager.requestDismissKeyguard` (26), `Files.readAttributes` (26), `Connection.PROPERTY_SELF_MANAGED` (26), notification channels (26), plus `RoleManager` (29, already guarded) | 26–28 | `MainActivity.kt`, `Utils.kt`, `BaresipService.kt` | Ordinary `if (SDK_INT >= …)` guards |

**So the realistic floors are:**

- **Android 9 (API 28), as upstream:** no code changes. Runs on every phone
  that's on Android 9 or 10 (E4810 if it's on 9, E4811 on 10).
- **Android 8 (API 26):** small changes (native `API_LEVEL` 26, a handful of
  guards). Keeps AAudio and Telecom.
- **Android 7 (API 24):** both big changes above. Needed only for phones like
  the E4610.

Decision pending from the owner (see PLAN §8 S1).

## S2 — CALL and END keys
_Not started._ Record per phone: keycode and scan code for CALL, END, outer END,
headset button; whether END reaches the app during a call; whether Android's own
END handling ends a self-managed call.

## S3 — Ringing and battery with two accounts
_Not started._

## S4 — Audio routing
_Not started._

## S5 — Off-network (NAT)
_Not started._

## S6 — Camera, QR and the setup page
_Not started._
