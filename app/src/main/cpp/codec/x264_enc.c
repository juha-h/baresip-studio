//
// Created by Administrator on 2025/10/11.
//
#include <string.h>
#include <re.h>
#include <re_av1.h>
#include <rem.h>
#include <baresip.h>
#include <x264.h>
#include <err.h>

struct videnc_state
{
    double fps;
    unsigned bitrate;
    videnc_packet_h *pkth;
    const struct video *vid;
    struct videnc_param encprm;
    x264_t *enc;
    x264_param_t param;
    x264_picture_t pic_in;
    x264_picture_t pic_out;
    struct mbuf *mb;
};


static void x264_destructor(void *arg)
{
    struct videnc_state *ves = arg;
    if (ves->enc) {
        x264_encoder_close(ves->enc);
        ves->enc = NULL;
    }
    mem_deref(ves->mb);
}


int x264_encode_update(struct videnc_state **vesp, const struct vidcodec *vc,
        struct videnc_param *prm, const char *fmtp, videnc_packet_h *pkth, const struct video *vid)
{
    struct videnc_state *ves;
    (void)fmtp;

    if (!vesp || !vc || !prm)
        return EINVAL;

    ves = *vesp;
    if (!ves) {
        ves = mem_zalloc(sizeof(*ves), x264_destructor);
        if (!ves)
            return ENOMEM;
        *vesp = ves;
    }

    ves->bitrate = prm->bitrate;
    ves->fps = prm->fps;
    ves->pkth = pkth;
    ves->vid = vid;
    ves->encprm = *prm;
    ves->mb = mbuf_alloc(1024);
    if (!ves->mb) {
        return ENOMEM;
    }
    return 0;
}

static int open_encoder(struct videnc_state *ves, const struct vidsz *size)
{
    int fps = (int)ves->fps;
    int bitrate = (int)ves->bitrate;
    int width = (int)size->w;
    int height = (int)size->h;

    x264_param_t param;
    x264_param_default_preset(&param, "ultrafast", "zerolatency");

    param.rc.i_lookahead = 0;
    param.i_sync_lookahead = 0;
    param.b_sliced_threads = 1;
    param.rc.b_mb_tree = 0;
    //    param.i_log_level = X264_LOG_DEBUG;
    param.i_level_idc = 32;
    param.i_csp = X264_CSP_I420;
    param.i_width = width;
    param.i_height = height;
    param.i_bframe = 0;
    param.rc.i_rc_method = X264_RC_ABR;
    param.rc.i_bitrate = bitrate / 1024;
    param.i_fps_num = fps;
    param.i_fps_den = 1;
    param.i_timebase_den = param.i_fps_num;
    param.i_timebase_num = param.i_fps_den;
    param.b_vfr_input = 0;
    param.i_keyint_max = fps * 2;
    param.b_repeat_headers = 1;
    param.i_threads = 1;
    x264_param_apply_profile(&param, "baseline");

    ves->param = param;
    ves->enc = x264_encoder_open(&ves->param);
    if (!ves->enc) {
        warning("x264: encoder open failed\n");
        return ENODEV;
    }

    //    x264_picture_alloc(&ves->pic_in, X264_CSP_I420, size->w, size->h);
    // Avoid yuv copying time-consuming
    x264_picture_init(&ves->pic_in);
    //    int csp = X264_CSP_I420 & X264_CSP_MASK;
    ves->pic_in.img.i_csp = X264_CSP_I420;
    ves->pic_in.img.i_plane = 3;
    return 0;
}

int x264_encode_packet(
        struct videnc_state *ves, bool update, const struct vidframe *frame, uint64_t timestamp)
{
    int err = 0;
    uint64_t ts;
    if (!ves || !frame || frame->fmt != VID_FMT_YUV420P)
        return EINVAL;

    if (!ves->enc) {
        err = open_encoder(ves, &frame->size);
        if (err)
            return err;
    }

    for (int i = 0; i < 4; i++) {
        ves->pic_in.img.plane[i] = frame->data[i];
        ves->pic_in.img.i_stride[i] = frame->linesize[i];
    }
    ves->pic_in.i_pts = (int64_t)timestamp;

    x264_nal_t *nals;
    int i_nals;
    x264_encoder_encode(ves->enc, &nals, &i_nals, &ves->pic_in, &ves->pic_out);
    if (i_nals <= 0)
        return 0;

    mbuf_reset(ves->mb);
    for (int i = 0; i < i_nals; i++) {
        int nalu_type = nals[i].i_type;
        debug("x264: nalu_type: %d pts:%ld\n", nalu_type, (uint64_t)ves->pic_out.i_pts);
        mbuf_write_mem(ves->mb, nals[i].p_payload, nals[i].i_payload);
        if (nalu_type != NAL_SPS && nalu_type != NAL_PPS) {
            ts = video_calc_rtp_timestamp_fix((uint64_t)ves->pic_out.i_pts);
            err |= h264_packetize(ts, ves->mb->buf, ves->mb->pos, ves->encprm.pktsize,
                    (h264_packet_h *)ves->pkth, (void *)ves->vid);
            mbuf_reset(ves->mb);
        }
    }
    mbuf_reset(ves->mb);
    return err;
}


int x264_encode_packetize(struct videnc_state *ves, const struct vidpacket *packet)
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
