# Building BusyCall

BusyCall builds in **Android Studio on any computer, Windows included**.

baresip's native libraries (SIP, audio, encryption: the `distribution/` folder)
can only be built on Linux or macOS, so GitHub builds them and the Android Studio
build downloads them automatically. You only build the app.

## One-time: publish the native libraries

Do this once, and again only when `native-libs.properties` changes.

1. On GitHub, open the repo's **Actions** tab.
2. Choose **Native libraries** on the left, then **Run workflow** and pick your
   branch.
3. Wait for it to finish (roughly 30–60 minutes). It creates a release named
   `native-libs-<version>` containing `distribution.zip`. It's marked
   "pre-release" and isn't an app release.

Pushing a change to `native-libs.properties` or the `libbaresip-android`
submodule starts the workflow automatically. If the release already exists, it
does nothing unless you tick **rebuild**.

## Every time: build in Android Studio

1. Clone the repo **with its submodule**, e.g. in Android Studio:
   **File › New › Project from Version Control**, URL
   `https://github.com/Ben-Showalter/BusyCall.git`, then check out your branch.
   From a terminal:
   ```
   git clone --recurse-submodules https://github.com/Ben-Showalter/BusyCall.git
   ```
2. In **Settings › Languages & Frameworks › Android SDK › SDK Tools**, tick
   **Show Package Details** and install:
   - **NDK (Side by side)** version `30.0.16248370` (must match `ndkVersion` in
     `app/build.gradle.kts`)
   - **CMake** version `3.31.6`
3. **Build › Make Project** (or Run on a connected phone).

The first build prints `Downloading baresip native libraries …` and unpacks them
into `distribution/`. Later builds skip that until the version changes.

### If the build says it couldn't download the native libraries

The release for that version doesn't exist yet: run the **Native libraries**
workflow (above), then build again.

## Building the native libraries yourself (Linux, macOS or WSL)

Not needed normally. Follow `libbaresip-android/README.md`, passing the paths on
the command line instead of editing its Makefile:

```
cd libbaresip-android
make download-sources
make debug NDK_PATH=/path/to/ndk/30.0.16248370 OUTPUT_DIR="$PWD/../distribution"
```

Then write the version from `native-libs.properties` into
`distribution/.native-libs-version`, so the Android Studio build uses your
libraries instead of downloading.

To download from somewhere other than GitHub releases, set
`busycall.nativeLibsUrl=<url of distribution.zip>` in `gradle.properties`.
