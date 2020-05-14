This branch is a work-in-progress attempt to include video calling support to baresip Android app.

Static libraries and include files need to be generated to distribution directory using video branch of https://github.com/juha-h/libbaresip-android.  Video codecs and support of android_camera input device are provided by FFmpeg libraries and need at least Android API level 24. Encoding of H.264 video is based on x264 library, which is free to use under GNU GPL.

Video calling is possible only on devices that include Camera2 API at hardware support level LEVEL_3 or FULL.

Copyright (c) 2020 TutPro Inc. Distributed under GNU GPL license.
