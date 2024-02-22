/**
 * @file re_h264.h Interface to H.264 header parser
 *
 * Copyright (C) 2010 Creytiv.com
 */


/** NAL unit types */
enum h264_nalu {
	H264_NALU_SLICE        = 1,
	H264_NALU_DPA          = 2,
	H264_NALU_DPB          = 3,
	H264_NALU_DPC          = 4,
	H264_NALU_IDR_SLICE    = 5,
	H264_NALU_SEI          = 6,
	H264_NALU_SPS          = 7,
	H264_NALU_PPS          = 8,
	H264_NALU_AUD          = 9,
	H264_NALU_END_SEQUENCE = 10,
	H264_NALU_END_STREAM   = 11,
	H264_NALU_FILLER_DATA  = 12,
	H264_NALU_SPS_EXT      = 13,
	H264_NALU_AUX_SLICE    = 19,

	H264_NALU_STAP_A       = 24,
	H264_NALU_STAP_B       = 25,
	H264_NALU_MTAP16       = 26,
	H264_NALU_MTAP24       = 27,
	H264_NALU_FU_A         = 28,
	H264_NALU_FU_B         = 29,
};


/**
 * H.264 NAL Header
 */
struct h264_nal_header {
	unsigned f:1;      /**< Forbidden zero bit (must be 0) */
	unsigned nri:2;    /**< nal_ref_idc                    */
	unsigned type:5;   /**< NAL unit type                  */
};


int h264_nal_header_encode(struct mbuf *mb, const struct h264_nal_header *hdr);
int h264_nal_header_decode(struct h264_nal_header *hdr, struct mbuf *mb);
void h264_nal_header_decode_buf(struct h264_nal_header *hdr,
				const uint8_t *buf);
const char *h264_nal_unit_name(enum h264_nalu nal_type);


/**
 * H.264 Sequence Parameter Set (SPS)
 */
struct h264_sps {
	uint8_t profile_idc;
	uint8_t level_idc;
	uint8_t seq_parameter_set_id;               /* 0-31 */
	uint8_t chroma_format_idc;                  /* 0-3 */

	unsigned log2_max_frame_num;
	unsigned pic_order_cnt_type;

	unsigned max_num_ref_frames;
	unsigned pic_width_in_mbs;
	unsigned pic_height_in_map_units;

	unsigned frame_crop_left_offset;            /* pixels */
	unsigned frame_crop_right_offset;           /* pixels */
	unsigned frame_crop_top_offset;             /* pixels */
	unsigned frame_crop_bottom_offset;          /* pixels */
};

int  h264_sps_decode(struct h264_sps *sps, const uint8_t *p, size_t len);
void h264_sps_resolution(const struct h264_sps *sps,
		unsigned *width, unsigned *height);
const char *h264_sps_chroma_format_name(uint8_t chroma_format_idc);


typedef int (h264_packet_h)(bool marker, uint64_t rtp_ts,
			    const uint8_t *hdr, size_t hdr_len,
			    const uint8_t *pld, size_t pld_len,
			    void *arg);

/** Fragmentation Unit header */
struct h264_fu {
	unsigned s:1;      /**< Start bit                               */
	unsigned e:1;      /**< End bit                                 */
	unsigned r:1;      /**< The Reserved bit MUST be equal to 0     */
	unsigned type:5;   /**< The NAL unit payload type               */
};

int h264_fu_hdr_encode(const struct h264_fu *fu, struct mbuf *mb);
int h264_fu_hdr_decode(struct h264_fu *fu, struct mbuf *mb);

const uint8_t *h264_find_startcode(const uint8_t *p, const uint8_t *end);

int h264_packetize(uint64_t rtp_ts, const uint8_t *buf, size_t len,
		   size_t pktsize, h264_packet_h *pkth, void *arg);
int h264_nal_send(bool first, bool last,
		  bool marker, uint32_t ihdr, uint64_t rtp_ts,
		  const uint8_t *buf, size_t size, size_t maxsz,
		  h264_packet_h *pkth, void *arg);
bool h264_is_keyframe(int type);
int  h264_stap_encode(struct mbuf *mb, const uint8_t *frame,
		      size_t frame_sz);
int  h264_stap_decode_annexb(struct mbuf *mb_frame, struct mbuf *mb_pkt);
