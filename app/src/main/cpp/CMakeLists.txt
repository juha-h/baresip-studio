
cmake_minimum_required(VERSION 3.4.1)

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

add_library(lib_rem STATIC IMPORTED)
set_target_properties(lib_rem PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/rem/lib/${ANDROID_ABI}/librem.a)

add_library(lib_opus STATIC IMPORTED)
set_target_properties(lib_opus PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/opus/lib/${ANDROID_ABI}/libopus.a)

add_library(lib_spandsp STATIC IMPORTED)
set_target_properties(lib_spandsp PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/spandsp/lib/${ANDROID_ABI}/libspandsp.a)

add_library(lib_g722_1 STATIC IMPORTED)
set_target_properties(lib_g722_1 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g7221/lib/${ANDROID_ABI}/libg722_1.a)

add_library(lib_g729 STATIC IMPORTED)
set_target_properties(lib_g729 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g729/lib/${ANDROID_ABI}/libbcg729.a)

add_library(lib_amrnb STATIC IMPORTED)
set_target_properties(lib_amrnb PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrnb.a)

add_library(lib_amrwb STATIC IMPORTED)
set_target_properties(lib_amrwb PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrwb.a)

add_library(lib_amrwbenc STATIC IMPORTED)
set_target_properties(lib_amrwbenc PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrwbenc.a)

add_library(lib_webrtc STATIC IMPORTED)
set_target_properties(lib_webrtc PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/webrtc/lib/${ANDROID_ABI}/libwebrtc.a)

add_library(lib_bn STATIC IMPORTED)
set_target_properties(lib_bn PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/bn/lib/${ANDROID_ABI}/libbn.a)

add_library(lib_zrtp STATIC IMPORTED)
set_target_properties(lib_zrtp PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/zrtp/lib/${ANDROID_ABI}/libzrtp.a)

add_library(lib_baresip STATIC IMPORTED)
set_target_properties(lib_baresip PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/baresip/lib/${ANDROID_ABI}/libbaresip.a)

add_library(baresip SHARED baresip.c)

target_include_directories(baresip PRIVATE
        ${distribution_DIR}/openssl/include
        ${distribution_DIR}/re/include
        ${distribution_DIR}/rem/include
        ${distribution_DIR}/baresip/include)

target_link_libraries(baresip
        android
        OpenSLES
        lib_baresip
        lib_rem
        lib_re
        lib_ssl
        lib_crypto
        lib_opus
        lib_spandsp
        lib_g722_1
        lib_g729
        lib_amrnb
        lib_amrwb
        lib_amrwbenc
        lib_webrtc
        lib_zrtp
        lib_bn
        z
        log)
