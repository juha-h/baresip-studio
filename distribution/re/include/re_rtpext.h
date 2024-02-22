/**
 * @file re_rtpext.h  Interface to RTP Header Extensions
 *
 * Copyright (C) 2010 - 2022 Alfred E. Heggestad
 */


/*
 * RTP Header Extensions
 */

#define RTPEXT_HDR_SIZE        4
#define RTPEXT_TYPE_MAGIC      0xbede  /* One-Byte header */
#define RTPEXT_TYPE_MAGIC_LONG 0x1000  /* Two-Byte header */

enum {
	RTPEXT_ID_MIN  =  1,
	RTPEXT_ID_MAX  = 14,
};

enum {
	RTPEXT_LEN_MIN =  1,
	RTPEXT_LEN_MAX = 16,
	RTPEXT_LEN_MAX_LONG = 256,
};


/** Defines an RTP header extension */
struct rtpext {
	uint8_t id;                        /**< Identifier             */
	uint8_t len;                       /**< Length of data [bytes] */
	uint8_t data[RTPEXT_LEN_MAX_LONG]; /**< Data field             */
};


int rtpext_hdr_encode(struct mbuf *mb, size_t num_bytes);
int rtpext_hdr_encode_long(struct mbuf *mb, size_t num_bytes);
int rtpext_encode(struct mbuf *mb, uint8_t id, size_t len,
		  const uint8_t *data);
int rtpext_encode_long(struct mbuf *mb, uint8_t id, uint8_t len,
		       const uint8_t *data);
int rtpext_decode(struct rtpext *ext, struct mbuf *mb);
int rtpext_decode_long(struct rtpext *ext, struct mbuf *mb);
