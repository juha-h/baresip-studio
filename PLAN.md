# BusyCall — plan

> Working name. A SIP softphone for keypad ("dumb") phones that also works on
> touchscreen phones. First target phone system: **Grandstream UCM**. Second:
> every other standards-compliant SIP system.
>
> **All calling and SIP work comes from [baresip-studio](https://github.com/juha-h/baresip-studio).
> We build only the UI.**

This is the *why* and the *what*. `AGENTS.md` is the *how*. Device rules come
from the [Flip-DumbPhoneGuide](https://github.com/Ben-Showalter/Flip-DumbPhoneGuide/blob/main/AGENTS.md);
the few places BusyCall deliberately differs are listed in §3.5.

---

## 1. The problem

Keypad Android phones (Kyocera E4810 / E4811 / E4610, Sonim XP series, and
similar) can't run the usual mobile softphones well:

- **Grandstream Wave, Linphone, Zoiper, and baresip's own app are built for
  touch.** Parts of them can't be reached with a D-pad.
- **Most depend on Google push notifications** to ring, and these phones have
  no Google Play Services. (baresip is the exception: it doesn't, which is one
  reason we build on it.)
- **They ignore the phone's real CALL and END keys.**

Office and school phone systems (Grandstream UCM first) want these phones to
work as extensions, often with **more than one number per phone** (an office
extension plus a personal line). Today they can't.

## 2. Goals

| # | Goal | How we check it |
|---|------|-----------------|
| G1 | **Works on any keypad phone.** D-pad, number keys, CALL, END and **one** softkey (Options). No second softkey, no touch needed. | Key-only traversal test on every screen; hardware test on at least 3 different keypad phones |
| G2 | **Works on touch phones too.** Every action has an on-screen button; a phone with no keypad gets an on-screen dial pad. | Touch-only walkthrough on a normal smartphone emulator |
| G3 | **The phone's keys do what they look like they do.** Number keys dial, CALL places or answers, END hangs up or declines. | Traversal test, plus a manual check on hardware (risk R1) |
| G4 | **Several numbers on one phone.** Any mix of SIP accounts (e.g. a UCM extension and a hosted SIP number), all registered at once. Each call shows which number it is on. | Two accounts on two different systems ring the same phone; outgoing calls go out on the chosen one |
| G5 | **Uses the phone's own contacts.** Calling a contact works on any of its numbers. Incoming calls show the contact's name. | Contacts screen lists the phone's contacts; caller name shown on an incoming call |
| G6 | **Registers to a Grandstream UCM as a normal SIP extension**, with no Grandstream-specific code | Interop checklist §7 passes on a UCM63xx |
| G7 | **Works with other SIP systems** using the same settings: server, extension, password, transport | Checklist §7 passes on Asterisk/FreePBX and FreeSWITCH |
| G8 | **Rings reliably without Google services** | Incoming call rings within 3 s, screen off, flip closed, after 1 h idle |
| G9 | **Readable on a 2.4–2.8″ 240×320 screen** | The guide's type floor: body 16 sp, labels 14 sp, metadata 11 sp, headers 18 sp; contrast ≥ 7:1 |
| G10 | **No calling code of our own** | All SIP, audio, accounts, call state and Telecom integration come from baresip-studio's backend (§4); our code only calls it |

### Non-goals for v1

Video · chat/SMS over SIP (baresip supports it; we don't show it in v1) ·
conference calls on the phone · call recording · cloud contact sync · Google
Play distribution · iOS · a custom T9 keyboard (the phone's own keyboard is
used; see the guide §3 option a).

## 3. Key and button model

The core of the product. It works the same on every screen.

### 3.1 Physical keys

| Key | Idle / lists | Incoming call | In a call |
|---|---|---|---|
| **0–9, `*`, `#`** | Open the Dialer with that digit typed (on key **UP**, per the guide). In the Dialer, digits match numbers **and** names (§3.3). | — | Send keypad tones (DTMF) |
| Hold **0** (timed by us, 700 ms) | Types `+` | — | — |
| **CALL** | Call the focused contact or recent, or the typed number. Empty Dialer: redial. | **Answer** | — |
| **END** (`KEYCODE_ENDCALL`) | Leave to the system (usually Home) | **Decline**, then the system's own END action | **Hang up**, then the system's own END action (usually leaves the app). See §3.4. |
| **Options softkey** / **MENU** | Open this screen's Options menu | Options: Silence | Options: Speaker, Mute, Hold, Transfer, Keypad, Hang up |
| **The other softkey** | **Nothing.** No label, no job. | — | — |
| **D-pad ↑ ↓** | Move focus | Move between Answer / Decline | Move between buttons |
| **D-pad ← →** | On the "Call from" row: change number. Elsewhere: between side-by-side buttons only. | Same | Same |
| **OK** (`DPAD_CENTER` or `ENTER`) | Activate the focused item | Activate focused button | Activate focused button |
| **BACK / Clear** (Clear sends `BACK` on Kyocera) | Delete a typed digit; at an empty field or a list, go back | Silence ringer (**never** declines) | Leave the screen; **the call keeps running** |
| **Outer END** (Kyocera scan code 764 / 172) | — | Decline | Hang up |
| **Outer Speaker** (scan code 765 / 213) | — | — | Toggle speaker |
| **Headset button** (`HEADSETHOOK` / `MEDIA_PLAY_PAUSE`) | — | Answer | Hang up |
| Volume keys | System volume | Silence ringer | Call volume |

**Which side Options is on (automatic, as in the guide §1):**

- New install: "Options" is drawn on the **right**, and the right softkey
  (`SOFT_RIGHT`) opens it.
- Sonim phones (XP3800 and others) have a **MENU key on the left**. The **first
  `KEYCODE_MENU` press** opens Options **and** moves the "Options" label to the
  left on every screen, with no question asked. That first press already counts
  (the guide: "map the key that triggered the change with the new layout").
- Saved for good: once MENU has been seen, Options stays on the left, even if the
  phone also sends `SOFT_RIGHT` (otherwise a phone that sends both would flip
  every press).
- MENU **always** opens Options, whichever side the label is on. BACK is never
  part of this.
- Settings › Advanced › "Options label on the left" can change it by hand.

### 3.2 On-screen buttons (touch, and a fallback for keys)

Every action reachable by a key also has an on-screen button that is focusable
**and** tappable.

- **Dialer:** a "Call from" row (◀ number ▶), a green **Call** button. A 3×4 on-screen
  dial pad appears only on phones with **no hardware number keys**.
- **Incoming call:** **Answer** (green) and **Decline** (red), full width.
- **In a call:** **End call** (red), plus **Speaker / Mute / Keypad**.
- **Footer:** the "Options" label is itself a tappable button.

### 3.3 Finding a contact while dialing

The Dialer's field is "Number or name". A dropdown under it lists up to 3
matching contacts as you type. ▼ moves into the list, OK or CALL calls the
highlighted one, and the matched part is underlined.

- **The number keys do both, always.** There is **no letter-typing mode** and
  no keyboard. The field only ever holds digits, `*`, `#` and `+`. Typing
  `2-6-6` matches numbers containing "266" **and** names whose letters are on
  those keys (A**nn**a, **Con**nor: 2=ABC, 3=DEF, 4=GHI, 5=JKL, 6=MNO,
  7=PQRS, 8=TUV, 9=WXYZ). This is the classic keypad "smart dial".
- A name matches when the keys spell the start of the first name, the last
  name, or any later word ("Becker" matches `2-3-2`). Accented letters match
  their plain letter (É on 3).
- Pressing CALL with no match highlighted dials the digits as typed, so a
  number that happens to spell a name still dials normally.
- Matches come from the phone's contacts plus the call log. Each of a
  contact's numbers is its own row ("Anna Becker · mobile", "Anna Becker · work").
- Matching runs off the main thread on a cached index of contacts (the guide §6),
  so typing never stutters on a Snapdragon 215.
- Touch phones: the same dropdown above the on-screen keypad, which shows the
  letters under each digit. Same matching, no keyboard.

### 3.4 END: end the call, then let the system act

END behaves like END in the phone's own dialer:

1. **During a call or ringing:** BusyCall hangs up (or declines) first.
2. **Then the key is passed on** (`return super...`, never consumed), so the
   system does whatever END normally does on that phone, usually closing the
   app and going to Home. We don't fight it or bring the app back.
3. **Outside a call:** BusyCall doesn't touch END at all.

On-screen **End call** and Options › **Hang up** end the call and **stay** in the
app (back to the screen before the call), since those aren't END.

### 3.5 Where BusyCall deliberately differs from the guide

| Guide says | BusyCall does | Why |
|---|---|---|
| Left softkey = the screen's primary action | **Only one softkey is used: Options.** It starts on the right and moves left automatically on phones with a MENU key (Sonim). The other softkey does nothing. The primary action is CALL / OK / an on-screen button. | Owner's decision: works on phones whose left softkey is missing, mapped to MENU, or taken by the T9 keyboard |
| Hide the system navigation bar on every screen | Hide it on **keypad** phones only. Leave it on **touch-only** phones. | On a touch phone it's the only Back button |
| "Unhandled CALL/END must reach `super`" | **CALL** is handled (consumed) on our Dialer, lists and call screens. **END is never consumed:** we end the call, then pass it to `super` so the system still does its usual thing | Answer/place calls is the app's job; END should feel exactly like the phone's own dialer |

Everything else in the guide applies as written.

## 4. Technical choices

### 4.1 The backend: a fork of baresip-studio

baresip-studio is an app, not a library, so we **fork it and replace its UI**:

| Keep (backend, upstream code) | Replace (our UI) |
|---|---|
| `libbaresip-android` native libraries (built from juha-h/libbaresip-android into `distribution/`) | Every Compose `*Screen.kt`, `MainActivity`, `CustomElements`, `Theme`, `Colors` |
| `Api.kt` (JNI), `BaresipService.kt` (registration, calls, audio, notifications), `UserAgent`, `Account`, `Call`, `CallHistory`, `Contact` (reads Android contacts), `Config`, `Codec`, `ConnectionService`, `InCallService`, `BootCompletedReceiver` | — |
| — | New Android-Views screens in our own package (§5) |

What we found in the upstream code (Nov 2026, v94.1.5):

- **Multiple accounts are built in.** The service holds a list of `UserAgent`s,
  each with its own `Account`, registered at the same time. G4 costs us UI only.
- **Phone contacts are built in.** `Contact.kt` already reads Android contacts
  (`ContactsContract`). G5 costs us UI only.
- **It already uses a self-managed `ConnectionService`**, so cell-call priority
  and the system's in-call state come with it.
- **No Google push.** It keeps registrations alive from its own foreground service.
- **`minSdk` stays 28 (Android 9), as upstream (owner's decision,
  2026-11-04).** Going lower needs a non-Telecom call path and OpenSL ES audio
  (`docs/SPIKES.md` S1), so Android 7 phones such as the Kyocera E4610 are not
  supported for now.
- **The backend holds some state in Compose runtime types** (`mutableStateOf` in
  `BaresipService` and `Call`). Keep the `compose-runtime` dependency (state only,
  no Compose UI) so upstream merges stay easy. Our ViewModels read it through a
  small adapter that turns it into `StateFlow`s.
- **License: BSD-3-Clause.** We may use any license for BusyCall. We must keep
  TutPro's copyright notice and license text (in the repo and the About screen).

**Staying close to upstream is a goal.** Backend changes are kept small, each
listed in `UPSTREAM.md`, and offered back upstream where they make sense.

### 4.2 Everything else

| Area | Choice | Why |
|---|---|---|
| UI | Kotlin, **Android Views** | The guide §4; FlipWeather and MatChat do the same |
| Android versions | `minSdk 28` (Android 9), as upstream; `targetSdk` and build settings as upstream | Android 7 needs backend work (SPIKES S1); not worth it yet |
| Codecs | G.711 µ-law/a-law, G.722, Opus. No G.729 by default. | Every PBX supports the first three |
| Security | TLS + SRTP when the PBX supports them | UCM supports TLS |
| Updates | Self-update from GitHub Releases, FlipWeather's `UpdateChecker` | The guide §7 |
| Naming | The visible name is a **working name** and may change. The Android `applicationId` is **`com.busycall.app`** and **never changes**, even if the visible name does. The name lives only in `strings.xml` (`app_name`) and `AppIdentity.kt` (link scheme, setup-file name, update repo). Old link schemes stay accepted forever. | Changing the `applicationId` after install forces every phone to uninstall and lose its settings |
| Colors | **Dark red** title and softkey bars (`#6E0A12`, white text 12:1); near-black background; focus = light-red ring `#FF6B6B` on a dark-red tint; accents `#FF8F8F` / `#FFB4B4`. Call/Answer green, Decline/End a brighter red `#C62828`, always with a label. | Owner's choice; all text passes the guide's 7:1 / 4.5:1 |
| QR decoding | ZXing core (Apache-2.0), the decoder only; no Google ML Kit | Works offline, no Play Services |
| Outer display (Kyocera) | Incoming call card: caller name + number called, one line | The guide §4 outer-display rules |

## 5. Screens

Each screen fits about one 240×320 screen. Previews:
**[BusyCall screen previews](https://claude.ai/artifact/QRtWmnoJU3WSiBCMCRoSai)**.

| # | Screen | Contents | Options menu |
|---|---|---|---|
| S1 | **Home** | "My numbers" with live status and voicemail count each; Recent calls (each shows which number it was on). A digit jumps to S2. | Contacts · Call log · Voicemail… · Numbers · Settings |
| S2 | **Dialer** | "Number or name" field (digits only; the keys' letters find names), dropdown of up to 3 contact matches (§3.3), "from <number>" row (◀ ▶), Call button; on-screen pad on touch-only phones | Add to contacts · Call from… |
| S3 | **Contacts** | The phone's contacts, scrolled with the D-pad; 2–9 jumps to a letter; OK shows all of a contact's numbers | Call · Call from… · Open in phone's Contacts |
| S4 | **Incoming call** | "to <number>", caller name/number, Answer and Decline. Full-screen over a closed flip and the lock screen. | Silence |
| S5 | **In call** | Timer, "from <number>", name, audio route, Speaker / Mute / Keypad, End call | Speaker · Mute · Hold · Transfer · Keypad · Hang up |
| S6 | **Call log** | All calls; missed in red; which number each was on | Call back · Call from… · Add to contacts · Delete |
| S7 | **Numbers** | Every account with status; ★ marks the default for the Dialer; "+ Add a number" opens a menu: **Type it in** (S8) · **Paste setup link** · **Scan QR setup code** (§5.1) | Make default · Edit · Turn off · Delete |
| S7b | **Scan: code ready?** | Dialog on choosing Scan QR setup code: "Do you have a QR setup code ready?" · **Yes, scan it** (opens the scanner) · **No, help me set it up** (S7c) | — |
| S7c | **Make a setup code** | Step 1: **Save setup page to phone** button + result line; steps 2–4: "Move busycall-setup.html to a computer over USB, Bluetooth, or SD card", open it and fill it in, then come back and scan (§5.1) | Save again · Save to SD card · Send by Bluetooth |
| S7a | **Setup code found** | After a paste or scan: Name, Server, Extension · Password (hidden), Connection; OK on a line edits it; **Save and connect** | — |
| S8 | **Number setup** (Type it in) | Name (e.g. "Office"), Server, Extension, Auth ID, Password, Transport, "Test connection"; a "More options…" row (the guide §3: Options may not work while typing) | Advanced (codecs, DTMF mode, voicemail number) |
| S9 | **Settings** | Audio · Advanced (Options on left, check for updates) · About (licenses) | — |
| S10 | **Problem** | Plain-language reason ("Wrong extension or password", "Can't reach pbx.example.com") and Retry | — |

### 5.1 Setup codes: paste a link or scan a QR code

Typing a server name and password on a keypad is slow and error-prone (the
guide: "Never make the user type an API key"). So a number can arrive as a
**setup code**: the same text either pasted as a link or scanned as a QR code.

**There is no standard SIP setup QR code.** Every app uses its own format
(Linphone, SessionTalk, Grandstream Wave, 3CX, Yeastar Linkus, Zoiper), so
BusyCall reads several and the parsing lives in **one** place
(`numbers/setup/SetupCodeParser`):

| Format | Looks like | Where it comes from |
|---|---|---|
| **BusyCall link** (ours, preferred) | `busycall:add?v=1&name=Office&server=pbx.school.org&port=5061&user=104&auth=…&pass=…&transport=udp\|tcp\|tls&vm=*97`, every value URL-encoded, empty fields left out; only `v`, `server`, `user` required. Later also `https://<host>/busycall#add?…` with the same fields (see below). | The setup-code page (below) |
| **baresip account line** | `<sip:104@pbx.school.org;transport=tls>;auth_pass=…` | The backend's own format, so admins who already use baresip can reuse it |
| **SIP URI with password** | `sip:104:secret@pbx.school.org;transport=tls` | Some PBX tools; old but common |
| **Linphone-style provisioning URL** | `https://…/provisioning.xml`: fetch (HTTPS only), read Linphone's XML (`proxy_0`, `auth_info_0`) | Systems that provision Linphone |
| **Grandstream UCM QR** | Unknown: reportedly a provisioning URL to a Grandstream-format file | Read it only if spike S6 shows the format is usable |

Anything else gets a plain message: "This code isn't a BusyCall setup code.
Ask your admin for a BusyCall code."

- **Paste setup link** reads the clipboard. If it's empty: "Nothing copied yet:
  copy the setup link from your text or email, then try again."
- **Opening the link** from a text or email (`busycall:` scheme or the `#add`
  link) opens BusyCall straight to "Setup code found".
- **Scan QR setup code** opens the camera in our own screen (the camera key
  is swallowed by the OS; this is reached from the menu). Uses **ZXing core**
  (Apache-2.0, offline, no Google services) for decoding. Phones with no camera
  don't show this item.
- Nothing is saved until the user presses **Save and connect**. The password
  is never shown, logged or kept in the clipboard (cleared after reading),
  and the camera image is never saved.
- **Setup-code page:** one self-contained HTML file,
  `tools/setup-code/busycall-setup.html`, that an admin saves and opens on their
  own PC. It works offline (the QR library, qrcode-generator 1.4.4, MIT, is
  inlined), makes no network requests, and keeps nothing after it's closed. It
  builds the `busycall:` link and its QR code, and can copy the link, save the
  QR as a PNG, or print it. It says plainly: "This code contains the password.
  Treat it like one." The source is `tools/setup-code/src/page.html`, with the
  library inlined at build time.
- **Getting the page from the phone itself** (S7b → S7c): the app ships
  `busycall-setup.html` in its `assets/`, so a user with no admin and no
  internet can still make a code. "Save setup page to phone" writes it to the
  phone's public **Download** folder:
  - API 29+: `MediaStore.Downloads` insert, no permission needed.
  - API 28: `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`, asked at the
    moment of saving with a plain reason; write to
    `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` (the
    guide §5 storage rules; the E4811 has a storage-permission bug on API 29+,
    which is why 29+ uses MediaStore).
  - An existing file of that name is replaced, not duplicated as
    `busycall-setup (1).html`, so the instructions stay true.
  - Result line on screen: "✓ Saved: Download › busycall-setup.html", or the
    failure in plain words with Retry ("Phone storage is full", "Storage
    permission was turned off: Options › Allow storage").
  - Steps on screen: "Move busycall-setup.html to a computer over USB,
    Bluetooth, or SD card", open it on the computer and fill it in, then come
    back and choose Scan QR setup code.
  - Options on that screen make the other two routes one step:
    - **Save to SD card** (shown only when a removable card is mounted): also
      writes the file to the card's root, via `getExternalFilesDirs` /
      the storage access picker as the phone allows, and shows the path.
    - **Send by Bluetooth**: shares the file with `ACTION_SEND` (via a
      `FileProvider` URI) so the phone's own Bluetooth share screen opens. If
      no app can take it (the guide: `resolveActivity()` may return the
      chooser), say "This phone can't send files by Bluetooth from BusyCall"
      instead of failing silently.
  - The page in `assets/` is built from `tools/setup-code/` at build time, so
    the app and the standalone file are always the same version.
- **Tapping a link in a text:** many flip-phone message apps only make
  `http(s)` links tappable, so a `busycall:` link may have to be copied and
  pasted (Paste setup link) rather than tapped. If tapping matters later, host
  the same page somewhere free (GitHub Pages) and let it also produce the
  `https://…/busycall#add?…` form; the settings after `#` never reach the server.

**Choosing a number for outgoing calls:** the ★ default, unless the user
changes "Call from" on the Dialer, or calls back from Recents / Call log (which
use the number the call came in on).

### 5.2 "Phone (SIM)" in the list of numbers

The phone's own SIM line appears as one more choice next to the SIP numbers:
in "Call from" (◀ ▶ on the Dialer, and Call from… in Contacts and Call log),
and as a row on the Numbers screen. It can be the ★ default.

- **Calling "from Phone (SIM)" opens the phone's own dialer** with the number
  already typed (`Intent.ACTION_DIAL`, `tel:` URI), the same way TurboText
  hands off. The user presses CALL once more there, and the call is entirely
  the phone's: BusyCall doesn't show it, time it or log it.
- No permission is needed (`ACTION_DIAL`, not `ACTION_CALL`).
- Launch on key **UP** (the guide: otherwise the UP reaches the dialer and types
  or dials again). If `resolveActivity()` returns Android's chooser or nothing,
  show "This phone has no dialer app BusyCall can open" (the guide §5).
- Shown only on phones with telephony (`FEATURE_TELEPHONY`). On the Numbers
  screen its status reads "Opens the phone's dialer"; it has no settings and
  can't be deleted, only hidden (Settings › Advanced).
- **Incoming** SIM calls still ring in the phone's dialer, not BusyCall
  (owner's decision, 2026-11-04). Making BusyCall the default phone app is out of
  scope for now.

## 6. Risks — test these on hardware first

| # | Risk | Why it matters | Spike |
|---|---|---|---|
| R1 | **END (`KEYCODE_ENDCALL`) may never reach the app** on Kyocera: the guide says it "acts as Home", which the system may do before we see the key. The user would land on Home with the call **still running**. | G3 | S2 |
| R2 | ~~CALL may open the phone's own dialer~~ **Resolved:** the owner confirmed CALL/Send reaches apps on the target phones. S2 still logs its keycode per phone. | — | S2 |
| R3 | baresip-studio's `minSdk` is 28; the fleet includes Android 7. **Code review (docs/SPIKES.md S1):** Android 8 is a small change; Android 7 needs a non-Telecom call path and OpenSL ES audio, a real backend change. | Which phones can run BusyCall at all | S1 |
| R4 | Keeping several registrations alive drains the battery or gets killed | G8 | S3 |
| R5 | Audio routing (earpiece / speaker / flip closed) differs per phone | Calls feel broken | S4 |
| R6 | One-way audio off the office network (NAT) | Remote use | S5 |

**If END doesn't reach the app (R1), in order:**
1. During a self-managed `ConnectionService` call, Android's own END handling
   asks Telecom to end the current call, which may end *ours*. Test this first;
   if it works, END already behaves as §3.4 wants with no key code of ours.
2. Use the outer END button (scan code) and the headset button.
3. **Always** keep the on-screen **End call** button and Options > **Hang up**,
   and an "On call · 03:42" notification so a call left running after END is
   one press away.

## 7. Interop checklist ("works with Grandstream and others")

Run against **Grandstream UCM63xx**, **Asterisk/FreePBX**, **FreeSWITCH**, and
one hosted SIP provider. Optional: 3CX, Yeastar.

- [ ] Registers over UDP, TCP and TLS
- [ ] **Two accounts on two different systems registered at the same time**; each rings the phone, and the screen says which number was called
- [ ] Outgoing call uses the chosen "Call from" number
- [ ] Places a call to another extension and to an outside number; two-way audio
- [ ] Receives a call; rings with the screen off and the flip closed
- [ ] Hang up from either side ends the call cleanly on both
- [ ] Keypad tones work in the PBX's voicemail menu (RFC 4733)
- [ ] Voicemail count shows per number
- [ ] Hold and resume; blind transfer
- [ ] A cell call arriving during a SIP call takes over cleanly
- [ ] Re-registers by itself after losing signal or switching Wi-Fi ↔ mobile data
- [ ] Caller name from the phone's contacts shows on an incoming call

## 8. Spikes — throwaway code, a written answer each, before UI work

1. **S1 — Fork builds and runs.** Build the fork with upstream's UI still in
   place, as `com.busycall.app`, `minSdk 28`. Install on the Android 9
   and 10 phones. Register to a UCM and make one call. (Code review of going
   below Android 9 is recorded in SPIKES S1; not pursued for now.)
2. **S2 — CALL and END keys.** On every test phone, log the CALL, END, outer END and
   headset codes, with and without a call in progress. Decides how much of G3 is possible.
3. **S3 — Ringing and battery with two accounts.** Idle 1 h, screen off: ring time
   and battery used.
4. **S4 — Audio routing.** Earpiece, speaker, the flip closed during a call.
5. **S5 — Off-network.** Mobile data to a UCM behind NAT.
6. **S6 — Camera, QR and the setup page.** On each phone: save the setup page to Download (API 28 and 29+ paths) and move it to a Windows PC and a Mac over USB, Bluetooth and SD card, noting which routes each phone supports. Can we open the camera and decode a
   QR code at arm's length with ZXing? Also scan a Grandstream UCM's Wave QR code
   and record exactly what it contains, to decide whether BusyCall can read it.

## 9. Milestones

| M | Contents | Done when |
|---|---|---|
| M0 | Fork + spikes S1–S6 in `docs/SPIKES.md` | Fork runs on the fleet; CALL/END behaviour known per phone |
| M1 | Remove upstream UI; FlipBaseActivity; Home; Numbers; Number setup; setup links + QR (§5.1) | A keypad phone shows two numbers "Registered" and stays registered overnight |
| M2 | Dialer, outgoing calls, in-call screen, CALL/END keys | Checklist items 1–6 pass on UCM |
| M3 | Incoming calls, ringing, outer display, call log | Ring items pass; G8 measured |
| M4 | Contacts, voicemail, hold, transfer | Full checklist passes on UCM |
| M5 | Other PBXs, touch-phone pass, self-update, first release | Checklist passes on Asterisk and FreeSWITCH; touch walkthrough passes |

## 10. Open questions for the owner

1. **App name** (BusyCall is a placeholder) and **license** (BSD-3 to match
   upstream is simplest).
2. **Test phones:** E4810, E4811, one Sonim (Android 9+), one touch phone?
   Phones below Android 9 (E4610, older Sonims) are out of scope for now.
3. **SMS over SIP:** baresip supports it. Leave it out of v1 (current plan)?
