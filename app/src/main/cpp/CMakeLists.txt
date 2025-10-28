project(baresip)

cmake_minimum_required(VERSION 3.4.1)

add_link_options("LINKER:--build-id=none")

set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../../distribution)

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

add_library(lib_g722 STATIC IMPORTED)
set_target_properties(lib_g722 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g722/lib/${ANDROID_ABI}/libg722.a)

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

add_library(baresip SHARED baresip.c)

target_include_directories(baresip PRIVATE
        ${distribution_DIR}/openssl/include
        ${distribution_DIR}/re/include
        ${distribution_DIR}/baresip/include)

add_definitions(-DHAVE_PTHREAD)

target_link_libraries(baresip
        android
        aaudio
        lib_baresip
        lib_re
        lib_ssl
        lib_crypto
        lib_opus
        lib_g722
        lib_g722_1
        lib_g729
        lib_codec2
        lib_amrnb
        lib_amrwb
        lib_amrwbenc
        lib_zrtpcppcore
        lib_sndfile
        z
        log)
