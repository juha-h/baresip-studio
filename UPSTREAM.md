# Upstream tracking

This repo is built on [juha-h/baresip-studio](https://github.com/juha-h/baresip-studio)
(BSD-3-Clause, Copyright (c) 2018 TutPro Inc.). We keep its backend and replace its UI
(see `PLAN.md §4.1` and `AGENTS.md §0`).

## Current base

| | |
|---|---|
| Upstream repo | https://github.com/juha-h/baresip-studio |
| Upstream version | `94.1.5` (`app/build.gradle.kts` `versionName`) |
| Upstream commit | `6db813f10740ca835d8f7fbdca9c837315974a6e` ("Submodule update", 2026-11-03) |
| Native libraries | `libbaresip-android` submodule → https://github.com/juha-h/libbaresip-android (`master`); static libs go in `distribution/` (empty until built) |

## Changes to upstream files

Every edit to an upstream file is listed here: file, what changed, why, and the
upstream PR if one was offered. Keep each change as small as possible.

| File | Change | Why | Upstream PR |
|---|---|---|---|
| `app/build.gradle.kts` | `applicationId` `com.tutpro.baresip` → `com.busycall.app` | Installs next to the real baresip app instead of replacing it. `namespace` stays `com.tutpro.baresip` so upstream code compiles and merges unchanged. | No (fork-only) |

## Notes

- `minSdk` stays **28**, as upstream (owner's decision, 2026-11-04). See
  `docs/SPIKES.md` S1 for what going lower would take.
- Hard-coded `com.tutpro.baresip…` strings (intent actions, notification
  channel IDs, wakelock tags) are only names, so they keep working under the
  new `applicationId`. `BaresipService.onCreate` sets an `intent` with
  `setPackage("com.tutpro.baresip")`, but that field is never broadcast, so it
  is left alone. File providers already use `${applicationId}`.

## Merging a new upstream release

1. `git fetch https://github.com/juha-h/baresip-studio master`
2. Merge into a branch, resolve conflicts (most should be in upstream UI files we
   deleted: keep them deleted).
3. Update the "Current base" table above.
4. Run the S1 smoke test on a real phone before merging to `master`.
