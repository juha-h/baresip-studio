//
// Created by Administrator on 2025/9/19.
//

#ifndef BARESIP_STUDIO_ANDROID_CAMERA2_H
#define BARESIP_STUDIO_ANDROID_CAMERA2_H

#include <jni.h>
#include <re.h>
#include <rem.h>
#include <baresip.h>

struct vidsrc_st
{
    struct vidframe *frame;
//    thrd_t thread;
//    RE_ATOMIC bool run;
    uint64_t ts;
    double fps;
    vidsrc_frame_h *frameh;
    const struct vidsz *size;
    void *arg;
    int fmt;
    int rotate;
    jobject jcam;
    size_t frameSize;
    void *buf;

    uint8_t *rotate_buf;
    size_t rotate_buf_size;
};

extern struct vidsrc *vid_android_camera2;

extern int android_camera2_alloc(struct vidsrc_st **stp, const struct vidsrc *vs,
        struct vidsrc_prm *prm, const struct vidsz *size, const char *fmt, const char *dev,
        vidsrc_frame_h *frameh, vidsrc_packet_h *packeth, vidsrc_error_h *errorh, void *arg);

#endif //BARESIP_STUDIO_ANDROID_CAMERA2_H
