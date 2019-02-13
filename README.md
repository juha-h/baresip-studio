This is very basic Android Studio project implementing
<a href="https://github.com/alfredh/baresip">baresip</a> based SIP User Agent.

<a href="https://f-droid.org/app/com.tutpro.baresip"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="100"></a> <a href='https://play.google.com/store/apps/details?id=com.tutpro.baresip'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height="100"/></a> 

Currently supports voice calls and messages with TLS transport,
voicemail MWI, incoming call transfer requests (REFER), PCMU/PCMA and
opus voice codecs, as well as ZRTP and (DTLS) SRTP media encapsulation.

Motivated by need for secure SIP user agent for Android that does not
depend on third party push notification services.

## How to build
The static libraries and include files in distribution directory have
been produced using <a
href="https://github.com/juha-h/libbaresip-android">
libbaresip-android</a>.
Before making libbaresip, apply reg.c-patch to re/src/sipreg/reg.c.  The
patch is needed due to re timer sometimes firing too late.

After cloning the project, in Android Studio 3.2.1:

- Open an existing Android Studio project

- File -> Invalidate Caches / Restart -> Invalidate & Restart

## License
Copyright (c) 2018 TutPro Inc. Distributed under BSD-3-Clause license.
