//
// Created by Administrator on 2025/10/4.
//
#include "codec.h"

int codec_module_init(void)
{
    // Is it possible to do the processing of mediacodec on the device?
    vidcodec_register(baresip_vidcodecl(), &mediacodec_h264);
    vidcodec_register(baresip_vidcodecl(), &mediacodec_h265);
    return 0;
}

int codec_module_close(void)
{
    vidcodec_unregister(&mediacodec_h264);
    vidcodec_unregister(&mediacodec_h265);
    return 0;
}