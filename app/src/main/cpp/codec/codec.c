//
// Created by Administrator on 2025/10/4.
//
#include "codec.h"
#include <media/NdkMediaCodec.h>
#include <stdbool.h>

static bool is_encoder_supported(const char *mime)
{
    AMediaCodec *codec = AMediaCodec_createEncoderByType(mime);
    if (codec) {
        AMediaCodec_delete(codec);
        return true;
    }
    return false;
}

static bool is_decoder_supported(const char *mime)
{
    AMediaCodec *codec = AMediaCodec_createDecoderByType(mime);
    if (codec) {
        AMediaCodec_delete(codec);
        return true;
    }
    return false;
}

int codec_module_init(void)
{
    bool h264_encoder_supported = is_encoder_supported("video/avc");
    bool h264_decoder_supported = is_decoder_supported("video/avc");
    if (!h264_encoder_supported) {
        warning("android_mediacodec: H264 encoder not supported\n");
        mediacodec_h264.encupdh = NULL;
        mediacodec_h264.ench = NULL;
        mediacodec_h264.packetizeh = NULL;
    }
    if (!h264_decoder_supported) {
        warning("android_mediacodec: H264 decoder not supported\n");
        mediacodec_h264.decupdh = NULL;
        mediacodec_h264.dech = NULL;
    }
    if (h264_encoder_supported || h264_decoder_supported) {
        warning("android_mediacodec: H264 encoder/decoder supported\n");
        vidcodec_register(baresip_vidcodecl(), &mediacodec_h264);
    }

    bool h265_encoder_supported = is_encoder_supported("video/hevc");
    bool h265_decoder_supported = is_decoder_supported("video/hevc");
    if (!h265_encoder_supported) {
        warning("android_mediacodec: H265 encoder not supported\n");
        mediacodec_h265.encupdh = NULL;
        mediacodec_h265.ench = NULL;
        mediacodec_h265.packetizeh = NULL;
    }
    if (!h265_decoder_supported) {
        warning("android_mediacodec: H265 decoder not supported\n");
        mediacodec_h265.decupdh = NULL;
        mediacodec_h265.dech = NULL;
    }
    if (h265_encoder_supported || h265_decoder_supported) {
        warning("android_mediacodec: H265 encoder/decoder supported\n");
        vidcodec_register(baresip_vidcodecl(), &mediacodec_h265);
    }
    vidcodec_register(baresip_vidcodecl(), &x264);
    return 0;
}

int codec_module_close(void)
{
    vidcodec_unregister(&mediacodec_h264);
    vidcodec_unregister(&mediacodec_h265);
    vidcodec_unregister(&x264);
    return 0;
}