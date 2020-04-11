This branch is a work-in-progress attempt to include video calling support to baresip Android app.  Currently video can be sent from device's camera to peer, but not displayed from peer on device's screen.

Static libraries and include files need to be generated to distribution directory using video branch of https://github.com/juha-h/libbaresip-android.  Video codecs and support of android_camera input device are provided by FFmpeg libraries and need at least Android API level 24. Encoding of H.264 video is based on x264 library, which is free to use under GNU GPL.

Copyright (c) 2020 TutPro Inc. Distributed under GNU GPL license.
