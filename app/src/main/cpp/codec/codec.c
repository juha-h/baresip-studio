//
// Created by Administrator on 2025/10/4.
//
#include "codec.h"

int codec_module_init(void)
{
    vidcodec_register(baresip_vidcodecl(), &mediacodec_h264);
    vidcodec_register(baresip_vidcodecl(), &mediacodec_h265);
//    vidcodec_register(baresip_vidcodecl(), &x264);

    return 0;
}

int codec_module_close(void)
{
    vidcodec_unregister(&mediacodec_h264);
    vidcodec_unregister(&mediacodec_h265);
//    vidcodec_unregister(&x264);

    return 0;
}