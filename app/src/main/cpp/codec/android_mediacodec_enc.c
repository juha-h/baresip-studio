//
// Created by Administrator on 2025/10/4.
//
#include <string.h>
#include <re.h>
#include <rem.h>
#include "re_h265.h"
#include <baresip.h>
#include <jni.h>
#include <android/native_window_jni.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaCodecInfo.h>
#include <err.h>
#include "android_mediacodec.h"
#include "../logger.h"


struct videnc_state
{
    double fps;
    unsigned bitrate;
    videnc_packet_h *pkth;
    const struct video *vid;
    struct videnc_param encprm;
    char *mime;
    AMediaCodec *codec;

    int width;
    int height;
    bool codec_started;
    uint64_t first_ts;
};

static void copy_frame_to_buffer(const struct vidframe *frame, uint8_t *dst, size_t *dst_size)
{
    size_t frame_size = 0;
    int y_size = (int)frame->size.w * (int)frame->size.h;
    if (frame->fmt == VID_FMT_YUV420P) {
        int uv_size = y_size / 4; // Each U, V plane is w*h/4
        // I420 Memory Layout = Y (W*H) + U (W*H/4) + V (W*H/4)
        memcpy(dst, frame->data[0], y_size);                     // Y
        memcpy(dst + y_size, frame->data[1], uv_size);           // U
        memcpy(dst + y_size + uv_size, frame->data[2], uv_size); // V
        frame_size = y_size + (uv_size * 2);
    } else if (frame->fmt == VID_FMT_NV12) {
        int uv_size = y_size / 2; // NV12 interleaved UV
        // NV12 Memory Layout = Y (W*H) + UV (W*H/2)
        memcpy(dst, frame->data[0], y_size);
        memcpy(dst + y_size, frame->data[1], uv_size);
        frame_size = y_size + uv_size;
    }
    *dst_size = frame_size;
}

static void android_mediacodec_destructor(void *arg)
{
    struct videnc_state *ves = arg;
    if (ves->codec) {
        if (ves->codec_started)
            AMediaCodec_stop(ves->codec);
        AMediaCodec_delete(ves->codec);
        ves->codec = NULL;
    }
    if (ves->mime) {
        mem_deref(ves->mime);
        ves->mime = NULL;
    }
}

int mediacodec_encode_update(struct videnc_state **vesp, const struct vidcodec *vc,
        struct videnc_param *prm, const char *fmtp, videnc_packet_h *pkth, const struct video *vid)
{
    struct videnc_state *ves;
    (void)vc;
    (void)fmtp;

    if (!vesp || !prm)
        return EINVAL;

    ves = *vesp;
    if (!ves) {
        ves = mem_zalloc(sizeof(*ves), android_mediacodec_destructor);
        if (!ves)
            return ENOMEM;
        *vesp = ves;
    }

    if (0 == str_casecmp(vc->name, "H264"))
        str_dup(&ves->mime, "video/avc");
    else if (0 == str_casecmp(vc->name, "H265"))
        str_dup(&ves->mime, "video/hevc");

    ves->bitrate = prm->bitrate;
    ves->fps = prm->fps;
    ves->pkth = pkth;
    ves->vid = vid;
    ves->encprm = *prm;
    return 0;
}

static int open_encoder(struct videnc_state *ves, const struct vidframe *frame)
{
    AMediaFormat *format = AMediaFormat_new();
    AMediaFormat_setString(format, "mime", ves->mime);
    for (int i = 0; i < ARRAY_ELEMS(color_formats); i++) {
        if (frame->fmt == color_formats[i].pix_fmt) {
            AMediaFormat_setInt32(format, "color-format", color_formats[i].color_format);
            break;
        }
    }
    int32_t w = (int32_t)frame->size.w;
    int32_t h = (int32_t)frame->size.h;
    int32_t fps = (int32_t)ves->fps;
    int bitrate = ves->bitrate;
    //    AMediaFormat_setInt32(format, "color-format", 0x7f420888);
    AMediaFormat_setInt32(format, "width", w);
    AMediaFormat_setInt32(format, "height", h);
    AMediaFormat_setInt32(format, "frame-rate", fps);
    AMediaFormat_setInt32(format, "bitrate", bitrate); // take a margin
    AMediaFormat_setInt32(format, "bitrate-mode", 1);
    AMediaFormat_setInt32(format, "latency", 1);
    AMediaFormat_setInt32(format, "priority", 0);
    // IDR intervals can be defined using a configuration file
    AMediaFormat_setInt32(format, "i-frame-interval", 1);
    AMediaFormat_setInt32(format, "intra-refresh-period", (int)ves->fps);
    AMediaFormat_setInt32(format, "prepend-sps-pps-to-idr-frames", 1);
    ves->codec = AMediaCodec_createEncoderByType(ves->mime);
    if (!ves->codec) {
        // release format
        AMediaFormat_delete(format);
        warning("android_mediacodec: create encoder failed\n");
        return ENODEV;
    }

    media_status_t status = AMediaCodec_configure(
            ves->codec, format, NULL, NULL, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    if (status != AMEDIA_OK) {
        // Release the format with codec
        AMediaFormat_delete(format);
        AMediaCodec_delete(ves->codec);
        ves->codec = NULL;
        warning("android_mediacodec: configure failed\n");
        return ENODEV;
    }

    status = AMediaCodec_start(ves->codec);
    if (status != AMEDIA_OK) {
        // Release the format with codec
        AMediaFormat_delete(format);
        AMediaCodec_delete(ves->codec);
        warning("android_mediacodec: start failed\n");
        return ENODEV;
    }
    AMediaFormat_delete(format);
    ves->codec_started = true;
    return 0;
}

int mediacodec_encode_packet(
        struct videnc_state *ves, bool update, const struct vidframe *frame, uint64_t timestamp)
{
    (void)update;
    int err = 0;
    uint64_t ts;
    if (!ves || !frame || (frame->fmt != VID_FMT_NV12 && frame->fmt != VID_FMT_YUV420P))
        return EINVAL;
    if (!ves->codec_started) {
        err = open_encoder(ves, frame);
        if (err) {
            debug("android_mediacodec: open_encoder failed:%d\n", err);
            return err;
        }
    }
    if (!ves->first_ts) {
        ves->first_ts = timestamp;
    }
    ssize_t idx = AMediaCodec_dequeueInputBuffer(ves->codec, 0);
    if (idx >= 0) {
        size_t bufsize;
        uint8_t *buf = AMediaCodec_getInputBuffer(ves->codec, idx, &bufsize);
        if (buf) {
            size_t frame_size;
            copy_frame_to_buffer(frame, buf, &frame_size);
            AMediaCodec_queueInputBuffer(ves->codec, idx, 0, frame_size, timestamp, 0);
        }
    }

    AMediaCodecBufferInfo info;
    ssize_t out_idx = AMediaCodec_dequeueOutputBuffer(ves->codec, &info, 0);
    while (out_idx >= 0) {
        size_t out_size;
        const uint8_t *out_buf = AMediaCodec_getOutputBuffer(ves->codec, out_idx, &out_size);
        if (out_buf && info.size > 0) {
            if (!info.presentationTimeUs) {
                ts = video_calc_rtp_timestamp_fix(ves->first_ts);
            } else {
                ts = video_calc_rtp_timestamp_fix((uint64_t)info.presentationTimeUs);
            }
            if (0 == str_casecmp(ves->mime, "video/avc"))
                err |= h264_packetize(ts, out_buf + info.offset, info.size, ves->encprm.pktsize,
                        (h264_packet_h *)ves->pkth, (void *)ves->vid);
            else if (0 == str_casecmp(ves->mime, "video/hevc"))
                h265_packetize(ts, out_buf + info.offset, info.size, ves->encprm.pktsize,
                        (h265_packet_h *)ves->pkth, (void *)ves->vid);
        }
        AMediaCodec_releaseOutputBuffer(ves->codec, out_idx, false);
        out_idx = AMediaCodec_dequeueOutputBuffer(ves->codec, &info, 0);
    }
    return err;
}

int mediacodec_encode_packetize(struct videnc_state *ves, const struct vidpacket *packet)
{
    uint64_t ts;
    int err = 0;

    if (!ves || !packet)
        return EINVAL;

    ts = video_calc_rtp_timestamp_fix(packet->timestamp);

    if (0 == str_casecmp(ves->mime, "video/avc"))
        err = h264_packetize(ts, packet->buf, packet->size, ves->encprm.pktsize,
                (h264_packet_h *)ves->pkth, (void *)ves->vid);
    else if (0 == str_casecmp(ves->mime, "video/hevc"))
        err = h265_packetize(ts, packet->buf, packet->size, ves->encprm.pktsize,
                (h265_packet_h *)ves->pkth, (void *)ves->vid);

    return err;
}