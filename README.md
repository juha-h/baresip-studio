This branch adds video calling capability to baresip app and provides its sister app called baresip+. Currently AV1, H.264, H.265, VP8, and VP9 video codecs are supported.

Static libraries and include files need to be generated to distribution directory using video branch of <a href="https://github.com/juha-h/libbaresip-android">libbaresip-android</a>.  Video codecs and support of android_camera input device are provided by FFmpeg libraries and need at least Android API level 28.

Video calling is possible on devices that include Camera2 API at hardware support level LIMITED or higher.

Ready to be installed baresip+ app is available from <a href="https://f-droid.org/en/packages/com.tutpro.baresip.plus">F-Droid</a> and from <a href="https://github.com/juha-h/baresip-studio/releases">GitHub</a>.

Copyright (c) 2020 TutPro Inc. Distributed under GNU GPL license.
