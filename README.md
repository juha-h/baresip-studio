This is very basic Android Studio project implementing
<a href="https://github.com/alfredh/baresip">baresip</a> based SIP User Agent.

Currently supports voice calls and messages with TLS transport,
voicemail MWI, incoming call transfer requests (REFER), PCMU/PCMA and
opus voice codecs, as well as ZRTP and (DTLS) SRTP media encapsulation.

Motivated by need for secure SIP user agent for Android that does not
depend on third party push notification services.

After cloning the project, generate static libraries and include files
to distribution directory using master branch of
https://github.com/juha-h/libbaresip-android.

Then in Android Studio 3.4.1:

- Open an existing Android Studio project

- File -> Invalidate Caches / Restart -> Invalidate & Restart

Available also on <a href="https://f-droid.org/app/com.tutpro.baresip">F-Droid</a> and <a href="https://play.google.com/store/apps/details?id=com.tutpro.baresip&hl=en_US">Google Play</a>.

Copyright (c) 2018 TutPro Inc. Distributed under BSD-3-Clause license.
