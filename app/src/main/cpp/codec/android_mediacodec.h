//
// Created by Administrator on 2025/10/4.
//

#ifndef BARESIP_STUDIO_ANDROID_MEDIACODEC_H
#define BARESIP_STUDIO_ANDROID_MEDIACODEC_H

#define ARRAY_ELEMS(a) (sizeof(a) / sizeof((a)[0]))

enum
{
    COLOR_FormatYUV420Planar = 0x13,
    COLOR_FormatYUV420SemiPlanar = 0x15,
};

static const struct
{
    int color_format;
    enum vidfmt pix_fmt;
} color_formats[] = {
        {COLOR_FormatYUV420Planar, VID_FMT_YUV420P},
        {COLOR_FormatYUV420SemiPlanar, VID_FMT_NV12},
};

/* Encode */
int mediacodec_encode_update(struct videnc_state **vesp, const struct vidcodec *vc,
        struct videnc_param *prm, const char *fmtp, videnc_packet_h *pkth, const struct video *vid);
int mediacodec_encode_packet_h264(
        struct videnc_state *ves, bool update, const struct vidframe *frame, uint64_t timestamp);
int mediacodec_encode_packet_h265(
        struct videnc_state *ves, bool update, const struct vidframe *frame, uint64_t timestamp);
int mediacodec_encode_packetize_h264(struct videnc_state *ves, const struct vidpacket *packet);
int mediacodec_encode_packetize_h265(struct videnc_state *ves, const struct vidpacket *packet);

/* Decode */
int mediacodec_decode_update(struct viddec_state **vdsp, const struct vidcodec *vc,
        const char *fmtp, const struct video *vid);
int mediacodec_decode_h264(
        struct viddec_state *vds, struct vidframe *frame, struct viddec_packet *pkt);
int mediacodec_decode_h265(
        struct viddec_state *vds, struct vidframe *frame, struct viddec_packet *pkt);

static struct vidcodec mediacodec_h264 = {
        .name = "H264",
        .encupdh = mediacodec_encode_update,
        .ench = mediacodec_encode_packet_h264,
        .decupdh = mediacodec_decode_update,
        .dech = mediacodec_decode_h264,
        .packetizeh = mediacodec_encode_packetize_h264,
};

static struct vidcodec mediacodec_h265 = {
        .name = "H265",
        .encupdh = mediacodec_encode_update,
        .ench = mediacodec_encode_packet_h265,
        .decupdh = mediacodec_decode_update,
        .dech = mediacodec_decode_h265,
        .packetizeh = mediacodec_encode_packetize_h265,
};

#endif //BARESIP_STUDIO_ANDROID_MEDIACODEC_H
