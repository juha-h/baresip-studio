# AGENTS.md — instructions for AI agents working on BusyCall

BusyCall is a **SIP softphone for keypad ("dumb") Android phones** that also
works on touchscreen phones. It is a **fork of
[baresip-studio](https://github.com/juha-h/baresip-studio)**: the backend
(SIP, audio, accounts, calls) is upstream's; **we build only the UI.**

**Read these first, in order:**
1. **[Flip-DumbPhoneGuide AGENTS.md](https://github.com/Ben-Showalter/Flip-DumbPhoneGuide/blob/main/AGENTS.md)**,
   the house rules for every keypad-phone app. They all apply here, except the
   three deviations in §3 below.
2. `PLAN.md`: what we're building and why.
3. This file: BusyCall-specific rules.

If a rule here conflicts with your habits, this file wins. If a human in the
task says otherwise, the human wins, and you update this file in the same PR.
When you learn a new device quirk, add it to **the guide** (its "Keeping this
guide alive" section), not only here.

---

## 0. The rules that matter most

1. **Don't write calling code. Don't rewrite the backend.** SIP, registration,
   audio, codecs, DTMF, call state, accounts, contacts lookup, call history and
   Telecom integration all come from the upstream backend (§2). If you're about
   to parse SIP, open a socket, or add a JNI call, stop: find the upstream
   function instead.
2. **Backend changes are small and recorded.** Any edit to an upstream file is
   the smallest change that works, is listed in `UPSTREAM.md` (file, why,
   upstream PR if offered), and never mixes with UI work in the same commit.
   Merging upstream releases must stay easy.
3. **One softkey only: Options.** It's on the right by default; the first MENU
   press (Sonim's left MENU key) moves it to the left for good (§4). The other
   softkey does nothing on every screen. Nothing may depend on it.
4. **Every action works three ways:** a physical key, D-pad focus + OK, and touch.
5. **A call can always be ended**, whatever END does on a given phone: the
   in-call screen always has an on-screen **End call** button and Options >
   **Hang up**.
6. **No vendor special cases.** No `if (server is Grandstream)`. A setting other
   systems can use too, or a note in `docs/PBX-NOTES.md`.

## 0a. The name is not final

"BusyCall" is a working name. Keep a rename to a one-file change:

- The visible name appears only as `@string/app_name` (and strings that use it
  as a placeholder). Never hard-code it in layouts, Kotlin, or log tags.
- The link scheme (`busycall:`), the setup-page file name, and the GitHub repo
  used by the update checker are constants in `AppIdentity.kt`, nowhere else.
- **Never change `applicationId`** (`com.busycall.app`), even if the
  visible name changes. Phones would have to uninstall and lose their settings.
- If the scheme changes, the parser keeps accepting every old scheme.

## 1. Before you start a task

- [ ] Restate the acceptance criteria in the PR description.
- [ ] Find the screen in `PLAN.md §5` and in the previews. Not there? **Stop and
      ask.** Don't invent screens.
- [ ] Check whether upstream already does it (`grep` the backend) before writing
      anything that isn't UI.
- [ ] A new dependency needs a one-paragraph reason in the PR. **No Google Play
      Services or Firebase, ever** (guide §5).

## 2. Repository map

```
app/src/main/kotlin/
├── com/tutpro/baresip/      UPSTREAM BACKEND. Keep its package name so merges work.
│   ├── Api.kt               JNI into libbaresip
│   ├── BaresipService.kt    registrations, calls, audio, notifications
│   ├── UserAgent.kt Account.kt Call.kt CallHistory.kt Contact.kt Config.kt Codec.kt
│   ├── ConnectionService.kt InCallService.kt BootCompletedReceiver.kt …
│   └── (upstream *Screen.kt / MainActivity / Theme: DELETED; list them in UPSTREAM.md)
└── <our.package>/           OUR UI. Android Views only.
    ├── bridge/              The ONLY code that touches com.tutpro.baresip. Turns its
    │                        state (incl. Compose mutableStateOf) into StateFlows and
    │                        plain Kotlin types: NumberStatus, CallUi, ContactUi.
    ├── keys/                KeyRouter: the ONLY place that reads keycodes and scan codes
    ├── ui/                  FlipBaseActivity, footer, SystemBars, focus drawables, menus
    ├── home/ dialer/ contacts/ call/ calllog/ numbers/ settings/
    └── update/              Self-update from GitHub Releases (from FlipWeather)
distribution/                libbaresip static libs + headers (built from juha-h/libbaresip-android)
libbaresip-android/          upstream JNI glue (CMake). Don't edit.
docs/                        UX-SPEC.md, SPIKES.md, PBX-NOTES.md, DEVICES.md
UPSTREAM.md                  upstream version we're on + every change to upstream files
```

Rules:

- Only `bridge/` imports `com.tutpro.baresip.*`. Screens and ViewModels use
  `bridge/` types only.
- **No Compose UI anywhere in our code.** The backend's `compose-runtime` state
  types are allowed inside `bridge/` only.
- Only `keys/` uses `KeyEvent.KEYCODE_*` or `scanCode`.

## 3. Deviations from the guide (the only three)

| Guide | BusyCall |
|---|---|
| Left softkey = primary action | **Options is the only softkey.** Right by default, left after the first MENU press. The other softkey has no label and no job (consume it, do nothing). |
| Hide the system navigation bar on every screen | Hide it on **keypad** phones. On a phone with **no hardware number keys** (`configuration.keyboard != KEYBOARD_12KEY`), leave it visible: it's their only Back. |
| Unhandled CALL/END reach `super` | **CALL** is handled on the Dialer, Contacts, Recents, Call log and call screens; elsewhere it reaches `super`. **END always reaches `super`**: on a call screen we hang up/decline first, then pass it on (PLAN §3.4). |

Do not add a fourth without the owner's say-so, recorded here.

## 4. Keys (on top of the guide §1)

The full key table is `PLAN.md §3.1`. In code:

- **`keys/KeyRouter`** turns raw events into logical keys: `Digit(c)`, `HoldZero`,
  `Call`, `End`, `Options`, `Back`, `Up`, `Down`, `Left`, `Right`, `Ok`,
  `OuterEnd`, `OuterSpeaker`, `Headset`. Screens react to logical keys only.
- Base it on the guide's `templates/FlipBaseActivity.kt`. Keep the guide's
  pairing rules: act on UP only for a DOWN this window saw, consume the UP of
  every DOWN you consume, consume the whole MENU press.
- **Options side (guide §1, automatic):** start with Options on the right
  (`SOFT_RIGHT`). On the **first `KEYCODE_MENU`**: open Options, set
  `Prefs.optionsOnLeft = true` and `Prefs.menuKeySeen = true`, and redraw every
  footer with "Options" on the left. No prompt. After that, MENU and
  `SOFT_LEFT` open Options; `SOFT_RIGHT` does nothing. Consume the whole MENU
  press (DOWN, UP, repeats) so AppCompat's panel never opens (Sonim XP3800).
  Settings › Advanced can still change the side by hand.
- **Dialer matching (PLAN §3.3):** digits match numbers **and** names by the
  letters on the keys (2=ABC … 9=WXYZ). **There is no letter-typing mode:**
  don't add an abc toggle, a text `inputType`, or a T9 keyboard. The field is
  `inputType="phone"` with `stateAlwaysHidden` (guide §3). Matching runs on a
  background thread over a cached index (each name pre-converted to its digit
  string per word); never query ContactsProvider per keypress.
- **Digits from Home or a list open the Dialer on key UP** (guide: "Digits that
  open the dialer act on key UP").
- **Hold 0 for `+`: time it yourself** (guide: don't trust `isLongPress` on Kyocera).
- **Clear arrives as `BACK`.** In the number field: delete a digit while there is
  one; at an empty field, go back.
- **BACK never hangs up and never declines.** On an incoming call it silences the
  ringer. In a call it leaves the screen and an "On call · 03:42" bar stays on
  every screen until the call ends; OK on that bar returns to the call.
- **END:** if a call is ringing or active, decline/hang up on DOWN, then
  `return super.dispatchKeyEvent(event)` for both DOWN and UP. Never consume
  END, never relaunch the app after it. Outside a call, don't look at END.
- **On-screen keypad:** only on phones with no hardware number keys (§3). On a
  keypad phone the Dialer never draws it.
- **Outer END / outer Speaker** (scan codes in the guide's `docs/keys.md`): act
  only during a call or incoming call, and only with the screen on.
- **Unknown keys:** show the code on screen (guide §7). When a phone sends
  something new for CALL, END or a softkey, add it to `KeyRouter` with a comment
  naming the phone, and add it to the guide's `docs/keys.md`.

## 5. Touch support

- Every tappable view is also focusable, and every focusable view is tappable.
- Touch targets ≥ 40 dp tall at 240×320 (48 dp where there's room). Call,
  Answer, Decline and End call fill the full width.
- **On-screen dial pad**: only when there are no hardware number keys.
- **The footer "Options" label is a real button.**
- No swipe or gesture is ever required. Answer is a button, never a swipe.

## 6. Multiple numbers

- Every screen that shows or places a call shows **which number** it's on:
  "to Office · 104" (incoming), "from Office · 104" (in call), "Missed · to Home" (recents).
- Outgoing calls use: the "Call from" choice on the Dialer, else the number the
  call came in on (call back from Recents / Call log), else the ★ default.
- An account that isn't registered is shown as such everywhere it appears, with
  the reason in plain words and Retry (guide §7). Never hide it.
- Name numbers by the user's label ("Office") first, the extension or number second.
- **"Phone (SIM)" is one more entry in the same list** (PLAN §5.2), not a SIP
  account. Calling from it only fires `ACTION_DIAL` with a `tel:` URI on key UP,
  and stops there. Never use `ACTION_CALL`, never request `CALL_PHONE`, and
  never try to answer, track or log SIM calls. BusyCall is **not** the default
  phone app.

## 6a. Setup codes (PLAN §5.1)

- Every setup-code format is parsed in `numbers/setup/SetupCodeParser`, and
  nowhere else. A new format means a new parser branch plus a unit test with a
  real sample (passwords replaced).
- The parser returns the fields only; nothing touches the backend until the
  user presses **Save and connect** on "Setup code found".
- Never log a setup code, URL or field from it. Clear the clipboard after
  reading a pasted code. Never save camera frames.
- Provisioning URLs: **HTTPS only**, 10 s timeout, max 64 KB, shown as
  "Fetching setup from <host>…" with Retry on failure.
- The scan screen is our own Activity (D-pad: OK = rescan, BACK = cancel), not
  a third-party scanner app, and not Google ML Kit.
- **Scan QR setup code** always asks first: "Do you have a QR setup code
  ready?" (`AlertDialog.setItems`, guide §2): **Yes, scan it** / **No, help me
  set it up**. "No" opens Make a setup code (PLAN S7c), never the camera.
- The setup page in `assets/` is generated from `tools/setup-code/` by a Gradle
  task. Never hand-edit the asset copy.
- Saving to Download: MediaStore on API 29+, legacy path with
  `WRITE_EXTERNAL_STORAGE maxSdkVersion=28` below that. Replace an existing
  `busycall-setup.html`; show the exact saved path on screen.
- Hide "Scan QR setup code" on phones without a camera
  (`FEATURE_CAMERA_ANY`), and declare the camera `required="false"` (guide §5).

## 7. Contacts

- Read the phone's contacts through upstream's `Contact.kt` (via `bridge/`).
  Ask for `READ_CONTACTS` at first use, with a toast giving the D-pad path if
  it's refused (guide §5). Without permission, the app still works with typed
  numbers and shows "Contacts are off — Options › Allow contacts".
- Match incoming numbers to contacts for the caller name.
- The Contacts list is short-to-medium and scrolled with the D-pad; 2–9 jump to
  the first name starting with that key's letters.

## 8. Calls, audio and the phone

Mostly upstream's job. What the UI must do:

- Incoming call: full-screen intent over a closed flip and the lock screen
  (guide §5 `USE_FULL_SCREEN_INTENT`); post a one-line outer-display card on
  Kyocera ("Anna Becker · Office").
- Don't show a ringing screen for a call that already ended; show it as missed.
- Earpiece by default. Speaker is a button, an Options item and the outer
  Speaker key.
- `configChanges` for the flip (guide §4): opening or closing it never
  recreates a call screen.
- Screen off during a call by timeout; many phones have no proximity sensor.

## 9. Screen and text rules

The guide §4 applies in full: 240×320 mdpi (and 320×240 landscape), body 16 sp,
labels 14 sp, metadata 11 sp, headers 18 sp, ≥ 7:1 contrast, dark background,
`minHeight` rows, one-line ellipsized labels, `isFakeBoldText` for bold, ≤ 150 ms
alpha-only animation.

BusyCall additions:

- Call/Answer = green, Decline/End = red, **always with a text label too**.
- **Dark red theme.** Title and softkey bars `#6E0A12` with white text; background
  near-black `#0B0B0C`; focus = 2 dp ring `#FF6B6B` over a `#6E0A12` tint (the
  guide's `list_row_selector`, recolored); accent text `#FF8F8F`, `#FFB4B4`;
  footer hints `#FFD6D6`. Names live in `colors.xml` (`bar`, `focus_ring`,
  `focus_tint`, `accent`, `accent_soft`), never raw hex in layouts.
- Decline/End use a **brighter** red (`#C62828`) than the bars, always with an
  icon and a label, so they never read as part of the chrome.
- All strings in `strings.xml` with translator comments; placeholders, not
  concatenation.

## 10. Privacy and logging

- **Never log** passwords, SIP auth headers, full phone numbers, contact names or
  DTMF digits (they may be PINs). Mask numbers as `***1234`.
- Upstream has its own logging: keep its debug logging off in release builds, and
  check any upstream log line you touch.
- No analytics, no third-party crash reporting.

## 11. Testing — what your PR must include

| You changed | You must add |
|---|---|
| A ViewModel | JUnit test with `FakeBridge`, covering every branch |
| A layout | Screenshot at 240×320, normal and largest font scale |
| Keys, focus or a screen | Key-only traversal test **and** touch-only test |
| `keys/KeyRouter` | Unit test for the new code, naming the phone |
| `bridge/` | Test against a fake backend. Never a real PBX in CI. |
| An upstream file | Entry in `UPSTREAM.md`; the fork still builds and S1's smoke test passes |
| Anything | Lint, format and unit tests green |

Manual hardware tests (`PLAN.md §7`) go in the PR: which phone, which PBX, what passed.
Test on a 240×320 mdpi emulator with touch off, then on the real phone (guide checklist).

## 12. Definition of done

- [ ] Acceptance criteria met and restated in the PR.
- [ ] Works with keys only (traversal test) and touch only (touch test).
- [ ] Uses no softkey but Options.
- [ ] The guide's new-app checklist still holds.
- [ ] No calling code of our own; upstream edits recorded in `UPSTREAM.md`.
- [ ] `docs/UX-SPEC.md` updated if a screen, key or label changed.
- [ ] No TODO without an issue number; no unrelated formatting changes.

## 13. Things agents get wrong here (read twice)

- **Editing upstream to make the UI easier.** Adapt in `bridge/` instead.
- **Rebuilding upstream screens in Compose** because the code is right there.
  Views only.
- **Using the non-Options softkey "just this once."**
- **Asking before moving Options left.** The first MENU press moves it silently
  and also opens Options. No dialog.
- **Assuming END reaches the app.** It often doesn't. Keep the on-screen End.
- **Consuming END** or bringing the app back after it. Hang up, then let the
  system do what END does (usually Home).
- **Leaving the app after the on-screen End call button.** Only the END key
  leaves; the button returns to the previous screen.
- **Hanging up on BACK.**
- **Forgetting which number.** Every call screen and log row names it.
- **Hiding the navigation bar on touch phones**, or showing the dial pad on
  keypad phones.
- **Silent failures.** Every failure gets plain words and Retry.

## 14. When you're unsure

Stop and ask, with a specific question and a proposed default. Don't guess at:
a new screen, a key's meaning, a change to an upstream file, a new dependency,
or anything touching passwords or encryption.
