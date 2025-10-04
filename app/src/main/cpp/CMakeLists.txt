project(baresip+)

cmake_minimum_required(VERSION 3.4.1)

add_link_options("LINKER:--build-id=none")

set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../../distribution.video)

add_library(lib_crypto STATIC IMPORTED)
set_target_properties(lib_crypto PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/openssl/lib/${ANDROID_ABI}/libcrypto.a)

add_library(lib_ssl STATIC IMPORTED)
set_target_properties(lib_ssl PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/openssl/lib/${ANDROID_ABI}/libssl.a)

add_library(lib_re STATIC IMPORTED)
set_target_properties(lib_re PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/re/lib/${ANDROID_ABI}/libre.a)

add_library(lib_opus STATIC IMPORTED)
set_target_properties(lib_opus PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/opus/lib/${ANDROID_ABI}/libopus.a)

add_library(lib_vpx STATIC IMPORTED)
set_target_properties(lib_vpx PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/vpx/lib/${ANDROID_ABI}/libvpx.a)

add_library(lib_x264 STATIC IMPORTED)
set_target_properties(lib_x264 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/x264/lib/${ANDROID_ABI}/libx264.a)

add_library(lib_x265 STATIC IMPORTED)
set_target_properties(lib_x265 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/x265/lib/${ANDROID_ABI}/libx265.a)

add_library(lib_aom STATIC IMPORTED)
set_target_properties(lib_aom PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/aom/lib/${ANDROID_ABI}/libaom.a)

add_library(lib_png STATIC IMPORTED)
set_target_properties(lib_png PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/png/lib/${ANDROID_ABI}/libpng.a)

add_library(lib_cpu_features STATIC IMPORTED)
set_target_properties(lib_cpu_features PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/cpu_features/lib/${ANDROID_ABI}/libcpu_features.a)

add_library(lib_ndk_compat STATIC IMPORTED)
set_target_properties(lib_ndk_compat PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/cpu_features/lib/${ANDROID_ABI}/libndk_compat.a)

add_library(lib_avcodec SHARED IMPORTED)
set_target_properties(lib_avcodec PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libavcodec.so)

add_library(lib_avutil SHARED IMPORTED)
set_target_properties(lib_avutil PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libavutil.so)

add_library(lib_swresample SHARED IMPORTED)
set_target_properties(lib_swresample PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libswresample.so)

add_library(lib_avdevice SHARED IMPORTED)
set_target_properties(lib_avdevice PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libavdevice.so)

add_library(lib_avfilter SHARED IMPORTED)
set_target_properties(lib_avfilter PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libavfilter.so)

add_library(lib_swscale SHARED IMPORTED)
set_target_properties(lib_swscale PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libswscale.so)

add_library(lib_avformat SHARED IMPORTED)
set_target_properties(lib_avformat PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libavformat.so)

add_library(lib_postproc SHARED IMPORTED)
set_target_properties(lib_postproc PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ffmpeg/lib/${ANDROID_ABI}/libpostproc.so)

add_library(lib_spandsp STATIC IMPORTED)
set_target_properties(lib_spandsp PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/spandsp/lib/${ANDROID_ABI}/libspandsp.a)

add_library(lib_g722_1 STATIC IMPORTED)
set_target_properties(lib_g722_1 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g7221/lib/${ANDROID_ABI}/libg722_1.a)

add_library(lib_g729 STATIC IMPORTED)
set_target_properties(lib_g729 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g729/lib/${ANDROID_ABI}/libbcg729.a)

add_library(lib_codec2 STATIC IMPORTED)
set_target_properties(lib_codec2 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/codec2/lib/${ANDROID_ABI}/libcodec2.a)

add_library(lib_amrnb STATIC IMPORTED)
set_target_properties(lib_amrnb PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrnb.a)

add_library(lib_amrwb STATIC IMPORTED)
set_target_properties(lib_amrwb PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrwb.a)

add_library(lib_amrwbenc STATIC IMPORTED)
set_target_properties(lib_amrwbenc PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrwbenc.a)

add_library(lib_zrtpcppcore STATIC IMPORTED)
set_target_properties(lib_zrtpcppcore PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/gzrtp/lib/${ANDROID_ABI}/libzrtpcppcore.a)

add_library(lib_sndfile STATIC IMPORTED)
set_target_properties(lib_sndfile PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/sndfile/lib/${ANDROID_ABI}/libsndfile.a)

add_library(lib_baresip STATIC IMPORTED)
set_target_properties(lib_baresip PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/baresip/lib/${ANDROID_ABI}/libbaresip.a)

add_library(lib_yuv STATIC IMPORTED)
set_target_properties(lib_yuv PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/libyuv/lib/${ANDROID_ABI}/libyuv.a)

add_library(baresip SHARED baresip.c common.c vidisp.c android_camera2.c codec/codec.c codec/android_mediacodec_enc.c codec/android_mediacodec_dec.c)

#set(CMAKE_SHARED_LINKER_FLAGS
#        "${CMAKE_SHARED_LINKER_FLAGS} -u AImageReader_new")

target_include_directories(baresip PRIVATE
        ${distribution_DIR}/openssl/include
        ${distribution_DIR}/ffmpeg/include
        ${distribution_DIR}/re/include
        ${distribution_DIR}/baresip/include
        ${distribution_DIR}/libyuv/include)

add_definitions(-DHAVE_PTHREAD)

target_link_libraries(baresip
        android
        EGL
        GLESv2
        GLESv1_CM
        aaudio
        lib_yuv
        lib_baresip
        lib_re
        lib_ssl
        lib_crypto
        lib_opus
        lib_avdevice
        lib_avfilter
        lib_swscale
        lib_avformat
        lib_avcodec
        lib_x264
        lib_aom
        lib_png
        lib_vpx
        lib_avutil
        lib_swresample
        lib_postproc
        lib_spandsp
        lib_g722_1
        lib_g729
        lib_codec2
        lib_amrnb
        lib_amrwb
        lib_amrwbenc
        lib_zrtpcppcore
        lib_sndfile
        z
        log
        camera2ndk
        mediandk)
