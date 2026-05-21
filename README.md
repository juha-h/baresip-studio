This is a bare-bones Android Studio project implementing <a href="https://github.com/alfredh/baresip">baresip</a> library based SIP and mobile calling and messaging User Agent for Android. Its development is motivated by need for a secure, privacy focused User Agent that does not depend on third party push notification services.

Currently baresip supports SIP and mobile voice calls and messages.  In addition, voice conference calls, voicemail Message Waiting Indication as well as blind and attended call transfers are available when SIP is used. MMS messaging is not supported yet.

In SIP calls audio can be coded with Opus, AMR, Codec2, G.729, G.722, G.722.1, or PCMU/PCMA codecs. Security is achieved via TLS or WSS SIP signaling transport and ZRTP or (DTLS) SRTP media encapsulation.

This application can be installed on Android devices running Android version 9 or later.  Mobile calling and messaging needs Android version 10 or later.

If you need video calling and have a device that supports Camera2 API, you can instead of this application install its sister application baresip+ from the video branch.

After cloning the project, generate static libraries and include files to distribution directory using master branch of <a href="https://github.com/juha-h/libbaresip-android">libbaresip-android</a>.

Then in Android Studio (tested with Android Studio Panda 4):

- Open an existing Android Studio project

- File -> Invalidate Caches ... -> Invalidate & Restart

- Build -> Generate Signed Bundle / APK ...

Ready to be installed baresip app is available from <a href="https://f-droid.org/app/com.tutpro.baresip">F-Droid</a>, <a href="https://play.google.com/store/apps/details?id=com.tutpro.baresip">Play Store</a>, and from <a href="https://github.com/juha-h/baresip-studio/releases">GitHub</a>.  Signing certificate SHA-256 fingerprint of baresip and baresip+ APKs on GitHub is `fecc79c20ab725b9b78bb16a75ba9a04092822cf52ba32e4a437170a68020602`. Use `apksigner verify  --print-certs app-release.apk` to verify.

Language translations are managed via baresip <a href="https://hosted.weblate.org/projects/baresip/">Weblate</a> project.

Copyright (c) 2018 TutPro Inc. Distributed under BSD-3-Clause license.
