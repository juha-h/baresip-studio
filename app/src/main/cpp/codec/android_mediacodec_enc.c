//
// Created by Administrator on 2025/10/4.
//
#include <string.h>
#include <re.h>
#include <rem.h>
#include <baresip.h>
#include "codec.h"
#include "../logger.h"
#include <jni.h>
#include <android/native_window_jni.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaCodecInfo.h>
//#include <media/NdkMediaCodecList.h>
#include <err.h>

struct videnc_state
{
    double fps;
    unsigned bitrate;
    videnc_packet_h *pkth;
    const struct video *vid;
    struct videnc_param encprm;
    char *mime;
    AMediaCodec *codec;
    AMediaFormat *format;
    uint64_t frame_counter;
    struct mbuf *mb;

    int width;
    int height;
    bool codec_started;
};

static void android_mediacodec_destructor(void *arg)
{
    struct videnc_state *ves = arg;
    if (ves->codec) {
        if (ves->codec_started)
            AMediaCodec_stop(ves->codec);
        AMediaCodec_delete(ves->codec);
        ves->codec = NULL;
    }
    if (ves->format) {
        AMediaFormat_delete(ves->format);
        ves->format = NULL;
    }
    mem_deref(ves->mb);
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
        ves->mime = "video/avc";
    else if (0 == str_casecmp(vc->name, "H265"))
        ves->mime = "video/hevc";

    ves->bitrate = prm->bitrate;
    ves->fps = prm->fps;
    ves->pkth = pkth;
    ves->vid = vid;
    ves->encprm = *prm;
    ves->frame_counter = 0;
    ves->mb = mbuf_alloc(1024);
    if (!ves->mb)
        return ENOMEM;

    return 0;
}

static int open_encoder(struct videnc_state *ves, const struct vidframe *frame)
{
    AMediaFormat *format = AMediaFormat_new();
    AMediaFormat_setString(format, "mime", ves->mime);
    AMediaFormat_setInt32(format, "color-format", 0x7f420888);
    AMediaFormat_setInt32(format, "width", frame->size.w);
    AMediaFormat_setInt32(format, "height", frame->size.h);
    AMediaFormat_setInt32(format, "frame-rate", (int)ves->fps);
    AMediaFormat_setInt32(format, "bitrate", (ves->bitrate * 9) / 10); // take a margin
    AMediaFormat_setInt32(format, "bitrate-mode", 1);
    AMediaFormat_setInt32(format, "i-frame-interval", 10);
    AMediaFormat_setInt32(format, "latency", 1);
    AMediaFormat_setInt32(format, "priority", 0);

    ves->format = format;

    LOGD("open_encoder format:%s", AMediaFormat_toString(format));

    ves->codec = AMediaCodec_createEncoderByType("video/avc");
    if (!ves->codec) {
        warning("mediacodec: create encoder failed\n");
        return ENODEV;
    }

    media_status_t status = AMediaCodec_configure(
            ves->codec, ves->format, NULL, NULL, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    if (status != AMEDIA_OK) {
        warning("mediacodec: configure failed\n");
        return ENODEV;
    }

    status = AMediaCodec_start(ves->codec);
    if (status != AMEDIA_OK) {
        warning("mediacodec: start failed\n");
        return ENODEV;
    }
    ves->codec_started = true;
    return 0;
}


int mediacodec_encode_packet(
        struct videnc_state *ves, bool update, const struct vidframe *frame, uint64_t timestamp)
{
    int err;
    if (!ves || !frame || (frame->fmt != VID_FMT_NV12 && frame->fmt != VID_FMT_YUV420P))
        return EINVAL;
    if (!ves->codec_started) {
        err = open_encoder(ves, frame);
        if (err) {
            LOGD("open_encoder failed:%d", err);
            return err;
        }
    }

    ssize_t idx = AMediaCodec_dequeueInputBuffer(ves->codec, 0);
    if (idx >= 0) {
        size_t bufsize;
        uint8_t *buf = AMediaCodec_getInputBuffer(ves->codec, idx, &bufsize);
        if (buf) {
            int frame_size = 0;
            if (frame->fmt == VID_FMT_NV12) {
                int y_size = frame->size.w * frame->size.h;
                int uv_size = y_size / 2; // NV12 interleaved UV

                // NV12 Memory Layout = Y (W*H) + UV (W*H/2)
                memcpy(buf, frame->data[0], y_size);
                memcpy(buf + y_size, frame->data[1], uv_size);
                frame_size = y_size + uv_size;
            } else {
                int y_size = frame->size.w * frame->size.h;
                int uv_size = y_size / 4; // Each U, V plane is w*h/4

                // I420 Memory Layout = Y (W*H) + U (W*H/4) + V (W*H/4)
                memcpy(buf, frame->data[0], y_size);                     // Y
                memcpy(buf + y_size, frame->data[1], uv_size);           // U
                memcpy(buf + y_size + uv_size, frame->data[2], uv_size); // V
                frame_size = y_size + (uv_size * 2);
            }

            AMediaCodec_queueInputBuffer(ves->codec, idx, 0, frame_size,
                    (int64_t)(ves->frame_counter * 1000000 / ves->fps), 0);
        }
    }

    AMediaCodecBufferInfo info;
    ssize_t out_idx = AMediaCodec_dequeueOutputBuffer(ves->codec, &info, 0);
    int size = 0;
    while (out_idx >= 0) {
        size_t out_size;
        const uint8_t *out_buf = AMediaCodec_getOutputBuffer(ves->codec, out_idx, &out_size);
        if (out_buf && info.size > 0) {
            //            mbuf_reset(ves->mb);
            //            mbuf_write_mem(ves->mb, out_buf, out_size);
            LOGD("mediacodec_encode_packet out_size: %d", info.size - info.offset);

            uint32_t rtp_ts = (uint32_t)(ves->frame_counter * 90000 / ves->fps);
            h264_packetize(rtp_ts, out_buf + info.offset, info.size, ves->encprm.pktsize,
                    (h264_packet_h *)ves->pkth, (void *)ves->vid);
            ves->frame_counter++;
            size += (info.size - info.offset);
        }
        AMediaCodec_releaseOutputBuffer(ves->codec, out_idx, false);
        out_idx = AMediaCodec_dequeueOutputBuffer(ves->codec, &info, 0);
    }
    LOGD("mediacodec_encode_packet size: %d", size);
    return 0;
}

int mediacodec_encode_packetize(struct videnc_state *ves, const struct vidpacket *packet)
{
    uint64_t ts;
    int err;

    if (!ves || !packet)
        return EINVAL;

    ts = video_calc_rtp_timestamp_fix(packet->timestamp);

    err = h264_packetize(ts, packet->buf, packet->size, ves->encprm.pktsize,
            (h264_packet_h *)ves->pkth, (void *)ves->vid);
    return err;
}