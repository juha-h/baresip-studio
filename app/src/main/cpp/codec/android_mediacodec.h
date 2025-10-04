//
// Created by Administrator on 2025/10/4.
//

#ifndef BARESIP_STUDIO_ANDROID_MEDIACODEC_H
#define BARESIP_STUDIO_ANDROID_MEDIACODEC_H

/* Encode */
int mediacodec_encode_update(struct videnc_state **vesp, const struct vidcodec *vc,
        struct videnc_param *prm, const char *fmtp, videnc_packet_h *pkth, const struct video *vid);
int mediacodec_encode_packet(
        struct videnc_state *ves, bool update, const struct vidframe *frame, uint64_t timestamp);
int mediacodec_encode_packetize(struct videnc_state *ves, const struct vidpacket *packet);

/* Decode */
//int open264_decode_update(struct viddec_state **vdsp, const struct vidcodec *vc,
//        const char *fmtp, const struct video *vid);
//int open264_decode(struct viddec_state *vds, struct vidframe *frame,
//        struct viddec_packet *pkt);

static struct vidcodec mediacodec_h264 = {
        .name = "H264",
        .encupdh = mediacodec_encode_update,
        .ench = mediacodec_encode_packet,
        .decupdh = NULL,
        .dech = NULL,
        .packetizeh = mediacodec_encode_packetize,
};

#endif //BARESIP_STUDIO_ANDROID_MEDIACODEC_H
