//
// Created by Administrator on 2025/10/4.
//

#ifndef BARESIP_STUDIO_CODEC_H
#define BARESIP_STUDIO_CODEC_H

#include <re.h>
#include <rem.h>
#include <baresip.h>
#include "android_mediacodec.h"
#include "x264.h"

int codec_module_init(void);

int codec_module_close(void);

#endif //BARESIP_STUDIO_CODEC_H
