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
| _(none yet)_ | | | |

## Planned first changes (spike S1)

- `app/build.gradle.kts`: `applicationId` → `com.benshowalter.sipphone` so it
  installs next to the real baresip app and never collides with it. `namespace`
  stays `com.tutpro.baresip` so upstream code keeps compiling and merging.
- `app/build.gradle.kts`: `minSdk` 28 → 24, then guard every API 26–28 call
  that breaks.

## Merging a new upstream release

1. `git fetch https://github.com/juha-h/baresip-studio master`
2. Merge into a branch, resolve conflicts (most should be in upstream UI files we
   deleted: keep them deleted).
3. Update the "Current base" table above.
4. Run the S1 smoke test on a real phone before merging to `master`.
