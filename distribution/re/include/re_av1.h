/**
 * @file re_av1.h AV1 Open Bitstream Unit (OBU)
 *
 * Copyright (C) 2010 - 2022 Alfred E. Heggestad
 */


/* OBU (Open Bitstream Units) */


/** Defines the OBU type */
enum obu_type {
	AV1_OBU_SEQUENCE_HEADER        =  1,
	AV1_OBU_TEMPORAL_DELIMITER     =  2,
	AV1_OBU_FRAME_HEADER           =  3,
	AV1_OBU_TILE_GROUP             =  4,
	AV1_OBU_METADATA               =  5,
	AV1_OBU_FRAME                  =  6,
	AV1_OBU_REDUNDANT_FRAME_HEADER =  7,
	AV1_OBU_TILE_LIST              =  8,
	AV1_OBU_PADDING                = 15,
};

/**
 * AV1 OBU Header
 *
 *     0 1 2 3 4 5 6 7
 *    +-+-+-+-+-+-+-+-+
 *    |F| type  |X|S|-| (REQUIRED)
 *    +-+-+-+-+-+-+-+-+
 */
struct av1_obu_hdr {
	enum obu_type type;  /**< OBU type       */
	bool x;              /**< Extension flag */
	bool s;              /**< Has size field */
	size_t size;         /**< Payload size   */
};

int av1_leb128_encode(struct mbuf *mb, uint64_t value);
int av1_leb128_decode(struct mbuf *mb, uint64_t *value);
int av1_obu_encode(struct mbuf *mb, uint8_t type, bool has_size,
		   size_t len, const uint8_t *payload);
int av1_obu_decode(struct av1_obu_hdr *hdr, struct mbuf *mb);
int av1_obu_print(struct re_printf *pf, const struct av1_obu_hdr *hdr);
unsigned av1_obu_count(const uint8_t *buf, size_t size);
unsigned av1_obu_count_rtp(const uint8_t *buf, size_t size);
const char *av1_obu_name(enum obu_type type);


/*
 * Packetizer
 */

typedef int (av1_packet_h)(bool marker, uint64_t rtp_ts,
			    const uint8_t *hdr, size_t hdr_len,
			    const uint8_t *pld, size_t pld_len,
			    void *arg);

int av1_packetize_high(bool *newp, bool marker, uint64_t rtp_ts,
		       const uint8_t *buf, size_t len, size_t maxlen,
		       av1_packet_h *pkth, void *arg);


enum {
	AV1_AGGR_HDR_SIZE = 1,
};

/** AV1 Aggregation Header */
struct av1_aggr_hdr {
	unsigned z:1;  /**< Continuation of OBU fragment from prev packet */
	unsigned y:1;  /**< Last OBU element will continue in next packe  */
	unsigned w:2;  /**< Number of OBU elements in the packet          */
	unsigned n:1;  /**< First packet of a coded video sequence        */
};

int av1_aggr_hdr_decode(struct av1_aggr_hdr *hdr, struct mbuf *mb);
