//
// Created by Administrator on 2025/10/11.
//

#ifndef BARESIP_STUDIO_X264_H
#define BARESIP_STUDIO_X264_H

/* Encode */
int x264_encode_update(struct videnc_state **vesp, const struct vidcodec *vc,
        struct videnc_param *prm, const char *fmtp,
        videnc_packet_h *pkth, const struct video *vid);
int x264_encode_packet(struct videnc_state *ves, bool update,
        const struct vidframe *frame, uint64_t timestamp);
int x264_encode_packetize(struct videnc_state *ves,
        const struct vidpacket *packet);

static struct vidcodec x264 = {
        .name = "H264",
        .encupdh = x264_encode_update,
        .ench = x264_encode_packet,
        .decupdh = NULL,
        .dech = NULL,
        .packetizeh = x264_encode_packetize,
};

#endif //BARESIP_STUDIO_X264_H
