/**
 * @file rem_avc.h Advanced Video Coding
 *
 * Copyright (C) 2010 Creytiv.com
 */


struct avc_config {
	uint8_t profile_ind;
	uint8_t profile_compat;
	uint8_t level_ind;
	uint16_t sps_len;
	uint8_t sps[256];
	uint16_t pps_len;
	uint8_t pps[64];
};


int avc_config_encode(struct mbuf *mb, uint8_t profile_ind,
		      uint8_t profile_compat, uint8_t level_ind,
		      uint16_t sps_length, const uint8_t *sps,
		      uint16_t pps_length, const uint8_t *pps);
int avc_config_decode(struct avc_config *conf, struct mbuf *mb);
