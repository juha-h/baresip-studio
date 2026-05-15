This is a bare-bones Android Studio project implementing <a href="https://github.com/alfredh/baresip">baresip</a> based SIP User Agent for Android. Its development is motivated by need for a secure, privacy focused SIP user agent for Android that does not depend on third party push notification services.

Currently the application supports VoIP calling and messaging, VoIP conference calls, UDP, TCP, TLS, and WSS signaling transports, voicemail Message Waiting Indication, call transfers (REFER), AMR, Codec2, G.722, G.722.1, G.729, Opus, and PCMU/PCMA voice codecs, as well as ZRTP and (DTLS) SRTP media encapsulation. Minimum supported Android version is 9 (API level 28).

Recent versions of baresip also support mobile callig when baresip selected as the default phone app in Android settings.  This is possible when Android version is 10 or higher.

If you need video calling and have a device that supports Camera2 API, you can instead of this application install its sister application baresip+ from video branch.

After cloning the project, generate static libraries and include files to distribution directory using master branch of <a href="https://github.com/juha-h/libbaresip-android">libbaresip-android</a>.

Then in Android Studio (tested with Android Studio Panda 2 | 2025.3.2):

- Open an existing Android Studio project

- File -> Invalidate Caches ... -> Invalidate & Restart

- Build -> Generate Signed Bundle / APK ...

Ready to be installed baresip app is available from <a href="https://f-droid.org/app/com.tutpro.baresip">F-Droid</a>, <a href="https://play.google.com/store/apps/details?id=com.tutpro.baresip">Play Store</a>, and from <a href="https://github.com/juha-h/baresip-studio/releases">GitHub</a>.  Signing certificate SHA-256 fingerprint of baresip and baresip+ APKs on GitHub is `fecc79c20ab725b9b78bb16a75ba9a04092822cf52ba32e4a437170a68020602`. Use `apksigner verify  --print-certs app-release.apk` to verify.

Language translations are managed via baresip <a href="https://hosted.weblate.org/projects/baresip/">Weblate</a> project.

Copyright (c) 2018 TutPro Inc. Distributed under BSD-3-Clause license.
