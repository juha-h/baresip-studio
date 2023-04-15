This is a bare-bones Android Studio project implementing <a href="https://github.com/alfredh/baresip">baresip</a> based SIP User Agent. Its development is motivated by need for a secure, privacy focused SIP user agent for Android that does not depend on third party push notification services.

Currently the application supports voice calling and messaging, UDP, TCP, TLS, and WSS signaling transports, voicemail Message Waiting Indication, call transfers (REFER), PCMU/PCMA, GSM, G.722, G.722.1, G.726, G.729, AMR, and Opus voice codecs, as well as ZRTP and (DTLS) SRTP media encapsulation.

If you need video calling and have a device with Android version 7.0 or newer that supports Camera2 API at LEVEL3, you can instead of this application install its sister application baresip+ from video branch.

After cloning the project, generate static libraries and include files to distribution directory using master branch of <a href="https://github.com/juha-h/libbaresip-android">libbaresip-android</a>.

Then in latest stable version of Android Studio:

- Open an existing Android Studio project

- File -> Invalidate Caches / Restart -> Invalidate & Restart

Ready to be installed baresip app is available from <a href="https://f-droid.org/app/com.tutpro.baresip">F-Droid</a> and from <a href="https://github.com/juha-h/baresip-studio/releases">GitHub</a>.  Signing certificate SHA-256 fingerprint of the GitHub APKs is FE:CC:79:C2:0A:B7:25:B9:B7:8B:B1:6A:75:BA:9A:04:09:28:22:CF:52:BA:32:E4:A4:37:17:0A:68:02:06:02.  Use `keytool -printcert -jarfile app-release.apk` to verify.

Copyright (c) 2018 TutPro Inc. Distributed under BSD-3-Clause license.
