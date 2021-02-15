This is a bare-bones Android Studio project implementing <a href="https://github.com/alfredh/baresip">baresip</a> based SIP User Agent.

Currently the application supports voice calling and messaging, UDP, TCP, and TLS signaling transports, voicemail Message Waiting Indication, incoming call transfers (REFER), PCMU/PCMA, iLBC, G.722, G.722.1, G.726, G.729, AMR, and Opus voice codecs, as well as ZRTP and (DTLS) SRTP media encapsulation.

Motivated by need for a secure SIP user agent for Android that does not depend on third party push notification services.

If you need video calling and have a device with Android version 7.0 or newer that supports Camera2 API at LEVEL3, you can instead of this application install its sister application baresip+ from video branch.

After cloning the project, generate static libraries and include files to distribution directory using master branch of <a href="https://github.com/juha-h/libbaresip-android">libbaresip-android</a>.

Then in Android Studio 4.1.2:

- Open an existing Android Studio project

- File -> Invalidate Caches / Restart -> Invalidate & Restart

Available also on <a href="https://f-droid.org/app/com.tutpro.baresip">F-Droid</a> and <a href="https://play.google.com/store/apps/details?id=com.tutpro.baresip&hl=en_US">Google Play</a>.

Copyright (c) 2018 TutPro Inc. Distributed under BSD-3-Clause license.
