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
#include "../logger.h"
#include "android_mediacodec.h"
#include "parse/h2645_parse.h"

enum
{
    DECODE_MAXSZ = 524288,
};

struct viddec_state
{
    //    const AVCodec *codec;
    //    AVCodecContext *ctx;
    //    AVFrame *pict;
    AMediaCodec *codec;
    AMediaFormat *format;
    struct mbuf *mb;
    bool got_keyframe;
    size_t frag_start;
    bool frag;
    uint16_t frag_seq;

    struct
    {
        unsigned n_key;
        unsigned n_lost;
    } stats;
    bool open;
    char *mime;
    bool codec_started;
};


static void destructor(void *arg)
{
    struct viddec_state *st = arg;

    debug("avcodec: decoder stats"
          " (keyframes:%u, lost_fragments:%u)\n",
            st->stats.n_key, st->stats.n_lost);

    if (st->codec) {
        if (st->codec_started)
            AMediaCodec_stop(st->codec);
        AMediaCodec_delete(st->codec);
        st->codec = NULL;
    }
    if (st->format) {
        AMediaFormat_delete(st->format);
        st->format = NULL;
    }
    mem_deref(st->mb);
}

static inline void fragment_rewind(struct viddec_state *vds)
{
    vds->mb->pos = vds->frag_start;
    vds->mb->end = vds->frag_start;
}

static int init_decoder(struct viddec_state *st, const char *name)
{
    if (0 == str_casecmp(name, "H264"))
        st->mime = "video/avc";
    else if (0 == str_casecmp(name, "H265"))
        st->mime = "video/hevc";

    if (!st->format) {
        AMediaFormat *format = AMediaFormat_new();
        AMediaFormat_setString(format, "mime", st->mime);
        st->format = format;
    }
    if (st->open) {
        AMediaCodec *codec = AMediaCodec_createDecoderByType(st->mime);
        if (!codec) {
            warning("mediacodec: create encoder failed\n");
            return ENODEV;
        }
        st->codec = codec;
        media_status_t status = AMediaCodec_configure(st->codec, st->format, NULL, NULL, 0);
        if (status != AMEDIA_OK) {
            warning("mediacodec: configure failed\n");
            return ENODEV;
        }
        status = AMediaCodec_start(st->codec);
        if (status != AMEDIA_OK) {
            warning("mediacodec: start failed\n");
            return ENODEV;
        }
        st->codec_started = true;
    }
    return 0;
}

int mediacodec_decode_update(struct viddec_state **vdsp, const struct vidcodec *vc,
        const char *fmtp, const struct video *vid)
{
    struct viddec_state *st;
    int err = 0;

    if (!vdsp || !vc)
        return EINVAL;

    if (*vdsp)
        return 0;

    (void)fmtp;
    (void)vid;

    st = mem_zalloc(sizeof(*st), destructor);
    if (!st)
        return ENOMEM;

    st->open = false;
    st->mb = mbuf_alloc(1024);
    if (!st->mb) {
        err = ENOMEM;
        goto out;
    }

    err = init_decoder(st, vc->name);
    if (err) {
        warning("avcodec: %s: could not init decoder\n", vc->name);
        goto out;
    }

    debug("avcodec: video decoder %s (%s)\n", vc->name, fmtp);

out:
    if (err)
        mem_deref(st);
    else
        *vdsp = st;

    return err;
}

static int decode(
        struct viddec_state *st, struct vidframe *frame, struct viddec_packet *pkt, bool full_frame)
{
    int err = 0;

    if (full_frame) {
        size_t size = st->mb->pos;
        ssize_t in_idx = AMediaCodec_dequeueInputBuffer(st->codec, 2000);
        if (in_idx >= 0) {
            size_t buf_size;
            uint8_t *buf = AMediaCodec_getInputBuffer(st->codec, in_idx, &buf_size);
            if (buf && size <= buf_size) {
                memcpy(buf, st->mb->buf, size);
                AMediaCodec_queueInputBuffer(st->codec, in_idx, 0, size, 0, 0);
            }
        }
    }
    AMediaCodecBufferInfo info;
    ssize_t out_idx = AMediaCodec_dequeueOutputBuffer(st->codec, &info, 0);
    if (out_idx >= 0) {
        uint8_t *out_buf = AMediaCodec_getOutputBuffer(st->codec, out_idx, NULL);
        LOGI("Got decoded HEVC frame, size=%d", info.size);
        AMediaFormat *format = AMediaCodec_getOutputFormat(st->codec);
        LOGD("decode format:%s", AMediaFormat_toString(format));
        int32_t w = 0, h = 0;
        // 进行解码操作了
        // nv12 需要宽高，但是首次传入的sps后面分辨率变化会导致解析错误
        AMediaFormat_getInt32(st->format, "width", &w);
        AMediaFormat_getInt32(st->format, "height", &h);
        if (w > 0 && h > 0) {
            struct vidsz size = {
                    .w = w,
                    .h = h,
            };
            vidframe_init_buf(frame, VID_FMT_NV12, &size, out_buf);
        }
        // out_buf 是解码后的 YUV420P 数据，可渲染或保存
        AMediaCodec_releaseOutputBuffer(st->codec, out_idx, false);
        //        out_idx = AMediaCodec_dequeueOutputBuffer(st->codec, &info, 0);
    } else {
        err = EBADMSG;
    }
    return err;
}

int mediacodec_decode_h264(
        struct viddec_state *st, struct vidframe *frame, struct viddec_packet *pkt)
{
    struct h264_nal_header h264_hdr;
    const uint8_t nal_seq[3] = {0, 0, 1};
    int err;

    if (!st || !frame || !pkt || !pkt->mb)
        return EINVAL;

    pkt->intra = false;
    struct mbuf *src = pkt->mb;

    err = h264_nal_header_decode(&h264_hdr, src);
    if (err)
        return err;

#if 0
	re_printf("avcodec: decode: %s %s type=%2d %s  \n",
		  marker ? "[M]" : "   ",
		  h264_is_keyframe(h264_hdr.type) ? "<KEY>" : "     ",
		  h264_hdr.type,
		  h264_nal_unit_name(h264_hdr.type));
#endif

    if (h264_hdr.type == H264_NALU_SLICE && !st->got_keyframe) {
        debug("avcodec: decoder waiting for keyframe\n");
        return EPROTO;
    }

    if (h264_hdr.f) {
        info("avcodec: H264 forbidden bit set!\n");
        return EBADMSG;
    }

    if (st->frag && h264_hdr.type != H264_NALU_FU_A) {
        debug("avcodec: lost fragments; discarding previous NAL\n");
        fragment_rewind(st);
        st->frag = false;
        ++st->stats.n_lost;
    }

    /* handle NAL types */
    if (1 <= h264_hdr.type && h264_hdr.type <= 23) {

        --src->pos;

        /* prepend H.264 NAL start sequence */
        err = mbuf_write_mem(st->mb, nal_seq, 3);

        err |= mbuf_write_mem(st->mb, mbuf_buf(src), mbuf_get_left(src));
        if (err)
            goto out;
    } else if (H264_NALU_FU_A == h264_hdr.type) {
        struct h264_fu fu;

        err = h264_fu_hdr_decode(&fu, src);
        if (err)
            return err;
        h264_hdr.type = fu.type;

        if (fu.s) {
            if (st->frag) {
                debug("avcodec: start: lost fragments;"
                      " ignoring previous NAL\n");
                fragment_rewind(st);
                ++st->stats.n_lost;
            }

            st->frag_start = st->mb->pos;
            st->frag = true;

            /* prepend H.264 NAL start sequence */
            mbuf_write_mem(st->mb, nal_seq, 3);

            /* encode NAL header back to buffer */
            err = h264_nal_header_encode(st->mb, &h264_hdr);
            if (err)
                goto out;
        } else {
            if (!st->frag) {
                debug("avcodec: ignoring fragment"
                      " (nal=%u)\n",
                        fu.type);
                ++st->stats.n_lost;
                return 0;
            }

            if (rtp_seq_diff(st->frag_seq, pkt->hdr->seq) != 1) {
                debug("avcodec: lost fragments detected\n");
                fragment_rewind(st);
                st->frag = false;
                ++st->stats.n_lost;
                return 0;
            }
        }

        err = mbuf_write_mem(st->mb, mbuf_buf(src), mbuf_get_left(src));
        if (err)
            goto out;

        if (fu.e)
            st->frag = false;

        st->frag_seq = pkt->hdr->seq;
    } else if (H264_NALU_STAP_A == h264_hdr.type) {

        err = h264_stap_decode_annexb(st->mb, src);
        if (err)
            goto out;
    } else {
        warning("avcodec: decode: unknown NAL type %u\n", h264_hdr.type);
        return EBADMSG;
    }

    if (!pkt->hdr->m) {

        if (st->mb->end > DECODE_MAXSZ) {
            warning("avcodec: decode buffer size exceeded\n");
            err = ENOMEM;
            goto out;
        }
        /* You need to decode the cache of the previous
		frame as soon as possible to avoid
		accumulation Especially when using mediacodec */
        if (st->open) {
            decode(st, frame, pkt, false);
        }

        return 0;
    }

    if (st->frag) {
        err = EPROTO;
        goto out;
    }

    /*	When using MediaCodec hardware decoding,
		you must set width, height,
		and SPS/PPS parameters before decoding the first frame,
		otherwise decoding will fail.
	 	Here, width, height, and extradata are set
		by parsing the SPS/PPS.
	 	This is only required when using MediaCodec hardware
		decoding—other software decoders do not need this.
	 	Additionally, the extradata format must be:
		0x00 0x00 0x01 sps 0x00 0x00 0x01 pps */
    if (!st->open) {
        uint8_t sps_data[MAX_SPS];
        size_t sps_len;
        uint8_t pps_data[MAX_PPS];
        size_t pps_len;
        int ret = h264_get_sps_pps(
                st->mb->buf, (int)st->mb->pos, sps_data, &sps_len, pps_data, &pps_len);
        if (ret) {
            warning("avcodec: decode: "
                    "h264_get_sps_pps error %d\n",
                    ret);
            goto out;
        }
        int w, h;
        ret = h264_decode_sps_with_width_and_height(sps_data + 3, sps_len - 3, &w, &h);
        if (ret) {
            warning("avcodec: decode: "
                    "h264_decode_sps_"
                    "with_width_and_height error %d\n",
                    ret);
            goto out;
        }
        AMediaFormat_setInt32(st->format, "width", w);
        AMediaFormat_setInt32(st->format, "height", h);
        AMediaFormat_setBuffer(st->format, "csd-0", sps_data, sps_len);
        AMediaFormat_setBuffer(st->format, "csd-1", pps_data, pps_len);
        st->open = true;
        debug("avcodec: decode: init decoder H264\n");
        init_decoder(st, "H264");
    }

    err = decode(st, frame, pkt, true);
    if (err)
        goto out;

out:
    mbuf_rewind(st->mb);
    st->frag = false;

    return err;
}

enum
{
    H265_FU_HDR_SIZE = 1
};

struct h265_fu
{
    unsigned s : 1;
    unsigned e : 1;
    unsigned type : 6;
};


static inline int h265_fu_decode(struct h265_fu *fu, struct mbuf *mb)
{
    uint8_t v;

    if (mbuf_get_left(mb) < 1)
        return EBADMSG;

    v = mbuf_read_u8(mb);

    fu->s = v >> 7 & 0x1;
    fu->e = v >> 6 & 0x1;
    fu->type = v >> 0 & 0x3f;

    return 0;
}

int mediacodec_decode_h265(
        struct viddec_state *vds, struct vidframe *frame, struct viddec_packet *pkt)
{
    static const uint8_t nal_seq[3] = {0, 0, 1};
    struct h265_nal hdr;
    int err;

    if (!vds || !frame || !pkt || !pkt->mb)
        return EINVAL;

    pkt->intra = false;
    struct mbuf *mb = pkt->mb;

    if (mbuf_get_left(mb) < H265_HDR_SIZE)
        return EBADMSG;

    err = h265_nal_decode(&hdr, mbuf_buf(mb));
    if (err)
        return err;

    mbuf_advance(mb, H265_HDR_SIZE);

#if 0
	debug("avcodec: h265: decode:  [%s]  %s  type=%2d  %s\n",
	      marker ? "M" : " ",
	      h265_is_keyframe(hdr.nal_unit_type) ? "<KEY>" : "     ",
	      hdr.nal_unit_type,
	      h265_nalunit_name(hdr.nal_unit_type));
#endif

    if (vds->frag && hdr.nal_unit_type != H265_NAL_FU) {
        debug("h265: lost fragments; discarding previous NAL\n");
        fragment_rewind(vds);
        vds->frag = false;
    }

    /* handle NAL types */
    if (hdr.nal_unit_type <= 40) {

        mb->pos -= H265_HDR_SIZE;

        err = mbuf_write_mem(vds->mb, nal_seq, 3);
        err |= mbuf_write_mem(vds->mb, mbuf_buf(mb), mbuf_get_left(mb));
        if (err)
            goto out;
    } else if (H265_NAL_FU == hdr.nal_unit_type) {

        struct h265_fu fu;

        err = h265_fu_decode(&fu, mb);
        if (err)
            return err;

        if (fu.s) {
            if (vds->frag) {
                debug("h265: lost fragments; ignoring NAL\n");
                fragment_rewind(vds);
            }

            vds->frag_start = vds->mb->pos;
            vds->frag = true;

            hdr.nal_unit_type = fu.type;

            err = mbuf_write_mem(vds->mb, nal_seq, 3);
            err |= h265_nal_encode_mbuf(vds->mb, &hdr);
            if (err)
                goto out;
        } else {
            if (!vds->frag) {
                debug("h265: ignoring fragment\n");
                return 0;
            }

            if (rtp_seq_diff(vds->frag_seq, pkt->hdr->seq) != 1) {
                debug("h265: lost fragments detected\n");
                fragment_rewind(vds);
                vds->frag = false;
                return 0;
            }
        }

        err = mbuf_write_mem(vds->mb, mbuf_buf(mb), mbuf_get_left(mb));
        if (err)
            goto out;

        if (fu.e)
            vds->frag = false;

        vds->frag_seq = pkt->hdr->seq;
    } else if (hdr.nal_unit_type == H265_NAL_AP) {

        while (mbuf_get_left(mb) >= 2) {

            const uint16_t len = ntohs(mbuf_read_u16(mb));

            if (mbuf_get_left(mb) < len)
                return EBADMSG;

            err = mbuf_write_mem(vds->mb, nal_seq, 3);
            err |= mbuf_write_mem(vds->mb, mbuf_buf(mb), len);
            if (err)
                goto out;

            mb->pos += len;
        }
    } else {
        warning("avcodec: unknown H265 NAL type %u (%s) [%zu bytes]\n", hdr.nal_unit_type,
                h265_nalunit_name(hdr.nal_unit_type), mbuf_get_left(mb));
        return EPROTO;
    }

    if (!pkt->hdr->m) {

        if (vds->mb->end > DECODE_MAXSZ) {
            warning("avcodec: h265 decode buffer size exceeded\n");
            err = ENOMEM;
            goto out;
        }
        /* You need to decode the cache of the previous
		frame as soon as possible to avoid
		accumulation Especially when using mediacodec*/
        if (vds->open) {
            decode(vds, frame, pkt, false);
        }

        return 0;
    }

    if (vds->frag) {
        err = EPROTO;
        goto out;
    }

    /* When using MediaCodec hardware decoding, you must set width, height,
		and SPS/PPS parameters before decoding the first frame,
		otherwise decoding will fail.
		Here, width, height, and extradata are set
		by parsing the SPS/PPS.
		This is only required when using MediaCodec hardware
		decoding—other software decoders do not need this.
		Additionally, the extradata format must be:
		0x00 0x00 0x01 vps 0x00 0x00 0x01 sps 0x00 0x00 0x01 pps */
    if (!vds->open) {
        uint8_t vps_data[MAX_VPS];
        size_t vps_len = 0;
        uint8_t sps_data[MAX_SPS];
        size_t sps_len = 0;
        uint8_t pps_data[MAX_PPS];
        size_t pps_len = 0;
        int ret = h265_get_vps_sps_pps(vds->mb->buf, (int)vds->mb->pos, vps_data, &vps_len,
                sps_data, &sps_len, pps_data, &pps_len);
        if (ret) {
            warning("avcodec: decode: "
                    "h265_get_vps_sps_pps error %d\n",
                    ret);
            goto out;
        }
        int w, h;
        ret = h265_decode_sps_with_width_and_height(sps_data + 3, sps_len - 3, &w, &h);
        if (ret) {
            warning("avcodec: decode: "
                    "h265_decode_sps_"
                    "with_width_and_height error %d\n",
                    ret);
            goto out;
        }
        AMediaFormat_setInt32(vds->format, "width", w);
        AMediaFormat_setInt32(vds->format, "height", h);
        AMediaFormat_setBuffer(vds->format, "csd-0", vps_data, vps_len);
        AMediaFormat_setBuffer(vds->format, "csd-1", sps_data, sps_len);
        AMediaFormat_setBuffer(vds->format, "csd-2", pps_data, pps_len);
        vds->open = true;
        debug("avcodec: decode: init decoder H265\n");
        init_decoder(vds, "H265");
    }

    err = decode(vds, frame, pkt, true);
    if (err)
        goto out;

out:
    mbuf_rewind(vds->mb);
    vds->frag = false;

    return err;
}