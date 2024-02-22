/**
 * @file re_h265.h Interface to H.265 header parser
 *
 * Copyright (C) 2010 - 2022 Alfred E. Heggestad
 */


enum {
	H265_HDR_SIZE = 2
};


enum h265_naltype {
	/* VCL class */
	H265_NAL_TRAIL_N         = 0,
	H265_NAL_TRAIL_R         = 1,

	H265_NAL_TSA_N           = 2,
	H265_NAL_TSA_R           = 3,

	H265_NAL_STSA_N          = 4,
	H265_NAL_STSA_R          = 5,
	H265_NAL_RADL_N          = 6,
	H265_NAL_RADL_R          = 7,

	H265_NAL_RASL_N          = 8,
	H265_NAL_RASL_R          = 9,

	H265_NAL_BLA_W_LP        = 16,
	H265_NAL_BLA_W_RADL      = 17,
	H265_NAL_BLA_N_LP        = 18,
	H265_NAL_IDR_W_RADL      = 19,
	H265_NAL_IDR_N_LP        = 20,
	H265_NAL_CRA_NUT         = 21,

	/* non-VCL class */
	H265_NAL_VPS_NUT         = 32,
	H265_NAL_SPS_NUT         = 33,
	H265_NAL_PPS_NUT         = 34,
	H265_NAL_AUD             = 35,
	H265_NAL_EOS_NUT         = 36,    /* End of sequence */
	H265_NAL_EOB_NUT         = 37,    /* End of bitstream */
	H265_NAL_FD_NUT          = 38,    /* Filler data */
	H265_NAL_PREFIX_SEI_NUT  = 39,
	H265_NAL_SUFFIX_SEI_NUT  = 40,

	/* RFC 7798 */
	H265_NAL_AP              = 48,    /* Aggregation Packets */
	H265_NAL_FU              = 49,
};

struct h265_nal {
	unsigned nal_unit_type:6;          /* NAL unit type (0-40)       */
	unsigned nuh_temporal_id_plus1:3;  /* temporal identifier plus 1 */
};

void h265_nal_encode(uint8_t buf[2], unsigned nal_unit_type,
		     unsigned nuh_temporal_id_plus1);
int  h265_nal_encode_mbuf(struct mbuf *mb, const struct h265_nal *nal);
int  h265_nal_decode(struct h265_nal *nal, const uint8_t *p);
void h265_nal_print(const struct h265_nal *nal);
const char *h265_nalunit_name(enum h265_naltype type);
bool h265_is_keyframe(enum h265_naltype type);


typedef int (h265_packet_h)(bool marker, uint64_t rtp_ts,
			    const uint8_t *hdr, size_t hdr_len,
			    const uint8_t *pld, size_t pld_len,
			    void *arg);

int h265_packetize(uint64_t rtp_ts, const uint8_t *buf, size_t len,
		   size_t pktsize, h265_packet_h *pkth, void *arg);
